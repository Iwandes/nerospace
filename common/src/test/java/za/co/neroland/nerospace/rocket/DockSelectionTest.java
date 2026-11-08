package za.co.neroland.nerospace.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerospace.rocket.DestinationSelection.Outcome;
import za.co.neroland.nerospace.rocket.StationRegistry.StationEntry;
import za.co.neroland.nerospace.world.station.DockingPolicy;

/** The dock-selection rules shared by the rocket, both consoles and the arrival path. */
class DockSelectionTest {

    static final String ME = "4b2b9a4e-1c2d-4e5f-8a9b-0c1d2e3f4a5b";
    static final String OTHER = "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a";

    static StationEntry station(int slot, String owner) {
        return new StationEntry(slot, "S" + slot, new BlockPos(4096 * (slot + 1), 64, 0), owner);
    }

    @Test
    @DisplayName("default dock: the viewer's own station first, even when founded after one they belong to")
    void ownerFirst() {
        List<StationEntry> all = List.of(station(0, OTHER), station(1, OTHER).withMembers(List.of(ME)),
                station(2, ME), station(3, ME));
        assertEquals(2, DockSelection.defaultSlot(all, ME));
        assertEquals(DockSelection.Relation.YOURS, DockSelection.relation(all.get(2), ME));
        assertEquals(DockSelection.Relation.MEMBER, DockSelection.relation(all.get(1), ME));
        assertEquals(DockSelection.Relation.PUBLIC, DockSelection.relation(all.get(0), ME));
    }

    @Test
    @DisplayName("default dock: a station they are a member of when they own none (MEMBERS policy included)")
    void memberFallback() {
        List<StationEntry> all = List.of(station(0, OTHER),
                station(1, OTHER).withMembers(List.of(ME)).withDocking(DockingPolicy.MEMBERS));
        assertEquals(1, DockSelection.defaultSlot(all, ME));
    }

    @Test
    @DisplayName("default dock: the origin platform when they own and belong to nothing, or nobody is looking")
    void originFallback() {
        List<StationEntry> all = List.of(station(0, OTHER), station(1, ""));
        assertEquals(DockSelection.ORIGIN, DockSelection.defaultSlot(all, ME));
        assertEquals(DockSelection.ORIGIN, DockSelection.defaultSlot(List.of(), ME));
        assertEquals(DockSelection.ORIGIN, DockSelection.defaultSlot(all, null));
        // An unowned (erased / legacy) station is public, never "yours".
        assertEquals(DockSelection.Relation.PUBLIC, DockSelection.relation(all.get(1), ME));
    }

    @Test
    @DisplayName("MEMBERS stations are skipped for outsiders: not offered, not defaulted, refused with a reason")
    void membersSkip() {
        StationEntry closed = station(0, OTHER).withDocking(DockingPolicy.MEMBERS);
        StationEntry open = station(1, OTHER);
        List<StationEntry> all = List.of(closed, open);
        assertFalse(DockSelection.mayChoose(closed, ME));
        assertTrue(DockSelection.mayChoose(closed, OTHER));
        assertEquals(List.of(DockSelection.ORIGIN, 1),
                DockSelection.choices(all, e -> DockSelection.mayChoose(e, ME), false));
        assertEquals(DockSelection.ORIGIN, DockSelection.defaultSlot(all, ME));
        assertEquals(Outcome.MEMBERS_ONLY, DockSelection.evaluate(0, DockSelection.ORIGIN, false, closed,
                DockSelection.mayChoose(closed, ME), true));
        assertEquals(Outcome.OK, DockSelection.evaluate(1, DockSelection.ORIGIN, false, open, true, false));
    }

    @Test
    @DisplayName("explicit picks: launching, unknown slots, New Station without a charter; re-picking stands")
    void evaluate() {
        StationEntry s = station(3, OTHER);
        assertEquals(Outcome.LAUNCHING, DockSelection.evaluate(3, -1, true, s, true, true));
        assertEquals(Outcome.UNKNOWN_TARGET, DockSelection.evaluate(5, -1, false, null, false, true));
        assertEquals(Outcome.NO_CHARTER, DockSelection.evaluate(DockSelection.NEW_STATION, -1, false, null, false,
                false));
        assertEquals(Outcome.OK, DockSelection.evaluate(DockSelection.NEW_STATION, -1, false, null, false, true));
        assertEquals(Outcome.ALREADY_SELECTED, DockSelection.evaluate(DockSelection.ORIGIN, DockSelection.ORIGIN,
                false, null, false, false));
        assertTrue(Outcome.ALREADY_SELECTED.accepted(), "re-picking the origin marks it as an explicit choice");
    }

    @Test
    @DisplayName("the cycler keeps the 2.0.0 order: origin, stations, New Station, origin")
    void cycleOrder() {
        List<Integer> withNew = DockSelection.choices(List.of(station(0, ME), station(4, OTHER)), e -> true, true);
        assertEquals(List.of(-1, 0, 4, -2), withNew);
        assertEquals(0, DockSelection.next(withNew, -1));
        assertEquals(4, DockSelection.next(withNew, 0));
        assertEquals(-2, DockSelection.next(withNew, 4));
        assertEquals(-1, DockSelection.next(withNew, -2));
        assertEquals(-2, DockSelection.next(withNew, 9), "a vanished slot moves on like 2.0.0");
        List<Integer> noNew = DockSelection.choices(List.of(), e -> true, false);
        assertEquals(-1, DockSelection.next(noNew, -1));
        assertEquals(-1, DockSelection.next(noNew, 7));
    }

    @Test
    @DisplayName("no reset on tier change or launch: the default only fills an untouched origin")
    void noResetOnTierChange() throws Exception {
        assertTrue(DockSelection.shouldApplyDefault(DockSelection.ORIGIN, false));
        assertFalse(DockSelection.shouldApplyDefault(DockSelection.ORIGIN, true), "an explicit origin pick stands");
        assertFalse(DockSelection.shouldApplyDefault(3, false));
        assertFalse(DockSelection.shouldApplyDefault(DockSelection.NEW_STATION, false));
        String rocket = PodArrivalTest.source("RocketEntity.java");
        String setTier = PodArrivalTest.body(rocket, "public void setTier(");
        assertFalse(setTier.contains("DATA_STATION") || setTier.contains("applyDefaultDock"), setTier);
        for (String sig : new String[] { "public boolean boardAndLaunch(", "public void startLaunch(",
                "private void completeLaunch0(" }) {
            assertFalse(PodArrivalTest.body(rocket, sig).contains("applyDefaultDock"), sig);
        }
        // Deploy and the open paths do apply it.
        assertTrue(PodArrivalTest.source("RocketItem.java").contains("rocket.applyDefaultDock(player)"));
        assertTrue(PodArrivalTest.body(rocket, "public void prepareConsoleFor(").contains("applyDefaultDock("));
        assertTrue(PodArrivalTest.body(rocket, "public DestinationSelection.Outcome selectDestination(")
                .contains("applyDefaultDock(player)"));
    }

    @Test
    @DisplayName("button ids are bounded, append-only and never collide with the 2.0 ids")
    void boundedIds() {
        Set<Integer> ids = new HashSet<>();
        for (int slot = DockSelection.NEW_STATION; slot < StationRegistry.MAX_STATIONS; slot++) {
            int id = DockSelection.buttonFor(slot);
            assertEquals(slot, DockSelection.slotForButton(id));
            assertEquals(PadSelection.NOT_A_PAD, PadSelection.indexForButton(id));
            assertTrue(ids.add(id));
        }
        for (int pad = PadSelection.AUTO; pad < PadRegistry.MAX_PADS; pad++) {
            int id = PadSelection.buttonFor(pad);
            assertEquals(pad, PadSelection.indexForButton(id));
            assertEquals(DockSelection.NOT_A_SLOT, DockSelection.slotForButton(id));
            assertTrue(ids.add(id));
        }
        assertEquals(200, DockSelection.buttonFor(DockSelection.NEW_STATION));
        assertEquals(201, DockSelection.buttonFor(DockSelection.ORIGIN));
        assertEquals(300, PadSelection.buttonFor(PadSelection.AUTO));
        for (int legacy : new int[] { 0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 100, 107, 199 }) {
            assertEquals(DockSelection.NOT_A_SLOT, DockSelection.slotForButton(legacy));
            assertEquals(PadSelection.NOT_A_PAD, PadSelection.indexForButton(legacy));
            assertFalse(ids.contains(legacy));
        }
        assertEquals(DockSelection.NOT_A_SLOT, DockSelection.slotForButton(200 + DockSelection.SELECT_SPAN));
        assertEquals(PadSelection.NOT_A_PAD, PadSelection.indexForButton(300 + PadSelection.SELECT_SPAN));
        assertEquals(PadSelection.NOT_A_PAD, PadSelection.indexForButton(-1));
        assertEquals(Outcome.OUT_OF_RANGE, DockSelection.evaluate(-3, -1, false, null, false, true));
        assertEquals(Outcome.OUT_OF_RANGE, DockSelection.evaluate(StationRegistry.MAX_STATIONS, -1, false, null,
                false, true));
        // The controller declares the ids D1 names.
        assertEquals(10, za.co.neroland.nerospace.menu.LaunchControllerMenu.BUTTON_CYCLE_STATION);
        assertEquals(11, za.co.neroland.nerospace.menu.LaunchControllerMenu.BUTTON_CYCLE_PAD);
        assertEquals(200, za.co.neroland.nerospace.menu.LaunchControllerMenu.SELECT_STATION_BASE);
        assertEquals(300, za.co.neroland.nerospace.menu.LaunchControllerMenu.SELECT_PAD_BASE);
    }

    @Test
    @DisplayName("pad picks: nearest and listed pads stand, a vanished pad is named, the cycler wraps to nearest")
    void pads() {
        assertEquals(Outcome.OK, PadSelection.evaluate(2, 3, -1, false, true));
        assertEquals(Outcome.OK, PadSelection.evaluate(PadSelection.AUTO, 3, 1, false, true));
        assertEquals(Outcome.ALREADY_SELECTED, PadSelection.evaluate(1, 3, 1, false, true));
        assertEquals(Outcome.UNKNOWN_TARGET, PadSelection.evaluate(3, 3, -1, false, true));
        assertEquals(Outcome.OUT_OF_RANGE, PadSelection.evaluate(0, 3, -1, false, false));
        assertEquals(Outcome.OUT_OF_RANGE, PadSelection.evaluate(-2, 3, -1, false, true));
        assertEquals(Outcome.LAUNCHING, PadSelection.evaluate(0, 3, -1, true, true));
        assertEquals(0, PadSelection.next(-1, 2));
        assertEquals(1, PadSelection.next(0, 2));
        assertEquals(-1, PadSelection.next(1, 2));
        assertEquals(-1, PadSelection.next(0, 0));
        assertEquals(130, PadSelection.roundedDistance(127));
        assertEquals(-1, PadSelection.roundedDistance(-1));
    }

    @Test
    @DisplayName("arrival fall-through names why: dock gone, MEMBERS mid-flight, no charter, registry full")
    void fallThrough() {
        StationEntry s = station(0, OTHER);
        assertEquals(DockSelection.FallThrough.NONE, DockSelection.arrivalFallThrough(s, true));
        assertEquals(DockSelection.FallThrough.DOCK_GONE, DockSelection.arrivalFallThrough(null, false));
        assertEquals(DockSelection.FallThrough.MEMBERS_ONLY, DockSelection.arrivalFallThrough(s, false));
        assertEquals(DockSelection.FallThrough.NO_CHARTER,
                DockSelection.foundingFallThrough(StationFounding.Outcome.REDIRECT));
        assertEquals(DockSelection.FallThrough.REGISTRY_FULL,
                DockSelection.foundingFallThrough(StationFounding.Outcome.REDIRECT_FULL));
        assertEquals(DockSelection.FallThrough.NONE,
                DockSelection.foundingFallThrough(StationFounding.Outcome.FOUND_CONSUME));
        assertNull(DockSelection.FallThrough.NONE.langKey());
        assertEquals("entity.nerospace.rocket.pad_missing", DockSelection.FallThrough.DOCK_GONE.langKey());
        assertEquals("entity.nerospace.rocket.station_members_only", DockSelection.FallThrough.MEMBERS_ONLY.langKey());
        assertEquals("entity.nerospace.rocket.no_charter", DockSelection.FallThrough.NO_CHARTER.langKey());
    }

    @Test
    @DisplayName("controller writes through the rocket and invalidates its cache on every dock/pad change")
    void controllerCacheInvalidation() throws Exception {
        String be = PodArrivalTest.source("LaunchControllerBlockEntity.java");
        for (String sig : new String[] { "public DestinationSelection.Outcome selectRocketStation(",
                "public DestinationSelection.Outcome selectRocketPad(",
                "public DestinationSelection.Outcome cycleRocketStation(",
                "public DestinationSelection.Outcome cycleRocketPad(", "public void prepareOpen(" }) {
            String body = PodArrivalTest.body(be, sig);
            assertTrue(body.contains("this.cacheTick = -1L;"), sig + " must invalidate the menu cache");
            assertTrue(body.contains("rocket."), sig + " must write through the rocket");
        }
        String refresh = PodArrivalTest.body(be, "private void refresh(");
        // 2.1 Stage 2: the cached dock value is the station slot or a free-dock value (DockSelection.isFree).
        assertTrue(refresh.contains("this.rocketStation = rocket.getDockValue();"));
        assertTrue(refresh.contains("this.rocketPad = rocket.getPadIndex();"));
        assertEquals(27, LaunchControllerBlockEntity.DATA_COUNT); // 2.1 Stage 3: [24..26] crew
        assertEquals(22, LaunchControllerBlockEntity.DATA_STATION);
        assertEquals(23, LaunchControllerBlockEntity.DATA_PAD);
    }
}
