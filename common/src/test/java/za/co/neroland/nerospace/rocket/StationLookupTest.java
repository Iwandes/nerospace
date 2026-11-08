package za.co.neroland.nerospace.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D7 known defect: {@code entryAt}/{@code ownerAt} matched stations by X only, which breaks for player-chosen
 * centres. The two-axis lookup must find placed stations on both axes and answer exactly as 2.0.0 did for a registry
 * of grid-allocated stations.
 */
class StationLookupTest {

    static final String A = "4b2b9a4e-1c2d-4e5f-8a9b-0c1d2e3f4a5b";
    static final String B = "00000000-0000-4000-8000-0000000000b2";

    /** The 2.0.0 {@code entryAt} body, verbatim in behaviour: first station whose X band holds pos. */
    @Nullable
    static StationRegistry.StationEntry oldEntryAt(List<StationRegistry.StationEntry> all, BlockPos pos) {
        for (StationRegistry.StationEntry e : all) {
            if (Math.abs(e.center().getX() - pos.getX()) <= StationRegistry.SLOT_SPACING / 2) {
                return e;
            }
        }
        return null;
    }

    /** The 2.0.0 {@code ownerAt} body: first owned station whose X band holds pos. */
    @Nullable
    static String oldOwnerAt(List<StationRegistry.StationEntry> all, BlockPos pos) {
        for (StationRegistry.StationEntry e : all) {
            if (e.owner().isEmpty()) {
                continue;
            }
            if (Math.abs(e.center().getX() - pos.getX()) <= StationRegistry.SLOT_SPACING / 2) {
                return e.owner();
            }
        }
        return null;
    }

    static StationRegistry grid() {
        StationRegistry r = new StationRegistry();
        r.found("One", A);
        r.found("Two", null); // unowned (legacy / erased)
        r.found("Three", B);
        r.found("Four", A);
        return r;
    }

    @Test
    @DisplayName("grid-allocated stations: identical answers to 2.0.0 everywhere, both lookups")
    void gridParity() {
        List<StationRegistry.StationEntry> all = grid().all();
        int checked = 0;
        for (int x = -3000; x <= 24_000; x += 37) {
            for (int z : new int[] { -50_000, -2048, -255, 0, 256, 3000, 29_999 }) {
                BlockPos pos = new BlockPos(x, 64, z);
                assertSame(oldEntryAt(all, pos), StationRegistry.lookup(all, pos), pos.toString());
                assertEquals(oldOwnerAt(all, pos), StationRegistry.ownerLookup(all, pos), pos.toString());
                checked++;
            }
        }
        // The exact band boundaries (shared by two slots) keep the founding-order winner.
        for (int slot = 0; slot < 4; slot++) {
            BlockPos edge = StationRegistry.centerFor(slot).offset(StationRegistry.SLOT_SPACING / 2, 0, 0);
            assertSame(oldEntryAt(all, edge), StationRegistry.lookup(all, edge));
            assertEquals(oldOwnerAt(all, edge), StationRegistry.ownerLookup(all, edge));
        }
        assertTrue(checked > 5000);
    }

    @Test
    @DisplayName("a placed station is found on both axes, ahead of the grid band it sits in")
    void placedTwoAxis() {
        StationRegistry r = grid();
        StationRegistry.StationEntry placed = r.foundAt("Far Out", B, new BlockPos(5000, 120, 12_000));
        List<StationRegistry.StationEntry> all = r.all();
        BlockPos inside = new BlockPos(5040, 64, 11_960);
        assertSame(placed, StationRegistry.lookup(all, inside));
        assertEquals(B, StationRegistry.ownerLookup(all, inside));
        assertSame(placed, StationRegistry.lookup(all, new BlockPos(5000 + 256, 0, 12_000 - 256)));
        // Same X band, far away on Z: still the grid station of that band (the 2.0.0 answer).
        assertSame(r.get(0), StationRegistry.lookup(all, new BlockPos(5000, 64, 0)));
        assertEquals(A, StationRegistry.ownerLookup(all, new BlockPos(5000, 64, 0)));
    }

    @Test
    @DisplayName("placed stations outside every grid band: found only near their own centre")
    void placedOffGrid() {
        StationRegistry r = new StationRegistry();
        StationRegistry.StationEntry south = r.foundAt("South", A, new BlockPos(-8000, 64, -8000));
        StationRegistry.StationEntry north = r.foundAt("North", null, new BlockPos(-8000, 64, 8000));
        List<StationRegistry.StationEntry> all = r.all();
        assertSame(south, StationRegistry.lookup(all, new BlockPos(-8000, 64, -7800)));
        assertSame(north, StationRegistry.lookup(all, new BlockPos(-8100, 64, 8100)));
        assertNull(StationRegistry.ownerLookup(all, new BlockPos(-8100, 64, 8100)), "unowned station: no owner");
        assertNull(StationRegistry.lookup(all, new BlockPos(-8000, 64, 0)), "between them: nobody's");
        assertNull(StationRegistry.lookup(all, new BlockPos(-8000, 64, -7743)), "257 out: outside the square");
        assertTrue(StationRegistry.inClaimBox(south, new BlockPos(-8048, 70, -7952)));
    }

    @Test
    @DisplayName("overlapping squares (operator lowered minSpacing): the nearest centre wins")
    void nearestWins() {
        StationRegistry r = new StationRegistry();
        StationRegistry.StationEntry a = r.foundAt("A", A, new BlockPos(20_000, 64, 0));
        StationRegistry.StationEntry b = r.foundAt("B", B, new BlockPos(20_200, 64, 0));
        assertSame(a, StationRegistry.lookup(r.all(), new BlockPos(20_090, 64, 0)));
        assertSame(b, StationRegistry.lookup(r.all(), new BlockPos(20_110, 64, 0)));
    }

    @Test
    @DisplayName("entryAt and ownerAt delegate to the two-axis lookup")
    void delegates() throws Exception {
        String src = PodArrivalTest.source("StationRegistry.java");
        assertTrue(PodArrivalTest.body(src, "public StationEntry entryAt(").contains("lookup(this.stations.values(), pos)"));
        assertTrue(PodArrivalTest.body(src, "public java.util.UUID ownerAt(")
                .contains("ownerLookup(this.stations.values(), pos)"));
    }
}
