package za.co.neroland.nerospace.rocket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.config.NerospaceConfig;

/**
 * Who is aboard a crewed rocket (2.1, up to four players). The <b>pilot</b> is the first seated player: the first to
 * board. Boarding appends (vanilla keeps a player-first passenger list and every passenger here is a player), so a
 * later boarder never takes the pilot's seat. Only the pilot may launch or change destination, dock or pad; the
 * rest of the <b>crew</b> board and leave freely before launch and travel with the rocket.
 *
 * <p>The pure rules (seat cap, oxygen per seat, the arrival-reserve split, the mid-flight pilot handover) take plain
 * values so they are tested without a world. The entity helpers read the live passenger list.</p>
 *
 * <p><b>Privacy.</b> Crew membership of a flight is never saved beyond the vanilla passenger list and is never
 * logged; telemetry gets the crew <em>count</em> only.</p>
 */
public final class Crew {

    /** No configuration can seat more than this. */
    public static final int HARD_CAP = 4;
    /** {@code rockets.maxCrew} default. */
    public static final int DEFAULT_MAX = 4;

    /** Ticks a rider spends in a sealed hull per flight: the ascent plus the 48-block descent and its settle. */
    public static final int FLIGHT_TICKS = RocketEntity.LAUNCH_DURATION
            + (int) Math.ceil(DescentMath.DESCENT_HEIGHT / DescentMath.DESCENT_SPEED) + DescentMath.SETTLE_TICKS;

    private Crew() {
    }

    // --- Pure rules -------------------------------------------------------------------------------------

    /** A configured crew size clamped to {@code [1, HARD_CAP]}. */
    public static int cap(int configured) {
        return Math.max(1, Math.min(HARD_CAP, configured));
    }

    /** Seats on this server: {@code rockets.maxCrew}, clamped to the hard cap. */
    public static int maxCrew() {
        return cap(NerospaceConfig.rocketsMaxCrew());
    }

    /** Whether one more player may take a seat beside {@code seated} others under a cap of {@code configured}. */
    public static boolean hasSeat(int seated, int configured) {
        return seated < cap(configured);
    }

    /**
     * Oxygen (mB) one seat draws over {@link #FLIGHT_TICKS} when the flight touches an airless world: the rocket's
     * ride drain ({@code ridePerCheck} mB every {@code checkTicks}). 0 for a flight between two breathable worlds.
     */
    public static int perSeatMb(boolean airlessLeg, int checkTicks, int ridePerCheck) {
        if (!airlessLeg || checkTicks <= 0) {
            return 0;
        }
        return Math.ceilDiv(FLIGHT_TICKS, checkTicks) * Math.max(0, ridePerCheck);
    }

    /**
     * Oxygen a launch needs: {@code crew × perSeat}. A solo flight needs none, exactly as in 2.0 (the rider's own
     * lungs or suit cover the trip); the rule exists so a full rocket cannot leave with a reserve too thin to share.
     */
    public static int oxygenNeeded(int crew, int perSeat) {
        return crew >= 2 ? crew * Math.max(0, perSeat) : 0;
    }

    /** How many seats {@code oxygen} covers for one flight ("O₂ for N"), at most {@code cap}. */
    public static int oxygenFor(int oxygen, int perSeat, int cap) {
        if (perSeat <= 0) {
            return cap;
        }
        return Math.max(0, Math.min(cap, oxygen / perSeat));
    }

    /**
     * Splits the arrival reserve between {@code crew} seats: equal shares, the remainder going to the first seats
     * (the pilot first). One rider gets the whole reserve, as in 2.0. The shares always sum to {@code total}.
     */
    public static int[] splitReserve(int total, int crew) {
        if (crew <= 0) {
            return new int[0];
        }
        int safe = Math.max(0, total);
        int[] shares = new int[crew];
        int each = safe / crew;
        int rest = safe % crew;
        for (int i = 0; i < crew; i++) {
            shares[i] = each + (i < rest ? 1 : 0);
        }
        return shares;
    }

    /** The pilot of a seat list (first seated), or {@code null} when nobody is aboard. */
    @Nullable
    public static <T> T pilotOf(List<T> seated) {
        return seated.isEmpty() ? null : seated.get(0);
    }

    /**
     * Whether the arrival is flown by someone other than the player who launched (the launch pilot disconnected
     * mid-flight and the next seated player took over for the arrival). {@code null} launch pilot = unknown (a
     * rocket reloaded mid-flight): treated as no handover, the 2.0 behaviour.
     */
    public static boolean handedOver(@Nullable UUID launchPilot, @Nullable UUID arrivalPilot) {
        return launchPilot != null && arrivalPilot != null && !launchPilot.equals(arrivalPilot);
    }

    // --- Taking a seat from the console (2.1) ---------------------------------------------------------------

    /**
     * Reach, in blocks from the player's eyes to the hull, within which the console's Take seat press is honoured:
     * a little past vanilla's own entity reach, so a player who opened the console from where they stand can sit.
     */
    public static final double SEAT_REACH = 4.5D;

    /**
     * The server's answer to the rocket console's Take seat press, checked in declaration order. Vanilla never seats
     * a sneaking player (and a seat taken while sneaking is dropped within a few ticks), so boarding is a console
     * button rather than a sneak gesture, and a press with sneak held is refused with a hint.
     */
    public enum SeatAnswer {
        /** The press seats the player: as the pilot when nobody is aboard, else as crew behind the pilot. */
        OK,
        /** The press did not come from this console's own viewer: nobody can be seated by someone else. */
        NOT_VIEWER,
        /** The viewer has stepped away from the hull. */
        OUT_OF_REACH,
        /** An uncrewed (cargo) hull. */
        UNCREWED,
        /** Launching or descending: the hatch is sealed. */
        IN_FLIGHT,
        /** Already seated in this rocket. */
        ALREADY_ABOARD,
        /** Every seat is taken. */
        FULL,
        /** Sneak is held: vanilla refuses the seat, so the player is told to let go first. */
        SNEAKING;

        private static final SeatAnswer[] VALUES = values();

        /** A small int for a menu data slot: 0 is "not synced yet". */
        public int encode() {
            return ordinal() + 1;
        }

        /** The answer an {@link #encode()}d value names; anything unknown (including 0) reads as {@link #OK}. */
        public static SeatAnswer decode(int code) {
            return code >= 1 && code <= VALUES.length ? VALUES[code - 1] : OK;
        }
    }

    /**
     * Whether a player may take a seat from the console, in the {@link SeatAnswer} order. Pure: the caller passes
     * whether the press came from the menu's own viewer, the squared distance from the player's eyes to the hull,
     * whether the hull carries crew, whether it is launching or descending, whether the player is already aboard,
     * how many are seated, the configured seat count and whether sneak is held.
     */
    public static SeatAnswer takeSeat(boolean viewer, double reachSq, boolean crewedHull, boolean inFlight,
            boolean aboard, int seated, int configured, boolean sneaking) {
        if (!viewer) {
            return SeatAnswer.NOT_VIEWER;
        }
        if (!(reachSq <= SEAT_REACH * SEAT_REACH)) { // NaN is refused too
            return SeatAnswer.OUT_OF_REACH;
        }
        if (!crewedHull) {
            return SeatAnswer.UNCREWED;
        }
        if (inFlight) {
            return SeatAnswer.IN_FLIGHT;
        }
        if (aboard) {
            return SeatAnswer.ALREADY_ABOARD;
        }
        if (!hasSeat(seated, configured)) {
            return SeatAnswer.FULL;
        }
        if (sneaking) {
            return SeatAnswer.SNEAKING;
        }
        return SeatAnswer.OK;
    }

    // --- Entity helpers ----------------------------------------------------------------------------------

    /** The pilot: the first passenger when it is a player, else {@code null}. */
    @Nullable
    public static Player pilot(Entity vehicle) {
        return vehicle.getFirstPassenger() instanceof Player player ? player : null;
    }

    /** Every seated player, pilot first. */
    public static List<Player> seated(Entity vehicle) {
        List<Player> out = new ArrayList<>(HARD_CAP);
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                out.add(player);
            }
        }
        return out;
    }

    /** Every seated server player, pilot first (server side). */
    public static List<ServerPlayer> seatedServer(Entity vehicle) {
        List<ServerPlayer> out = new ArrayList<>(HARD_CAP);
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                out.add(player);
            }
        }
        return out;
    }

    /** How many players are seated. */
    public static int count(Entity vehicle) {
        int n = 0;
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player) {
                n++;
            }
        }
        return n;
    }

    /** Whether {@code player} is this vehicle's pilot. */
    public static boolean isPilot(Entity vehicle, @Nullable Player player) {
        return player != null && pilot(vehicle) == player;
    }

    /**
     * Whether {@code player} may command the rocket (launch, destination, dock, pad): the pilot, or anyone while
     * nobody is aboard ({@link LaunchSeat#mayLaunch}). A {@code null} player is an API caller and always may.
     */
    public static boolean mayCommand(Entity vehicle, @Nullable Player player) {
        return player == null || LaunchSeat.mayLaunch(vehicle.getPassengers().isEmpty(), isPilot(vehicle, player));
    }
}
