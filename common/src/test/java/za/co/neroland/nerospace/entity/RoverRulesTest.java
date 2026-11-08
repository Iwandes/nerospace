package za.co.neroland.nerospace.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 2.1 Mars rover (D16–D19): the pure rules, plus source checks of the call sites that need a live world (the pattern of
 * {@code rocket/PodArrivalTest}).
 */
class RoverRulesTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-00000000f1c5");
    private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final double EARTH = 1.0D;
    private static final double MARS = 0.4D;   // GravityManager LOW_FACTOR
    private static final double LUNA = 0.15D;  // GravityManager MICRO_FACTOR
    private static final double HEAVY = 1.5D;  // GravityManager HIGH_FACTOR

    static String source(String rel) throws Exception {
        for (Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath(); p != null; p = p.getParent()) {
            Path c = p.resolve("common/src/main/java/za/co/neroland/nerospace").resolve(rel);
            if (Files.isRegularFile(c)) {
                return Files.readString(c).replace("\r\n", "\n");
            }
        }
        throw new IllegalStateException("source not found: " + rel);
    }

    static String body(String src, String signature) {
        int start = src.indexOf(signature);
        if (start < 0) {
            throw new IllegalStateException("missing " + signature);
        }
        int open = src.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}' && --depth == 0) {
                return src.substring(open, i + 1);
            }
        }
        throw new IllegalStateException("unbalanced " + signature);
    }

    // --- gravity scaling ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("lower gravity drives faster and hops higher, within bounds; levitation and NaN are clamped")
    void gravityScaling() {
        assertEquals(RoverRules.BASE_SPEED, RoverRules.maxSpeed(EARTH), 1e-9);
        assertTrue(RoverRules.maxSpeed(MARS) > RoverRules.maxSpeed(EARTH));
        assertTrue(RoverRules.maxSpeed(LUNA) >= RoverRules.maxSpeed(MARS));
        assertTrue(RoverRules.maxSpeed(HEAVY) < RoverRules.maxSpeed(EARTH));
        for (double g = -2.0D; g <= 4.0D; g += 0.05D) {
            double scale = RoverRules.speedScale(g);
            assertTrue(scale >= RoverRules.MIN_SPEED_SCALE && scale <= RoverRules.MAX_SPEED_SCALE, "g=" + g);
        }
        assertEquals(RoverRules.MIN_GRAVITY, RoverRules.clampGravity(-0.5D), 1e-9, "a Gravity Shard never lifts a rover");
        assertEquals(1.0D, RoverRules.clampGravity(Double.NaN), 1e-9);
        assertEquals(RoverRules.EARTH_GRAVITY * MARS, RoverRules.gravityAccel(MARS), 1e-9);

        double earth = RoverRules.simulateApex(RoverRules.jumpVelocity(EARTH), EARTH);
        double mars = RoverRules.simulateApex(RoverRules.jumpVelocity(MARS), MARS);
        assertTrue(mars > earth, "Mars hops higher than Earth: " + mars + " vs " + earth);
        assertTrue(earth > 0.3D, "a hop is a real hop: " + earth);
    }

    @Test
    @DisplayName("no hop at any gravity reaches a second block, so the one-block climb rule holds")
    void oneBlockClimb() {
        assertEquals(1.0F, RoverRules.STEP_HEIGHT);
        for (double g = 0.0D; g <= 2.5D; g += 0.01D) {
            double apex = RoverRules.simulateApex(RoverRules.jumpVelocity(g), g);
            assertTrue(apex < 1.0D, "g=" + g + " apex=" + apex);
            assertTrue(RoverRules.jumpApex(g) <= RoverRules.MAX_JUMP_APEX);
        }
    }

    @Test
    @DisplayName("throttle ramps to the gravity-scaled top speed; reverse is half; coasting stops")
    void throttle() {
        assertEquals(0.0D, RoverRules.throttleTarget(true, true, EARTH));
        assertEquals(0.0D, RoverRules.throttleTarget(false, false, EARTH));
        assertEquals(RoverRules.maxSpeed(MARS), RoverRules.throttleTarget(true, false, MARS), 1e-9);
        assertEquals(-RoverRules.maxSpeed(MARS) * 0.5D, RoverRules.throttleTarget(false, true, MARS), 1e-9);
        double v = 0.0D;
        for (int i = 0; i < 100; i++) {
            v = RoverRules.approachSpeed(v, RoverRules.maxSpeed(EARTH), true);
        }
        assertEquals(RoverRules.maxSpeed(EARTH), v, 1e-9, "never overshoots");
        for (int i = 0; i < 100; i++) {
            v = RoverRules.approachSpeed(v, 0.0D, false);
        }
        assertEquals(0.0D, v, 1e-9);
    }

    // --- energy -------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("energy drains only under throttle, never below zero; a flat battery cannot drive; charging is bounded")
    void energyDrain() {
        assertEquals(40_000, RoverRules.DEFAULT_CAPACITY);
        assertEquals(39_995, RoverRules.drain(40_000, true, 5));
        assertEquals(40_000, RoverRules.drain(40_000, false, 5), "coasting and parking are free");
        assertEquals(0, RoverRules.drain(3, true, 5));
        int e = RoverRules.DEFAULT_CAPACITY;
        int ticks = 0;
        while (RoverRules.canDrive(e, RoverRules.DEFAULT_DRAIN_PER_TICK)) {
            e = RoverRules.drain(e, true, RoverRules.DEFAULT_DRAIN_PER_TICK);
            ticks++;
        }
        assertEquals(8_000, ticks, "a full battery is 400 s of throttle at the defaults");
        assertTrue(RoverRules.canDrive(0, 0), "drainPerTick 0 = free driving");
        assertEquals(4_000, RoverRules.chargeRoom(0, 40_000, 200, 20));
        assertEquals(100, RoverRules.chargeRoom(39_900, 40_000, 200, 20), "never past capacity");
        assertEquals(0, RoverRules.chargeRoom(40_000, 40_000, 200, 20));
        assertEquals(0, RoverRules.chargeRoom(0, 40_000, 0, 20), "chargeRate 0 = no charging");
        assertEquals(50, RoverRules.percent(20_000, 40_000));
        assertEquals(100, RoverRules.percent(90_000, 40_000));
        assertEquals(0, RoverRules.percent(5, 0));
    }

    // --- ownership, pickup, cap ---------------------------------------------------------------------------------

    @Test
    @DisplayName("only the owner or an operator packs a rover up; an unowned rover is anyone's")
    void ownerOnlyPickup() throws Exception {
        assertTrue(RoverRules.mayPickUp(OWNER, OWNER, false));
        assertFalse(RoverRules.mayPickUp(OWNER, OTHER, false));
        assertTrue(RoverRules.mayPickUp(OWNER, OTHER, true), "operators may");
        assertTrue(RoverRules.mayPickUp(null, OTHER, false), "no owner recorded: anyone");

        String rover = source("entity/RoverEntity.java");
        String hurt = body(rover, "public boolean hurtServer(");
        assertTrue(hurt.indexOf("isSecondaryUseActive()") < hurt.indexOf("packUp("), "sneak first");
        assertTrue(hurt.indexOf("isVehicle()") < hurt.indexOf("packUp("), "nobody aboard");
        assertTrue(hurt.indexOf("mayPickUp(player)") < hurt.indexOf("packUp("), "owner check before packing up");
        assertTrue(body(rover, "public boolean mayPickUp(").contains("RoverRules.mayPickUp(this.owner"));
        // Anyone may ride: boarding never looks at the owner.
        assertFalse(body(rover, "protected boolean canAddPassenger(").contains("owner"));
        assertTrue(body(rover, "protected boolean canAddPassenger(").contains("SEATS"));
    }

    @Test
    @DisplayName("vanilla horse convention: right-click rides, sneak + right-click opens the cargo bay")
    void rideAndCargoGestures() throws Exception {
        String interact = body(source("entity/RoverEntity.java"), "public InteractionResult interact(");
        int sneak = interact.indexOf("player.isSecondaryUseActive()");
        int cargo = interact.indexOf("MenuOpener.openOrConsume(");
        int ride = interact.indexOf("rider.startRiding(this)");
        assertTrue(sneak >= 0 && cargo > sneak && ride > cargo, interact);
        // The sneak branch returns before the seat: nobody is ever seated while sneaking (vanilla would refuse).
        String sneakBranch = interact.substring(sneak, ride);
        assertTrue(sneakBranch.contains("return InteractionResult.SUCCESS;"));
        assertEquals(1, interact.split("startRiding\\(", -1).length - 1, "one seat path");
        assertTrue(interact.indexOf("canAddPassenger(rider)") < ride, "seat cap checked first");
    }

    @Test
    @DisplayName("the per-player cap counts loaded rovers at placement; nothing is stored per player")
    void perPlayerCap() throws Exception {
        assertTrue(RoverRules.mayPlace(0, 4));
        assertTrue(RoverRules.mayPlace(3, 4));
        assertFalse(RoverRules.mayPlace(4, 4));
        assertFalse(RoverRules.mayPlace(9, 4));
        assertFalse(RoverRules.mayPlace(0, 0));
        String item = source("item/RoverItem.java");
        String use = body(item, "public InteractionResult useOn(");
        assertTrue(use.contains("RoverEntity.countOwned(") && use.contains("RoverRules.mayPlace(owned, cap)"));
        assertTrue(use.indexOf("mayPlace(") < use.indexOf("addFreshEntity("), "cap checked before the rover exists");
        assertTrue(use.contains("rover.setOwner(player.getUUID())"));
        assertFalse(source("entity/RoverEntity.java").contains("SavedData"), "no per-player counter store");
    }

    /** The stored Owner string, "" when absent (getString already defaulted to "" before 1.21.5). */
    private static String ownerTag(CompoundTag tag) {
        //? if >=1.21.5 {
        return tag.getStringOr(RoverEntity.OWNER_KEY, "");
        //?} else {
        /*return tag.getString(RoverEntity.OWNER_KEY);
        *///?}
    }

    /** The stored charge, 0 when absent (getInt already defaulted to 0 before 1.21.5). */
    private static int energyTag(CompoundTag tag) {
        //? if >=1.21.5 {
        return tag.getIntOr(RoverEntity.ENERGY_KEY, 0);
        //?} else {
        /*return tag.getInt(RoverEntity.ENERGY_KEY);
        *///?}
    }

    @Test
    @DisplayName("owner NBT round-trips with and without an owner; malformed reads as unowned; absent key is written")
    void nbtRoundTrip() throws Exception {
        CompoundTag owned = new CompoundTag();
        owned.putString(RoverEntity.OWNER_KEY, RoverRules.writeOwner(OWNER));
        owned.putInt(RoverEntity.ENERGY_KEY, 1234);
        CompoundTag back = owned.copy();
        assertEquals(OWNER, RoverRules.readOwner(ownerTag(back)));
        assertEquals(1234, energyTag(back));

        CompoundTag unowned = new CompoundTag(); // a 2.1 rover with no owner writes no Owner key at all
        assertNull(RoverRules.readOwner(ownerTag(unowned)));
        assertEquals(0, energyTag(unowned), "absent energy = flat");
        assertNull(RoverRules.readOwner("not-a-uuid"));
        assertNull(RoverRules.readOwner(null));
        assertEquals("", RoverRules.writeOwner(null));

        String rover = source("entity/RoverEntity.java");
        String write = body(rover, "protected void addAdditionalSaveData(");
        assertTrue(write.contains("if (this.owner != null)"), "Owner is optional: written only when present");
        String read = body(rover, "protected void readAdditionalSaveData(");
        assertTrue(read.contains("getStringOr(OWNER_KEY, \"\")") && read.contains("getIntOr(ENERGY_KEY, 0)"));
        assertTrue(read.contains("NerospaceErasure.ownerErasedFilter(level())"), "an erased owner is dropped on load");
    }

    // --- erasure ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("erasure clears the owner (and only that owner); the sweep is wired into NerospaceErasure")
    void erasureClearsOwner() throws Exception {
        assertNull(RoverRules.forget(OWNER, OWNER));
        assertEquals(OTHER, RoverRules.forget(OTHER, OWNER));
        assertNull(RoverRules.forget(null, OWNER));
        assertTrue(RoverRules.mayPickUp(RoverRules.forget(OWNER, OWNER), OTHER, false),
                "after erasure the rover is anyone's to pack up");
        String erasure = source("data/NerospaceErasure.java");
        assertTrue(body(erasure, "public static void erasePlayer(").contains("RoverEntity.forgetOwnerEverywhere(server, uuid)"));
        assertTrue(body(source("entity/RoverEntity.java"), "public boolean forgetOwner(").contains("RoverRules.forget("));
    }

    // --- scope discipline, hazards, link ------------------------------------------------------------------------

    @Test
    @DisplayName("riders get no life support, the rover loads no chunks, drives from the vanilla input path")
    void scope() throws Exception {
        String rover = source("entity/RoverEntity.java");
        assertFalse(rover.contains("Ticket") || rover.contains("setChunkForced") || rover.contains("forceLoad"));
        assertTrue(rover.contains("getLastClientInput()"), "vanilla vehicle input, never raw keys");
        assertFalse(rover.contains("InputConstants") || rover.contains("GLFW"));
        assertFalse(rover.contains("getControllingPassenger"), "server-authoritative: no client-simulated controller");
        String oxygen = source("world/OxygenManager.java");
        assertFalse(oxygen.contains("RoverEntity"), "the rover has no life support");
        assertTrue(rover.contains("MenuOpener.openOrConsume("));
    }

    @Test
    @DisplayName("link rovers rows: only the requester's own rovers, in the proximity scan, counts only")
    void linkScope() throws Exception {
        assertEquals(4, za.co.neroland.nerospace.link.NerospaceLinkModule.SCHEMA_VERSION);
        String snap = source("link/NerospaceLinkSnapshots.java");
        assertTrue(snap.contains("r -> r.isOwnedBy(playerId)"), "own rovers only, never a roster");
        String row = body(snap, "private static JsonObject roverRow(");
        assertFalse(row.contains("owner") || row.contains("getPassengers"), "no identities");
    }

    @Test
    @DisplayName("the wreck chassis roll is deterministic, position-derived and roughly half of the wrecks")
    void wreckChassis() {
        int hits = 0;
        for (long k = 0; k < 10_000; k++) {
            boolean a = RoverRules.wreckHasChassis(k * 7919L);
            assertEquals(a, RoverRules.wreckHasChassis(k * 7919L));
            hits += a ? 1 : 0;
        }
        assertTrue(hits > 4_000 && hits < 6_000, "hits=" + hits);
    }
}
