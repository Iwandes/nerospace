package za.co.neroland.nerospace.rocket;

import java.util.List;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Pure "New Station from the rocket" rule: a charter is consumed on arrival; a
 * rocket saved by 1.6.0 already in flight with {@code StationSlot:-2} is honoured once for free; no charter →
 * the origin platform with a message; registry full → nothing founded, nothing consumed.
 */
public final class StationFounding {

    public enum Outcome { FOUND_FREE, FOUND_CONSUME, REDIRECT, REDIRECT_FULL }

    /**
     * Why a charter used in the station dimension did or did not found a station where the player stands. Append-only.
     * Every refusal leaves the charter in the player's hand.
     */
    public enum Placement {
        OK,
        /** Closer than {@code stations.minSpacing} to another station's centre or to the origin platform. */
        TOO_CLOSE,
        /** Outside {@code ±stations.maxRadius} on either axis. */
        TOO_FAR,
        /** {@link StationRegistry#MAX_STATIONS} reached. */
        REGISTRY_FULL
    }

    /** Lowest centre Y a placed station may take (the origin platform's level). */
    public static final int MIN_Y = 64;
    /** Highest centre Y a placed station may take. */
    public static final int MAX_Y = 256;

    /**
     * The verdict on a placed centre. {@code blocker} is the station that is too close (the nearest one when several
     * are), or {@code null} with {@code nearOrigin} when it is the origin platform.
     */
    public record PlaceResult(Placement outcome, BlockPos centre, @Nullable StationRegistry.StationEntry blocker,
            boolean nearOrigin) {
    }

    private StationFounding() {
    }

    /**
     * @param legacy       the rocket was saved before the charter rule (no {@code CharterRule} flag)
     * @param hasCharter   the rider carries at least one Station Charter
     * @param registryFull {@code StationRegistry} is at {@code MAX_STATIONS}
     */
    /**
     * Whether a loaded rocket follows the 2.0 charter rule. A 2.0 save carries {@code CharterRule}; a 1.6.0 save
     * has none, and only a rocket that was already in flight ({@code Launching}) is honoured once — a parked 1.6.0
     * rocket with "New Station" still selected follows the 2.0 rule (one charter per station).
     *
     * @param savedRule the saved {@code CharterRule}, or {@code null} when the field is absent
     * @param launching the saved {@code Launching} flag
     */
    public static boolean charterRuleAware(@org.jetbrains.annotations.Nullable Boolean savedRule, boolean launching) {
        return savedRule != null ? savedRule.booleanValue() : !launching;
    }

    /**
     * Snaps a player's feet position to a station centre: the block they stand on becomes the floor (feet Y − 1),
     * clamped to {@code [MIN_Y, MAX_Y]}; X and Z are the block they stand in.
     */
    public static BlockPos snap(BlockPos feet) {
        int y = Math.max(MIN_Y, Math.min(MAX_Y, feet.getY() - 1));
        return new BlockPos(feet.getX(), y, feet.getZ());
    }

    /** Horizontal Chebyshev distance — the square the claim boxes and the spacing rule both use. */
    public static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    /**
     * The placement rule for a charter used in the station dimension. Checks, in order: registry full, outside
     * {@code ±maxRadius}, closer than {@code minSpacing} (horizontal Chebyshev, so two placed stations' lookup squares
     * never overlap) to the origin platform at (0, 0) or to any station centre. {@code centre} is already snapped.
     */
    public static PlaceResult checkPlacement(BlockPos centre, List<StationRegistry.StationEntry> stations,
            int minSpacing, int maxRadius, boolean registryFull) {
        if (registryFull) {
            return new PlaceResult(Placement.REGISTRY_FULL, centre, null, false);
        }
        if (Math.abs(centre.getX()) > maxRadius || Math.abs(centre.getZ()) > maxRadius) {
            return new PlaceResult(Placement.TOO_FAR, centre, null, false);
        }
        if (chebyshev(centre, BlockPos.ZERO) < minSpacing) {
            return new PlaceResult(Placement.TOO_CLOSE, centre, null, true);
        }
        StationRegistry.StationEntry blocker = null;
        int best = Integer.MAX_VALUE;
        for (StationRegistry.StationEntry e : stations) {
            int d = chebyshev(centre, e.center());
            if (d < minSpacing && d < best) {
                best = d;
                blocker = e;
            }
        }
        return blocker != null ? new PlaceResult(Placement.TOO_CLOSE, centre, blocker, false)
                : new PlaceResult(Placement.OK, centre, null, false);
    }

    /** Only an accepted placement spends the charter; every refusal leaves it in hand. */
    public static boolean consumesCharter(Placement outcome) {
        return outcome == Placement.OK;
    }

    public static Outcome resolve(boolean legacy, boolean hasCharter, boolean registryFull) {
        if (!legacy && !hasCharter) {
            return Outcome.REDIRECT;
        }
        if (registryFull) {
            return Outcome.REDIRECT_FULL;
        }
        return legacy ? Outcome.FOUND_FREE : Outcome.FOUND_CONSUME;
    }
}
