package za.co.neroland.nerospace.rocket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The per-rider launch cooldown: rider UUID → game time the next launch is allowed. In memory only, never
 * saved; expired entries are dropped on every launch, so the map holds at most the riders who launched in the last
 * {@link DescentMath#LAUNCH_COOLDOWN} ticks. POPIA/GDPR erasure drops a player's entry ({@link #forget}).
 */
public final class LaunchCooldowns {

    private static final Map<UUID, Long> UNTIL = new HashMap<>();

    private LaunchCooldowns() {
    }

    /**
     * @return {@code false} if the rider is still cooling down; otherwise records the launch and returns {@code true}
     */
    public static synchronized boolean tryStart(UUID rider, long now, long cooldown) {
        Long until = UNTIL.get(rider);
        if (until != null && now < until) {
            return false;
        }
        UNTIL.entrySet().removeIf(e -> e.getValue() <= now);
        UNTIL.put(rider, now + cooldown);
        return true;
    }

    /** Whether {@code rider} is still inside the cooldown window at {@code now}. Read-only. */
    public static synchronized boolean coolingDown(UUID rider, long now) {
        Long until = UNTIL.get(rider);
        return until != null && now < until;
    }

    /** What {@link #gate} decided. {@code refusal} is the fee's refusal key when {@code verdict} is REFUSED. */
    public record Gate(Verdict verdict, @org.jetbrains.annotations.Nullable String refusal) {
    }

    /** Append-only: {@code CREW_COOLDOWN} (2.1) = the pilot is clear but a seated crew member is still cooling down. */
    public enum Verdict { COOLDOWN, REFUSED, OK, CREW_COOLDOWN }

    /** Game ticks left in {@code rider}'s cooldown window at {@code now} (0 when clear). Read-only. */
    public static synchronized long remaining(UUID rider, long now) {
        Long until = UNTIL.get(rider);
        return until == null ? 0L : Math.max(0L, until - now);
    }

    /**
     * What {@link #gateCrew} decided. {@code coolingSeats} are the seat indices (0 = pilot) still cooling down, so
     * each of them can be told their own wait without naming anyone to anybody else.
     */
    public record CrewGate(Verdict verdict, @org.jetbrains.annotations.Nullable String refusal,
            java.util.List<Integer> coolingSeats) {
    }

    /**
     * {@link #gate} for a crewed launch (2.1): every seated player ({@code seated}, pilot first) is checked against
     * their own cooldown, so nobody dodges theirs by riding along; then the fee is charged <b>once</b> ({@code charge},
     * the pilot's); only when it passed is a launch recorded for every seat. One seat behaves exactly like
     * {@link #gate}. An empty seat list starts nothing and charges nothing.
     */
    public static CrewGate gateCrew(java.util.List<UUID> seated, long now, long cooldown,
            java.util.function.Supplier<String> charge) {
        if (seated.isEmpty()) {
            return new CrewGate(Verdict.OK, null, java.util.List.of());
        }
        java.util.List<Integer> cooling = new java.util.ArrayList<>();
        for (int i = 0; i < seated.size(); i++) {
            if (coolingDown(seated.get(i), now)) {
                cooling.add(i);
            }
        }
        if (!cooling.isEmpty()) {
            return new CrewGate(cooling.get(0) == 0 ? Verdict.COOLDOWN : Verdict.CREW_COOLDOWN, null,
                    java.util.List.copyOf(cooling));
        }
        String refused = charge.get();
        if (refused != null) {
            return new CrewGate(Verdict.REFUSED, refused, java.util.List.of());
        }
        for (UUID rider : seated) {
            tryStart(rider, now, cooldown);
        }
        return new CrewGate(Verdict.OK, null, java.util.List.of());
    }

    /**
     * The launch order around a fee: the cooldown is checked first, so a launch inside the window is
     * refused before {@code charge} runs and nothing is paid; then the fee is charged ({@code charge} returns a
     * refusal key or {@code null}); the launch is recorded only when the fee passed.
     */
    public static Gate gate(UUID rider, long now, long cooldown,
            java.util.function.Supplier<String> charge) {
        if (coolingDown(rider, now)) {
            return new Gate(Verdict.COOLDOWN, null);
        }
        String refused = charge.get();
        if (refused != null) {
            return new Gate(Verdict.REFUSED, refused);
        }
        tryStart(rider, now, cooldown);
        return new Gate(Verdict.OK, null);
    }

    /** Erasure: drops the player's entry. */
    public static synchronized boolean forget(UUID player) {
        return UNTIL.remove(player) != null;
    }

    /** Conformance probe: whether an entry exists for the player. */
    public static synchronized boolean holds(UUID player) {
        return UNTIL.containsKey(player);
    }
}
