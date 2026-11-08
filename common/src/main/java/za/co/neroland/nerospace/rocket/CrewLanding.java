package za.co.neroland.nerospace.rocket;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * Where a crew member who disconnected mid-flight should rejoin (2.1). With two or more players aboard, vanilla does
 * not carry the rocket away with the one who leaves (it does for a lone rider, which is how 2.0 handled it): their
 * saved position is their seat, in the sky. So the rocket notes, in memory only, the spot it touched down on (or the
 * launch pad, for someone who left during the ascent), and the player is put there when they next join, provided
 * they rejoin in that same dimension.
 *
 * <p><b>Privacy (POPIA/GDPR).</b> Never saved and never logged: a restart forgets every entry, an entry is consumed
 * on the next join, the map is bounded ({@link #MAX_ENTRIES}, oldest dropped), and erasure drops a player's entry
 * ({@link #forget}). It records a dimension and a block position against a UUID, nothing else.</p>
 */
public final class CrewLanding {

    /** Bound on remembered entries; the oldest is dropped first. */
    public static final int MAX_ENTRIES = 256;

    /** Where to put a returning crew member. */
    public record Spot(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<UUID, Spot> SPOTS = new LinkedHashMap<>();

    /**
     * The server the entries belong to, weakly held. A single-player client keeps this class loaded when it closes one
     * world and opens another, so without this an entry from world A could move the same player in world B and would
     * outlive "a restart forgets every entry". Any entry from a different server is dropped on first contact.
     */
    private static java.lang.ref.WeakReference<Object> boundTo = new java.lang.ref.WeakReference<>(null);

    private CrewLanding() {
    }

    /**
     * Binds the map to {@code server}: if the entries were made on another server (a previous single-player world),
     * they are all dropped first. Called before every {@link #remember} and on every join.
     */
    public static synchronized void bindTo(@Nullable Object server) {
        if (server != null && boundTo.get() != server) {
            SPOTS.clear();
            boundTo = new java.lang.ref.WeakReference<>(server);
        }
    }

    /** Remembers {@code spot} for {@code player} (replacing an older one). */
    public static synchronized void remember(UUID player, ResourceKey<Level> dimension, BlockPos pos) {
        SPOTS.remove(player);
        SPOTS.put(player, new Spot(dimension, pos.immutable()));
        while (SPOTS.size() > MAX_ENTRIES) {
            UUID oldest = SPOTS.keySet().iterator().next();
            SPOTS.remove(oldest);
        }
    }

    /** Takes (and removes) the spot remembered for {@code player}, or {@code null}. */
    @Nullable
    public static synchronized Spot take(UUID player) {
        return SPOTS.remove(player);
    }

    /** Erasure: drops the player's entry. */
    public static synchronized boolean forget(UUID player) {
        return SPOTS.remove(player) != null;
    }

    /** Conformance probe: whether an entry exists for the player. */
    public static synchronized boolean holds(UUID player) {
        return SPOTS.containsKey(player);
    }

    /** Entries held (tests). */
    static synchronized int size() {
        return SPOTS.size();
    }

    /**
     * On join: a crew member whose rocket landed (or lifted off) while they were offline is placed beside it, if
     * they rejoined in that dimension and are not riding anything. The entry is consumed either way.
     */
    public static void onJoin(ServerPlayer player) {
        bindTo(player.level().getServer());
        Spot spot = take(player.getUUID());
        if (spot == null || player.isPassenger() || !spot.dimension().equals(player.level().dimension())) {
            return;
        }
        ServerLevel level = player.level();
        player.teleportTo(level, spot.pos().getX() + 0.5D, spot.pos().getY(), spot.pos().getZ() + 0.5D,
                Set.of(), player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
    }
}
