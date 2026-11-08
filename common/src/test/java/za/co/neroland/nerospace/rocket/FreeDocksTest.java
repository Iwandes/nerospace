package za.co.neroland.nerospace.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerospace.network.FoundStationPayload;
import za.co.neroland.nerospace.network.FreeDockSyncPayload;
import za.co.neroland.nerospace.rocket.DestinationSelection.Outcome;

/** D8: hand-placed Docking Rings outside every claim are proximity-visible free docks — never a server-wide list. */
class FreeDocksTest {

    static final String DIM = "nerospace:station";
    static final int R = FreeDocks.DEFAULT_RADIUS;

    static PadRegistry.PadNode dock(int id, String name, int x, int z) {
        BlockPos p = new BlockPos(x, 64, z);
        return new PadRegistry.PadNode(id, name, DIM, p, PadKind.DOCK, Optional.of(p), Optional.empty());
    }

    static List<PadRegistry.PadNode> nodes() {
        List<PadRegistry.PadNode> n = new ArrayList<>();
        n.add(dock(0, "Dock 1", 1000, 0));                       // unnamed, 1000 from the origin
        n.add(dock(1, "Dock 2", 2049, 0));                       // just out of range
        n.add(new PadRegistry.PadNode(2, "Pad 3", DIM, new BlockPos(500, 64, 0))); // a launch pad, not a dock
        n.add(dock(3, "Home Dock", 4096 + 13, 0));               // inside Home's claim box
        n.add(dock(4, "Origin ring", 5, 5));                     // inside the origin platform's box
        n.add(dock(5, "Bob's Base", -1500, 1000));               // named, ~1803 away
        n.add(dock(6, "Dock 7", 2048, 0));                       // exactly at the radius
        return n;
    }

    static List<StationRegistry.StationEntry> stations() {
        StationRegistry r = new StationRegistry();
        r.found("Home", StationPlacementTest.OWNER);
        return r.all();
    }

    @Test
    @DisplayName("visibility: docks within the radius of the origin before a first flight, outside every claim")
    void visibilityFromOrigin() {
        List<FreeDocks.Dock> seen = FreeDocks.visible(nodes(), stations(), FreeDocks.anchor(null), R);
        List<Integer> ids = seen.stream().map(FreeDocks.Dock::padId).toList();
        assertEquals(List.of(0, 5, 6), ids, "nearest first; out-of-range, pad, claimed and origin rings excluded");
        assertFalse(seen.get(0).named(), "an auto-labelled ring is unnamed (Dock @ x,z)");
        assertTrue(seen.get(1).named());
        assertEquals("Bob's Base", seen.get(1).name());
        assertEquals(1000, seen.get(0).distance());
        assertEquals(1800, seen.get(1).distance(), "rounded to 10 blocks");
    }

    @Test
    @DisplayName("visibility follows the rocket's last station position, and a far rocket sees nothing")
    void visibilityFromLastStation() {
        List<FreeDocks.Dock> near = FreeDocks.visible(nodes(), stations(), new BlockPos(4000, 64, 0), R);
        assertTrue(near.stream().anyMatch(d -> d.padId() == 1), "2049 is in range of a rocket that last stood at 4000");
        assertFalse(near.stream().anyMatch(d -> d.padId() == 5), "Bob's Base is 5600 away from there");
        assertTrue(FreeDocks.visible(nodes(), stations(), new BlockPos(100_000, 64, 100_000), R).isEmpty());
        assertTrue(FreeDocks.visible(nodes(), stations(), FreeDocks.anchor(null), 0).stream()
                .noneMatch(d -> d.distance() > 0), "radius 0: nothing but a ring at the anchor itself");
    }

    @Test
    @DisplayName("bounded: at most 16, the nearest; indexOf finds a pick or says it is gone")
    void bounded() {
        List<PadRegistry.PadNode> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(dock(100 + i, "Dock " + (101 + i), 600 + i * 20, 600));
        }
        List<FreeDocks.Dock> seen = FreeDocks.visible(many, List.of(), FreeDocks.anchor(null), R);
        assertEquals(FreeDocks.MAX_VISIBLE, seen.size());
        assertEquals(100, seen.get(0).padId());
        assertEquals(115, seen.get(15).padId());
        assertEquals(3, FreeDocks.indexOf(seen, 103));
        assertEquals(-1, FreeDocks.indexOf(seen, 139), "beyond the cap");
        assertEquals(-1, FreeDocks.indexOf(seen, 9999), "removed");
    }

    @Test
    @DisplayName("a ring that becomes part of a newly founded station's claim stops being a free dock")
    void claimedLater() {
        StationRegistry r = new StationRegistry();
        List<PadRegistry.PadNode> ring = List.of(dock(0, "Dock 1", 1200, 1200));
        assertEquals(1, FreeDocks.visible(ring, r.all(), FreeDocks.anchor(null), R).size());
        r.foundAt("Claimed", StationPlacementTest.OWNER, new BlockPos(1180, 64, 1220));
        assertTrue(FreeDocks.visible(ring, r.all(), FreeDocks.anchor(null), R).isEmpty());
    }

    @Test
    @DisplayName("select ids: 600..615, bounded, never colliding with station or pad ids")
    void freeIds() {
        Set<Integer> taken = new HashSet<>();
        for (int slot = DockSelection.NEW_STATION; slot < StationRegistry.MAX_STATIONS; slot++) {
            taken.add(DockSelection.buttonFor(slot));
        }
        for (int pad = PadSelection.AUTO; pad < PadRegistry.MAX_PADS; pad++) {
            taken.add(PadSelection.buttonFor(pad));
        }
        for (int i = 0; i < FreeDocks.MAX_VISIBLE; i++) {
            int value = DockSelection.freeValue(i);
            int id = DockSelection.buttonFor(value);
            assertEquals(600 + i, id);
            assertTrue(taken.add(id));
            assertEquals(value, DockSelection.slotForButton(id));
            assertTrue(DockSelection.isFree(value));
            assertEquals(PadSelection.NOT_A_PAD, PadSelection.indexForButton(id));
        }
        assertEquals(DockSelection.NOT_A_SLOT, DockSelection.slotForButton(599));
        assertEquals(DockSelection.NOT_A_SLOT, DockSelection.slotForButton(616));
        assertFalse(DockSelection.isFree(DockSelection.FREE_VALUE_BASE + FreeDocks.MAX_VISIBLE));
        assertFalse(DockSelection.isFree(StationRegistry.MAX_STATIONS - 1));
        assertEquals(5, DockSelection.Relation.FREE.ordinal(), "appended");
        assertEquals(Outcome.OK, DockSelection.evaluateFree(2, 3, DockSelection.ORIGIN, false));
        assertEquals(Outcome.ALREADY_SELECTED, DockSelection.evaluateFree(2, 3, DockSelection.freeValue(2), false));
        assertEquals(Outcome.UNKNOWN_TARGET, DockSelection.evaluateFree(3, 3, DockSelection.ORIGIN, false));
        assertEquals(Outcome.LAUNCHING, DockSelection.evaluateFree(0, 3, DockSelection.ORIGIN, true));
        assertEquals(Outcome.OUT_OF_RANGE, DockSelection.evaluateFree(-1, 3, DockSelection.ORIGIN, false));
    }

    @Test
    @DisplayName("sync: no ring position is ever sent (named or not); names and distances survive")
    void payload() {
        List<FreeDocks.Dock> seen = FreeDocks.visible(nodes(), stations(), FreeDocks.anchor(null), R);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FreeDockSyncPayload.write(buf, FreeDockSyncPayload.of(seen));
        FreeDockSyncPayload back = FreeDockSyncPayload.read(buf);
        assertEquals(3, back.rows().size());
        FreeDockSyncPayload.Row unnamed = back.rows().get(0);
        assertFalse(unnamed.named());
        assertEquals("", unnamed.name(), "an unnamed ring sends no label at all");
        assertEquals(seen.get(0).distance(), unnamed.distance());
        FreeDockSyncPayload.Row named = back.rows().get(1);
        assertTrue(named.named());
        assertEquals("Bob's Base", named.name());
        assertEquals(1800, named.distance());
        assertFalse(buf.isReadable());
        // Wire size proves no coordinates travel: count + (bool + distance) + (bool + name + distance) + ...
        FriendlyByteBuf one = new FriendlyByteBuf(Unpooled.buffer());
        FreeDockSyncPayload.write(one, new FreeDockSyncPayload(List.of(new FreeDockSyncPayload.Row("", false, 420))));
        assertEquals(1 + 1 + 2, one.readableBytes(), "an unnamed row is a flag and a distance, nothing else");
        for (java.lang.reflect.RecordComponent c : FreeDockSyncPayload.Row.class.getRecordComponents()) {
            assertFalse(java.util.Set.of("x", "y", "z", "pos", "centre").contains(c.getName()), "no position field: " + c.getName());
        }
    }

    @Test
    @DisplayName("naming payload: a 2.0 packet reads as auto slot + full structure; 2.1 flags round-trip")
    void namingPayload() {
        FriendlyByteBuf old = new FriendlyByteBuf(Unpooled.buffer());
        old.writeUtf("Legacy", 64); // the 2.0 wire format: the name only
        FoundStationPayload legacy = FoundStationPayload.read(old);
        assertEquals("Legacy", legacy.name());
        assertFalse(legacy.claimHere());
        assertTrue(legacy.buildStarter());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FoundStationPayload.write(buf, new FoundStationPayload("Here", true, false));
        FoundStationPayload back = FoundStationPayload.read(buf);
        assertEquals("Here", back.name());
        assertTrue(back.claimHere());
        assertFalse(back.buildStarter());
        assertFalse(buf.isReadable());
    }
}
