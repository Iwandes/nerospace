package za.co.neroland.nerospace.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerospace.rocket.DockSelection;
import za.co.neroland.nerospace.rocket.StationRegistry.StationEntry;

/**
 * The 2.1 station list carries only the viewer's own relation per row (no identities, no positions), and is re-sent
 * on entering the station dimension.
 */
class StationSyncRelationTest {

    private static final String ME = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER = "00000000-0000-0000-0000-000000000009";

    @Test
    void relationsAreTheViewersOwn() {
        StationEntry mine = new StationEntry(0, "Home", new BlockPos(4096, 64, 0), ME);
        StationEntry theirs = new StationEntry(1, "Club", new BlockPos(8192, 64, 0), OTHER).withMembers(List.of(ME));
        StationEntry open = new StationEntry(2, "Port", new BlockPos(12288, 64, 0), OTHER);
        StationSyncPayload p = StationSyncPayload.of(List.of(mine, theirs, open), e -> true,
                e -> DockSelection.relation(e, ME));
        assertArrayEquals(new int[] { 0, 1, 2 }, p.slots());
        assertEquals(DockSelection.Relation.YOURS, p.relation(0));
        assertEquals(DockSelection.Relation.MEMBER, p.relation(1));
        assertEquals(DockSelection.Relation.PUBLIC, p.relation(2));
        assertEquals(DockSelection.Relation.PUBLIC, p.relation(9));
        // The 2.0 factory still works and says nothing about relations.
        StationSyncPayload legacy = StationSyncPayload.of(List.of(mine), e -> true);
        assertEquals(DockSelection.Relation.PUBLIC, legacy.relation(0));
        // Only slot, name and a relation byte per row: nothing else is in the record.
        assertEquals(3, StationSyncPayload.class.getRecordComponents().length);
    }

    @Test
    void reSentOnEnteringTheStation() {
        String station = "nerospace:station";
        String home = "minecraft:overworld";
        assertTrue(StationListSync.entered(home, station, station));
        assertTrue(StationListSync.entered(null, station, station), "login straight into it");
        assertFalse(StationListSync.entered(station, station, station), "only on the way in");
        assertFalse(StationListSync.entered(station, home, station));
    }
}
