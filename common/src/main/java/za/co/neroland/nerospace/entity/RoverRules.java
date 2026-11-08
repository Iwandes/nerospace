package za.co.neroland.nerospace.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * The Mars rover's rules (D16–D19), pure so they are tested without a world: gravity scaling of speed and jump, the
 * energy budget, who may pack a rover up, the per-player cap, the owner field's stored form and the Rover Wreck
 * chassis roll. {@link RoverEntity} applies them inside its normal server tick.
 *
 * <p>Units: blocks and ticks. Gravity is a factor of Earth's (1.0), as {@code GravityManager.factorAt} returns it.</p>
 */
public final class RoverRules {

    /** Earth gravity in blocks/tick², the acceleration vanilla applies to falling entities. */
    public static final double EARTH_GRAVITY = 0.08D;
    /** Vertical drag per tick while airborne (vanilla's falling-entity drag). */
    public static final double AIR_DRAG = 0.98D;
    /** Gravity factors are clamped to this range: no levitation, no zero-g drifting off a cliff, no crushing. */
    public static final double MIN_GRAVITY = 0.1D;
    public static final double MAX_GRAVITY = 2.0D;

    /** Top speed on flat ground at Earth gravity, blocks/tick (6 blocks/s, a little faster than sprinting). */
    public static final double BASE_SPEED = 0.30D;
    /** Speed scale bounds: low gravity drives up to 35 % faster, high gravity down to 80 %. */
    public static final double MIN_SPEED_SCALE = 0.8D;
    public static final double MAX_SPEED_SCALE = 1.35D;
    /** Reverse is slower than forward. */
    public static final double REVERSE_FRACTION = 0.5D;
    /** Speed gained per tick under throttle, and lost per tick when coasting on the ground. */
    public static final double ACCELERATION = 0.02D;
    public static final double ROLLING_FRICTION = 0.03D;
    /** Degrees turned per tick while steering. */
    public static final float TURN_DEGREES = 4.0F;

    /** Hop apex at Earth gravity, blocks. */
    public static final double BASE_JUMP_APEX = 0.6D;
    /**
     * The highest a hop may ever reach, blocks. Kept below one block so a hop plus the one-block step can never put the
     * rover on top of a two-block wall (D16: it climbs one block, no more).
     */
    public static final double MAX_JUMP_APEX = 0.95D;
    /** Step-up height, blocks (D16). */
    public static final float STEP_HEIGHT = 1.0F;

    /** FE a hop costs. */
    public static final int JUMP_COST = 50;
    /** Defaults of the {@code rover.*} config keys. */
    public static final int DEFAULT_CAPACITY = 40_000;
    public static final int DEFAULT_MAX_PER_PLAYER = 4;
    public static final int DEFAULT_DRAIN_PER_TICK = 5;
    public static final int DEFAULT_CHARGE_RATE = 200;

    /** Rover Wreck chance (percent) of a {@code rover_chassis} in the salvage chest. */
    public static final int WRECK_CHASSIS_PERCENT = 50;

    private RoverRules() {
    }

    // --- Gravity ----------------------------------------------------------------------------------------------

    /** A raw gravity factor (which a Gravity Shard can make negative) clamped to {@code [MIN, MAX]}. */
    public static double clampGravity(double factor) {
        if (Double.isNaN(factor)) {
            return 1.0D;
        }
        return Math.max(MIN_GRAVITY, Math.min(MAX_GRAVITY, factor));
    }

    /** Downward acceleration per tick at this gravity factor. */
    public static double gravityAccel(double factor) {
        return EARTH_GRAVITY * clampGravity(factor);
    }

    /** Top-speed multiplier: {@code 1 + 0.4 × (1 − g)}, bounded. Less grip-limited weight on light worlds. */
    public static double speedScale(double factor) {
        double g = clampGravity(factor);
        return Math.max(MIN_SPEED_SCALE, Math.min(MAX_SPEED_SCALE, 1.0D + 0.4D * (1.0D - g)));
    }

    /** Top speed (blocks/tick) at this gravity factor. */
    public static double maxSpeed(double factor) {
        return BASE_SPEED * speedScale(factor);
    }

    /** The hop apex the rover aims for: {@code 0.6 / sqrt(g)}, capped below one block. */
    public static double jumpApex(double factor) {
        return Math.min(MAX_JUMP_APEX, BASE_JUMP_APEX / Math.sqrt(clampGravity(factor)));
    }

    /** Initial upward speed of a hop, from {@code v² = 2·a·h} (drag only lowers the real apex). */
    public static double jumpVelocity(double factor) {
        return Math.sqrt(2.0D * gravityAccel(factor) * jumpApex(factor));
    }

    /**
     * The apex the rover's own vertical integration reaches from {@code v0} (the same update {@link RoverEntity} runs:
     * {@code v = (v − a) × drag}, position += v). Used by the tests to prove the one-block rule.
     */
    public static double simulateApex(double v0, double factor) {
        double a = gravityAccel(factor);
        double y = 0.0D;
        double v = v0;
        for (int i = 0; i < 400 && v > 0.0D; i++) {
            y += v;
            v = (v - a) * AIR_DRAG;
        }
        return y;
    }

    /** One tick of throttle: approach {@code target} by at most {@code ACCELERATION}, or coast down by friction. */
    public static double approachSpeed(double current, double target, boolean throttle) {
        double step = throttle ? ACCELERATION : ROLLING_FRICTION;
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    /** Throttle target from the driver's forward/back keys. */
    public static double throttleTarget(boolean forward, boolean backward, double factor) {
        if (forward == backward) {
            return 0.0D;
        }
        return forward ? maxSpeed(factor) : -maxSpeed(factor) * REVERSE_FRACTION;
    }

    // --- Energy -----------------------------------------------------------------------------------------------

    /** Energy after one tick of driving: {@code drain} FE while the throttle is held, never below zero. */
    public static int drain(int energy, boolean throttle, int drainPerTick) {
        if (!throttle || drainPerTick <= 0) {
            return Math.max(0, energy);
        }
        return Math.max(0, energy - drainPerTick);
    }

    /** Whether the throttle may act: some charge left (or free driving when drain is configured to 0). */
    public static boolean canDrive(int energy, int drainPerTick) {
        return drainPerTick <= 0 || energy > 0;
    }

    /** FE the rover may take this charge pass: room left, bounded by rate × ticks. */
    public static int chargeRoom(int energy, int capacity, int ratePerTick, int ticks) {
        long room = Math.max(0L, (long) capacity - energy);
        long rate = Math.max(0L, (long) ratePerTick * ticks);
        return (int) Math.min(room, rate);
    }

    /** Charge as a whole percent, 0..100. */
    public static int percent(int energy, int capacity) {
        if (capacity <= 0) {
            return 0;
        }
        return (int) Math.max(0L, Math.min(100L, energy * 100L / capacity));
    }

    // --- Ownership, pickup and the cap (D18, D19) ---------------------------------------------------------------

    /**
     * Who may pack a rover up: anyone when it records no owner (a rover placed by a dispenser, or one whose owner was
     * erased), else its owner or an operator. Anyone may always ride.
     */
    public static boolean mayPickUp(@Nullable UUID owner, UUID player, boolean operator) {
        return owner == null || owner.equals(player) || operator;
    }

    /** Whether a player who already has {@code owned} loaded rovers may place another under a cap of {@code cap}. */
    public static boolean mayPlace(int owned, int cap) {
        return owned < Math.max(0, cap);
    }

    /** Erasure: the owner after forgetting {@code erased} — {@code null} if it was them, else unchanged. */
    @Nullable
    public static UUID forget(@Nullable UUID owner, @Nullable UUID erased) {
        return owner != null && owner.equals(erased) ? null : owner;
    }

    /** The owner as stored in entity NBT: a UUID string, or {@code ""} for none. */
    public static String writeOwner(@Nullable UUID owner) {
        return owner == null ? "" : owner.toString();
    }

    /** Parses the stored owner; {@code null} for absent, empty or malformed (a malformed owner means unowned). */
    @Nullable
    public static UUID readOwner(@Nullable String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(stored);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // --- Rover Wreck --------------------------------------------------------------------------------------------

    /**
     * Whether the wreck at this origin holds a chassis. Derived from the position rather than the feature's random so
     * the existing wreck layout and drone placement draw exactly the same numbers as in 2.0.
     */
    public static boolean wreckHasChassis(long originKey) {
        long h = originKey * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        return Math.floorMod(h, 100L) < WRECK_CHASSIS_PERCENT;
    }
}
