package za.co.neroland.nerospace.rocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.config.ConfigValue;
import za.co.neroland.nerospace.config.NerospaceConfig;

/**
 * 2.1 crewed rockets (D10–D15): up to four players, the first aboard is the pilot. Pure rules plus source checks of
 * the call sites that need a live world (the pattern of {@link PodArrivalTest}).
 */
class CrewRocketTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));

    // --- pilot-only launch ---------------------------------------------------------------------------------

    @Test
    @DisplayName("only the pilot (first aboard) may launch or set course; an empty rocket boards the presser")
    void pilotOnly() throws Exception {
        assertTrue(LaunchSeat.mayLaunch(true, false), "empty rocket: the presser boards and flies");
        assertTrue(LaunchSeat.mayLaunch(false, true), "the pilot presses Launch");
        assertFalse(LaunchSeat.mayLaunch(false, false), "a crew member or a bystander cannot");
        assertEquals("a", Crew.pilotOf(List.of("a", "b", "c")));
        assertNull(Crew.pilotOf(List.of()));

        String rocket = PodArrivalTest.source("RocketEntity.java");
        String board = PodArrivalTest.body(rocket, "public boolean boardAndLaunch(ServerPlayer player)");
        assertTrue(board.contains("LaunchSeat.mayLaunch(empty, Crew.isPilot(this, player))"), board);
        assertTrue(board.indexOf("LaunchSeat.mayLaunch(") < board.indexOf("startLaunch()"));
        for (String sig : new String[] { "public DestinationSelection.Outcome selectDestination(",
                "public void cycleStation(@Nullable Player viewer)", "public DestinationSelection.Outcome selectStation(",
                "public void cyclePad(@Nullable Player viewer)",
                "public DestinationSelection.Outcome selectPad(int index, @Nullable Player viewer)",
                "public void applyDefaultDock(" }) {
            assertTrue(PodArrivalTest.body(rocket, sig).contains("mayCommand("), sig + " must be pilot-gated");
        }
        assertTrue(PodArrivalTest.body(rocket, "public boolean canLaunch()").contains("Crew.pilot(this)"));

        String be = PodArrivalTest.source("LaunchControllerBlockEntity.java");
        assertTrue(PodArrivalTest.body(be, "public DestinationSelection.Outcome cycleRocketStation(")
                .contains("mayCommand(player)"));
        assertTrue(PodArrivalTest.body(be, "public DestinationSelection.Outcome cycleRocketPad(@Nullable Player")
                .contains("mayCommand(player)"));
        assertTrue(PodArrivalTest.source("RocketMenu.java").contains("current.selectPad(pad, player)"));

        DestinationSelection.Outcome notPilot = DestinationSelection.Outcome.NOT_PILOT;
        assertFalse(notPilot.accepted());
        assertEquals("gui.nerospace.dest.reason.pilot", notPilot.langKey());
        assertEquals(notPilot, DestinationSelection.decodeOutcome(DestinationSelection.encodeOutcome(7, notPilot)),
                "the new outcome still fits the 4-bit synced code");
    }

    @Test
    @DisplayName("every getFirstPassenger() in the rocket went through the Crew helpers")
    void noBareFirstPassenger() throws Exception {
        assertFalse(PodArrivalTest.source("RocketEntity.java").contains("getFirstPassenger()"));
    }

    // --- crew cap ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("rockets.maxCrew defaults to 4, is clamped to 1..4 and is server-authoritative")
    void crewCap() throws Exception {
        assertEquals(1, Crew.cap(0));
        assertEquals(1, Crew.cap(-3));
        assertEquals(3, Crew.cap(3));
        assertEquals(4, Crew.cap(4));
        assertEquals(Crew.HARD_CAP, Crew.cap(99), "no config seats more than four");
        assertTrue(Crew.hasSeat(3, 4));
        assertFalse(Crew.hasSeat(4, 4));
        assertFalse(Crew.hasSeat(4, 99), "hard cap");
        assertFalse(Crew.hasSeat(1, 1), "maxCrew 1 is the 2.0 one-seat rocket");
        assertTrue(Crew.hasSeat(0, 1));

        ConfigValue<?> v = NerospaceConfig.schema().value("rockets.maxCrew");
        assertNotNull(v);
        assertEquals(String.valueOf(Crew.DEFAULT_MAX), v.defaultAsString());
        assertTrue(v.serverAuthoritative());

        String add = PodArrivalTest.body(PodArrivalTest.source("RocketEntity.java"),
                "protected boolean canAddPassenger(Entity passenger)");
        assertTrue(add.contains("instanceof Player") && add.contains("Crew.maxCrew()"), add);
        assertTrue(cargoSource().replaceAll("\\s+", " ")
                .contains("protected boolean canAddPassenger(Entity passenger) { return false; }"),
                "cargo rockets stay uncrewed");
    }

    @Test
    @DisplayName("boarding is only the viewer's own Take seat press on the console; right-click opens it as in 2.0")
    void boarding() throws Exception {
        String rocket = PodArrivalTest.source("RocketEntity.java");
        String interact = PodArrivalTest.body(rocket, "public InteractionResult interact(");
        // S6-1: vanilla never seats a sneaking player, so there is no sneak gesture; sneaking or not opens the console.
        assertFalse(interact.contains("isSecondaryUseActive()") || interact.contains("isShiftKeyDown()"), interact);
        assertFalse(interact.contains("board(") || interact.contains("startRiding("), "a click never seats anyone");
        assertTrue(interact.contains("MenuOpener.openOrConsume(serverPlayer, this)"));
        String board = PodArrivalTest.body(rocket, "private boolean board(ServerPlayer player)");
        assertTrue(board.contains("player.startRiding(this)"), "the boarder seats themselves");
        assertTrue(board.contains("isLaunching()") && board.contains("isDescending()"));
        // board(...) is private and reached only from takeSeat(...), which only the console's button calls.
        String take = PodArrivalTest.body(rocket, "public Crew.SeatAnswer takeSeat(ServerPlayer player, boolean viewer)");
        assertTrue(take.contains("seatAnswer(player, viewer)") && take.indexOf("seatAnswer(") < take.indexOf("board(player)"));
        assertEquals(1, count(rocket.replace("private boolean board(ServerPlayer player)", ""), "board(player)"));
        assertEquals(0, count(rocket.replace("Crew.takeSeat(", ""), ".takeSeat("), "the rocket never seats anyone by itself");
        String seat = PodArrivalTest.body(rocket, "public Crew.SeatAnswer seatAnswer(Player player, boolean viewer)");
        assertTrue(seat.contains("hatchOpens()") && seat.contains("isLaunching() || isDescending()")
                && seat.contains("Crew.maxCrew()") && seat.contains("player.isShiftKeyDown()")
                && seat.contains("getBoundingBox().distanceToSqr(player.getEyePosition())"), seat);

        String menu = PodArrivalTest.source("RocketMenu.java");
        String click = PodArrivalTest.body(menu, "public boolean clickMenuButton(Player player, int id)");
        assertTrue(click.contains("current.takeSeat(seating, isViewer(seating))"), click);
        assertEquals(1, count(menu, ".takeSeat("), "one caller: the button");
        assertTrue(PodArrivalTest.body(menu, "boolean isViewer(Player player)")
                .contains("player == this.viewer && player.containerMenu == this"));
    }

    @Test
    @DisplayName("Take seat is button 4, appended after every console id in use and claimed by no other range")
    void takeSeatButtonId() {
        assertEquals(4, RocketMenu.BUTTON_TAKE_SEAT);
        int[] used = { RocketMenu.BUTTON_LAUNCH, RocketMenu.BUTTON_CYCLE_DEST, RocketMenu.BUTTON_CYCLE_STATION,
                RocketMenu.BUTTON_CYCLE_PAD };
        for (int id : used) {
            assertTrue(id < RocketMenu.BUTTON_TAKE_SEAT);
        }
        assertTrue(RocketMenu.BUTTON_TAKE_SEAT < RocketMenu.SELECT_DEST_BASE);
        assertEquals(DockSelection.NOT_A_SLOT, DockSelection.slotForButton(RocketMenu.BUTTON_TAKE_SEAT));
        assertEquals(PadSelection.NOT_A_PAD, PadSelection.indexForButton(RocketMenu.BUTTON_TAKE_SEAT));
    }

    @Test
    @DisplayName("Take seat server rules: viewer, reach, crewed hull, not in flight, not aboard, a free seat, no sneak")
    void takeSeatRules() {
        double near = 1.0D;
        double far = (Crew.SEAT_REACH + 0.5D) * (Crew.SEAT_REACH + 0.5D);
        assertEquals(Crew.SeatAnswer.OK, Crew.takeSeat(true, near, true, false, false, 0, 4, false), "empty: pilot");
        assertEquals(Crew.SeatAnswer.OK, Crew.takeSeat(true, near, true, false, false, 3, 4, false), "last seat: crew");
        assertEquals(Crew.SeatAnswer.NOT_VIEWER, Crew.takeSeat(false, near, true, false, false, 0, 4, false),
                "nobody is seated by someone else's press");
        assertEquals(Crew.SeatAnswer.OUT_OF_REACH, Crew.takeSeat(true, far, true, false, false, 0, 4, false));
        assertEquals(Crew.SeatAnswer.OUT_OF_REACH, Crew.takeSeat(true, Double.NaN, true, false, false, 0, 4, false));
        assertEquals(Crew.SeatAnswer.OK, Crew.takeSeat(true, Crew.SEAT_REACH * Crew.SEAT_REACH, true, false, false,
                0, 4, false), "the edge of reach counts");
        assertEquals(Crew.SeatAnswer.UNCREWED, Crew.takeSeat(true, near, false, false, false, 0, 4, false), "cargo");
        assertEquals(Crew.SeatAnswer.IN_FLIGHT, Crew.takeSeat(true, near, true, true, false, 0, 4, false));
        assertEquals(Crew.SeatAnswer.ALREADY_ABOARD, Crew.takeSeat(true, near, true, false, true, 1, 4, false));
        assertEquals(Crew.SeatAnswer.FULL, Crew.takeSeat(true, near, true, false, false, 4, 4, false));
        assertEquals(Crew.SeatAnswer.FULL, Crew.takeSeat(true, near, true, false, false, 1, 1, false), "cap 1 = 2.0");
        assertEquals(Crew.SeatAnswer.FULL, Crew.takeSeat(true, near, true, false, false, 4, 9, false), "hard cap 4");
        assertEquals(Crew.SeatAnswer.SNEAKING, Crew.takeSeat(true, near, true, false, false, 0, 4, true),
                "vanilla refuses a sneaking rider: say so instead of failing silently");
        // Order: the viewer check comes first, then reach, so a stranger learns nothing about the hull.
        assertEquals(Crew.SeatAnswer.NOT_VIEWER, Crew.takeSeat(false, far, false, true, true, 4, 4, true));
        assertEquals(Crew.SeatAnswer.OUT_OF_REACH, Crew.takeSeat(true, far, false, true, true, 4, 4, true));
        for (Crew.SeatAnswer a : Crew.SeatAnswer.values()) {
            assertEquals(a, Crew.SeatAnswer.decode(a.encode()));
            assertTrue(a.encode() > 0, "0 is 'not synced yet'");
        }
        assertEquals(Crew.SeatAnswer.OK, Crew.SeatAnswer.decode(0));
        assertEquals(Crew.SeatAnswer.OK, Crew.SeatAnswer.decode(99));
    }

    // --- cooldown per seat and the fee -------------------------------------------------------------------

    @Test
    @DisplayName("every seated player's cooldown is checked; the pilot is told 'a crew member', never who")
    void cooldownPerSeat() {
        UUID pilot = UUID.randomUUID();
        UUID crew = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        AtomicInteger charges = new AtomicInteger();
        try {
            LaunchCooldowns.tryStart(crew, 1_000L, DescentMath.LAUNCH_COOLDOWN); // crew launched alone moments ago
            LaunchCooldowns.CrewGate gate = LaunchCooldowns.gateCrew(List.of(pilot, crew, third), 1_040L,
                    DescentMath.LAUNCH_COOLDOWN, () -> {
                        charges.incrementAndGet();
                        return null;
                    });
            assertEquals(LaunchCooldowns.Verdict.CREW_COOLDOWN, gate.verdict());
            assertEquals(List.of(1), gate.coolingSeats());
            assertEquals(0, charges.get(), "nobody pays for a launch the cooldown refused");
            assertFalse(LaunchCooldowns.coolingDown(pilot, 1_041L), "the refused launch started nobody's cooldown");
            assertEquals(60L, LaunchCooldowns.remaining(crew, 1_040L));
            assertEquals(0L, LaunchCooldowns.remaining(pilot, 1_040L));

            LaunchCooldowns.tryStart(pilot, 1_050L, DescentMath.LAUNCH_COOLDOWN);
            assertEquals(LaunchCooldowns.Verdict.COOLDOWN, LaunchCooldowns.gateCrew(List.of(pilot, crew), 1_060L,
                    DescentMath.LAUNCH_COOLDOWN, () -> null).verdict(), "the pilot's own cooldown");

            String rocket = PodArrivalTest.source("RocketEntity.java");
            String start = PodArrivalTest.body(rocket, "public void startLaunch()");
            assertTrue(start.contains("LaunchCooldowns.gateCrew("));
            assertFalse(start.contains("LaunchCooldowns.gate("), "never the per-rider gate: it would charge each seat");
            assertTrue(start.contains("\"entity.nerospace.rocket.crew_cooldown\""));
            assertTrue(start.contains("\"entity.nerospace.rocket.your_cooldown\""));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            LaunchCooldowns.forget(pilot);
            LaunchCooldowns.forget(crew);
            LaunchCooldowns.forget(third);
        }
    }

    @Test
    @DisplayName("the NeroEconomy fee is charged once per flight, to the pilot, and every seat starts its cooldown")
    void feeOnce() throws Exception {
        List<UUID> seats = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        AtomicInteger charges = new AtomicInteger();
        try {
            LaunchCooldowns.CrewGate ok = LaunchCooldowns.gateCrew(seats, 9_000L, DescentMath.LAUNCH_COOLDOWN, () -> {
                charges.incrementAndGet();
                return null;
            });
            assertEquals(LaunchCooldowns.Verdict.OK, ok.verdict());
            assertEquals(1, charges.get(), "a flight, not a head count");
            for (UUID seat : seats) {
                assertTrue(LaunchCooldowns.coolingDown(seat, 9_001L));
            }
            List<UUID> fresh = List.of(UUID.randomUUID(), UUID.randomUUID());
            LaunchCooldowns.CrewGate refused = LaunchCooldowns.gateCrew(fresh, 9_000L, DescentMath.LAUNCH_COOLDOWN,
                    () -> "message.test.refused");
            assertEquals(LaunchCooldowns.Verdict.REFUSED, refused.verdict());
            assertFalse(LaunchCooldowns.coolingDown(fresh.get(1), 9_001L), "a refused fee starts no cooldown");
            for (UUID u : fresh) {
                LaunchCooldowns.forget(u);
            }
        } finally {
            for (UUID seat : seats) {
                LaunchCooldowns.forget(seat);
            }
        }
        String start = PodArrivalTest.body(PodArrivalTest.source("RocketEntity.java"), "public void startLaunch()");
        assertTrue(start.contains("chargeFlight(\n                            serverLevel.getServer(), pilot.getUUID()"),
                "charged to the pilot");
        assertEquals(1, count(start, "chargeFlight("));
    }

    // --- oxygen ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("O₂ readiness is crew × the flight's oxygen per seat; a solo flight needs none (2.0)")
    void oxygenReadiness() throws Exception {
        assertEquals(168, Crew.FLIGHT_TICKS, "ascent 100 + descent 60 + settle 8");
        int perSeat = Crew.perSeatMb(true, 10, 2);
        assertEquals(34, perSeat);
        assertEquals(0, Crew.perSeatMb(false, 10, 2), "between two breathable worlds nothing is drawn");
        assertEquals(0, Crew.oxygenNeeded(0, perSeat));
        assertEquals(0, Crew.oxygenNeeded(1, perSeat), "solo: the 2.0 rule");
        assertEquals(68, Crew.oxygenNeeded(2, perSeat));
        assertEquals(136, Crew.oxygenNeeded(4, perSeat));
        assertEquals(2, Crew.oxygenFor(100, perSeat, 4), "O₂ for 2");
        assertEquals(4, Crew.oxygenFor(2_000, perSeat, 4), "capped at the seat count");
        assertEquals(0, Crew.oxygenFor(0, perSeat, 4));
        assertEquals(4, Crew.oxygenFor(0, 0, 4), "no draw: every seat is covered");

        String rocket = PodArrivalTest.source("RocketEntity.java");
        assertTrue(PodArrivalTest.body(rocket, "public boolean isLaunchReady()").contains("oxygenNeeded()"));
        assertTrue(PodArrivalTest.body(rocket, "public int oxygenPerSeat()")
                .contains("OxygenManager.RIDE_DRAIN_PER_CHECK"), "the same drain the ride applies");
        assertFalse(PodArrivalTest.body(rocket, "public int currentFuelCost()").contains("Crew"), "no fuel change");
    }

    @Test
    @DisplayName("the arrival reserve is shared out per seat; one rider keeps the whole reserve")
    void arrivalReserveSplit() throws Exception {
        assertArrayEquals(new int[] { 2_000 }, Crew.splitReserve(2_000, 1));
        assertArrayEquals(new int[] { 3, 3, 2, 2 }, Crew.splitReserve(10, 4));
        assertArrayEquals(new int[] { 0, 0, 0 }, Crew.splitReserve(0, 3));
        assertArrayEquals(new int[] { 0, 0 }, Crew.splitReserve(-5, 2));
        assertArrayEquals(new int[0], Crew.splitReserve(500, 0));
        for (int total : new int[] { 1, 7, 1_999, 30_000 }) {
            for (int crew = 1; crew <= 4; crew++) {
                int sum = 0;
                for (int share : Crew.splitReserve(total, crew)) {
                    sum += share;
                }
                assertEquals(total, sum, "nothing is lost or duplicated");
            }
        }
        String launch = PodArrivalTest.body(PodArrivalTest.source("RocketEntity.java"), "private void completeLaunch0()");
        assertTrue(launch.contains("Crew.splitReserve(getOxygen(), crew.size())"));
        assertTrue(launch.contains("grantArrivalReserve(rider, reserve[i])"));
        assertFalse(launch.contains("grantArrivalReserve(player, getOxygen())"), "no longer the whole tank per rider");
    }

    // --- disconnects ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("pilot disconnects mid-flight: the next seated player is pilot for the arrival only")
    void pilotHandover() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<UUID> seated = new ArrayList<>(List.of(a, b));
        seated.remove(a); // the launch pilot disconnects; vanilla drops them from the passenger list
        assertEquals(b, Crew.pilotOf(seated));
        assertTrue(Crew.handedOver(a, b));
        assertFalse(Crew.handedOver(a, a));
        assertFalse(Crew.handedOver(null, b), "a rocket reloaded mid-flight: 2.0 behaviour");

        String rocket = PodArrivalTest.source("RocketEntity.java");
        String launch = PodArrivalTest.body(rocket, "private void completeLaunch0()");
        assertTrue(launch.contains("ServerPlayer player = Crew.pilotOf(crew);"));
        assertTrue(launch.contains("Crew.handedOver(this.launchPilot, player.getUUID())"));
        assertTrue(PodArrivalTest.body(rocket, "private StationRegistry.StationEntry stationForArrival(")
                .contains("!handedOver && hasCharter(player)"), "a stand-in pilot's charter is never spent");
        // Crew offline at touchdown rejoin beside the rocket; the descent re-seats crew who come back in time.
        assertTrue(PodArrivalTest.body(rocket, "private void touchdown(").contains("CrewLanding.remember("));
        assertTrue(PodArrivalTest.body(rocket, "private void reseatCrew(").contains("rider.startRiding(this)"));
        assertTrue(PodArrivalTest.body(rocket, "protected boolean canAddPassenger(")
                .contains("this.descentCrew.contains("), "a returning crew member is always re-admitted");
    }

    @Test
    @DisplayName("CrewLanding is memory only, consumed on join, bounded and erasable")
    void crewLanding() {
        UUID p = UUID.randomUUID();
        try {
            CrewLanding.remember(p, OVERWORLD, new BlockPos(1, 65, 2));
            assertTrue(CrewLanding.holds(p));
            CrewLanding.Spot spot = CrewLanding.take(p);
            assertNotNull(spot);
            assertEquals(new BlockPos(1, 65, 2), spot.pos());
            assertFalse(CrewLanding.holds(p), "consumed");
            CrewLanding.remember(p, OVERWORLD, BlockPos.ZERO);
            assertTrue(CrewLanding.forget(p));
            List<UUID> many = new ArrayList<>();
            for (int i = 0; i < CrewLanding.MAX_ENTRIES + 10; i++) {
                UUID u = UUID.randomUUID();
                many.add(u);
                CrewLanding.remember(u, OVERWORLD, BlockPos.ZERO);
            }
            assertTrue(CrewLanding.size() <= CrewLanding.MAX_ENTRIES);
            assertFalse(CrewLanding.holds(many.get(0)), "oldest dropped first");
            for (UUID u : many) {
                CrewLanding.forget(u);
            }
        } finally {
            CrewLanding.forget(p);
        }
    }

    // --- MEMBERS docking policy --------------------------------------------------------------------------

    @Test
    @DisplayName("MEMBERS docking is evaluated for the pilot; crew who cannot use the station arrive as visitors")
    void membersForPilot() throws Exception {
        String rocket = PodArrivalTest.source("RocketEntity.java");
        String start = PodArrivalTest.body(rocket, "public void startLaunch()");
        assertTrue(start.contains("Crew.pilot(this) instanceof ServerPlayer docking"));
        assertTrue(start.contains("canDockAt(target, docking)"));
        String launch = PodArrivalTest.body(rocket, "private void completeLaunch0()");
        assertTrue(launch.contains("StationRegistry.canUse(entry, player)"), "visitor flag from the pilot");
        assertTrue(launch.contains("origin.registered(),\n                        player, slot"), "Flight.rider = pilot");
        assertTrue(PodArrivalTest.body(rocket, "private StationRegistry.StationEntry stationForArrival(")
                .contains("canDockAt(chosen, player)"));
        // The station's own rule is unchanged: the pilot who cannot use a MEMBERS station is refused; crew are not
        // checked at all, so they arrive under the existing visitor rules.
        assertFalse(StationDockingRule.mayChoose(za.co.neroland.nerospace.world.station.DockingPolicy.MEMBERS, false));
        assertTrue(StationDockingRule.mayChoose(za.co.neroland.nerospace.world.station.DockingPolicy.MEMBERS, true));
    }

    // --- everyone travels ------------------------------------------------------------------------------------

    @Test
    @DisplayName("transfer, re-seat, touchdown, arrival message and Star Guide drives run for every seat")
    void everyoneTravels() throws Exception {
        String rocket = PodArrivalTest.source("RocketEntity.java");
        String launch = PodArrivalTest.body(rocket, "private void completeLaunch0()");
        assertTrue(launch.contains("for (int i = 0; i < crew.size(); i++)"));
        assertTrue(launch.contains("rider.startRiding(arriving)"));
        String touchdown = PodArrivalTest.body(rocket, "private void touchdown(");
        assertTrue(touchdown.contains("for (ServerPlayer rider : released)")
                && touchdown.contains("arrivalSideEffects(level, rider)"));
        assertFalse(PodArrivalTest.body(rocket, "private void arrivalSideEffects(").contains("this.arrivalMessage = null"),
                "the message is not consumed by the first crew member");
        assertTrue(PodArrivalTest.body(rocket, "private void arrivalDrives(")
                .contains("this.arrivalDrivesDone.add(player.getUUID())"), "once per player, not once per rocket");
    }

    @Test
    @DisplayName("seat offsets: four per tier, distinct, inside the hull wall at that tier's scale")
    void seatOffsets() {
        double[] scale = { 1.5D, 1.8D, 2.1D, 2.4D };
        double shoulderBlocks = 0.3D;
        double hullWallPx = 7.0D;
        for (RocketTier tier : RocketTier.values()) {
            List<RocketTier.Seat> seats = tier.seatOffsets();
            assertEquals(Crew.HARD_CAP, seats.size(), tier.name());
            assertEquals(seats.size(), new java.util.HashSet<>(seats).size(), "distinct seats " + tier);
            double shoulderPx = shoulderBlocks * 16.0D / scale[tier.ordinal()];
            for (RocketTier.Seat seat : seats) {
                double r = Math.hypot(seat.xPx(), seat.zPx());
                assertTrue(r + shoulderPx <= hullWallPx, tier + " seat " + seat + " pokes through the hull");
            }
        }
    }

    // --- privacy and save compatibility ------------------------------------------------------------------------

    @Test
    @DisplayName("no identity in any breadcrumb or threshold; crew is never persisted (D15)")
    void noIdentity() throws Exception {
        String rocket = PodArrivalTest.source("RocketEntity.java");
        int at = 0;
        while ((at = rocket.indexOf("breadcrumb(", at)) >= 0) {
            String call = rocket.substring(at, rocket.indexOf(");", at));
            for (String leak : new String[] { "getUUID", "getName", "getGameProfile", "getScoreboardName",
                    "blockPosition", "getX()", "launchPilot", "launchCrew", "descentCrew" }) {
                assertFalse(call.contains(leak), "breadcrumb leaks " + leak + ": " + call);
            }
            at++;
        }
        assertTrue(rocket.contains("\" crew=\" + crew.size()"), "the crew count only");
        String worlds = progressionSource("WorldsProgress.java");
        assertTrue(worlds.contains("public static void crewLaunch(int crew)"), "a head count, never an identity");

        String save = PodArrivalTest.body(rocket, "protected void addAdditionalSaveData(ValueOutput output)");
        String load = PodArrivalTest.body(rocket, "protected void readAdditionalSaveData(ValueInput input)");
        for (String body : new String[] { save, load }) {
            for (String field : new String[] { "Crew", "Pilot", "descentCrew", "launchCrew", "launchPilot" }) {
                assertFalse(body.contains(field), "crew is not persisted: " + field);
            }
        }
    }

    @Test
    @DisplayName("a 2.0.0 single-rider rocket still works: no new NBT key, one seat behaves exactly as 2.0")
    void singleRiderCompat() throws Exception {
        String rocket = PodArrivalTest.source("RocketEntity.java");
        String load = PodArrivalTest.body(rocket, "protected void readAdditionalSaveData(ValueInput input)");
        // Every key Stage 3 might have added would be read here; none is (vanilla Passengers carry the rider).
        assertFalse(load.contains("\"Crew"));
        assertArrayEquals(new int[] { 1_234 }, Crew.splitReserve(1_234, 1), "the whole reserve, as 2.0");
        assertEquals(0, Crew.oxygenNeeded(1, 34), "no new O₂ requirement for one rider");
        UUID rider = UUID.randomUUID();
        AtomicInteger charges = new AtomicInteger();
        try {
            assertEquals(LaunchCooldowns.Verdict.OK, LaunchCooldowns.gateCrew(List.of(rider), 100L,
                    DescentMath.LAUNCH_COOLDOWN, () -> {
                        charges.incrementAndGet();
                        return null;
                    }).verdict());
            assertEquals(LaunchCooldowns.Verdict.COOLDOWN, LaunchCooldowns.gateCrew(List.of(rider), 150L,
                    DescentMath.LAUNCH_COOLDOWN, () -> {
                        charges.incrementAndGet();
                        return null;
                    }).verdict(), "one seat = the 2.0 gate");
            assertEquals(1, charges.get());
        } finally {
            LaunchCooldowns.forget(rider);
        }
        // A rocket reloaded mid-descent has no crew list and releases whoever is seated (2.0 fallback).
        assertTrue(PodArrivalTest.body(rocket, "private void touchdown(").contains("Crew.seatedServer(this)"));
    }

    @Test
    @DisplayName("link rockets rows gain crew_count only (schema v3, additive)")
    void linkCrewCount() throws Exception {
        assertTrue(za.co.neroland.nerospace.link.NerospaceLinkModule.SCHEMA_VERSION >= 3, "v3 added crew_count");
        String snap = linkSource("NerospaceLinkSnapshots.java");
        assertTrue(snap.contains("row.addProperty(\"crew_count\", za.co.neroland.nerospace.rocket.Crew.count(rocket));"));
        assertFalse(snap.contains("getPassengers()"), "never the crew's identities");
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    private static String cargoSource() throws Exception {
        return pkgSource("route", "CargoRocketEntity.java");
    }

    private static String linkSource(String file) throws Exception {
        return pkgSource("link", file);
    }

    private static String progressionSource(String file) throws Exception {
        return pkgSource("progression", file);
    }

    private static String pkgSource(String pkg, String file) throws Exception {
        for (Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath(); p != null; p = p.getParent()) {
            Path c = p.resolve("common/src/main/java/za/co/neroland/nerospace").resolve(pkg).resolve(file);
            if (Files.isRegularFile(c)) {
                return Files.readString(c).replace("\r\n", "\n");
            }
        }
        throw new IllegalStateException("source not found: " + pkg + "/" + file);
    }
}
