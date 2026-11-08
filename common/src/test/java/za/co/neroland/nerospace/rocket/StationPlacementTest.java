package za.co.neroland.nerospace.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;
import za.co.neroland.nerospace.config.NerospaceConfig;
import za.co.neroland.nerospace.rocket.StationFounding.Placement;

/**
 * D6/D7: a charter used in the station dimension founds where the player stands, subject to the placement rule
 * (spacing from every station and the origin, the radius, the Y clamp); refusals keep the charter; the 4 096-grid
 * auto-allocation is unchanged for everyone else.
 */
class StationPlacementTest {

    static final String OWNER = "4b2b9a4e-1c2d-4e5f-8a9b-0c1d2e3f4a5b";
    static final int SPACING = 512;
    static final int RADIUS = 30_000;

    static StationRegistry withHome() {
        StationRegistry r = new StationRegistry();
        r.found("Home", OWNER); // grid slot 0 at (4096, 64, 0)
        return r;
    }

    static StationFounding.PlaceResult check(StationRegistry r, int x, int y, int z) {
        return StationFounding.checkPlacement(new BlockPos(x, y, z), r.all(), SPACING, RADIUS, r.isFull());
    }

    @Test
    @DisplayName("spacing: closer than minSpacing to a station is refused and names it; at minSpacing it is allowed")
    void spacingRefusal() {
        StationRegistry r = withHome();
        StationFounding.PlaceResult near = check(r, 4096 + 511, 80, 0);
        assertEquals(Placement.TOO_CLOSE, near.outcome());
        assertNotNull(near.blocker());
        assertEquals("Home", near.blocker().name());
        assertFalse(near.nearOrigin());
        assertEquals(Placement.TOO_CLOSE, check(r, 4096, 80, 511).outcome(), "Z counts too (two axes)");
        assertEquals(Placement.TOO_CLOSE, check(r, 4096 - 300, 200, -300).outcome());
        assertEquals(Placement.OK, check(r, 4096 + 512, 80, 0).outcome());
        assertEquals(Placement.OK, check(r, 4096, 80, -512).outcome());
    }

    @Test
    @DisplayName("spacing: the nearest of several blockers is the one named")
    void nearestBlocker() {
        StationRegistry r = withHome();
        StationRegistry.StationEntry placed = r.foundAt("Outpost", OWNER, new BlockPos(4096 + 600, 64, 0));
        assertNotNull(placed);
        StationFounding.PlaceResult res = check(r, 4096 + 500, 64, 0);
        assertEquals(Placement.TOO_CLOSE, res.outcome());
        assertSame(placed, res.blocker());
    }

    @Test
    @DisplayName("spacing: the origin platform counts as a station centre")
    void originSpacing() {
        StationRegistry r = new StationRegistry();
        StationFounding.PlaceResult res = check(r, 300, 64, -300);
        assertEquals(Placement.TOO_CLOSE, res.outcome());
        assertTrue(res.nearOrigin());
        assertNull(res.blocker());
        assertEquals(Placement.TOO_CLOSE, check(r, -511, 64, 0).outcome());
        assertEquals(Placement.OK, check(r, -512, 64, 0).outcome());
    }

    @Test
    @DisplayName("radius: outside ±maxRadius on either axis is too far; the edge is allowed")
    void radiusRefusal() {
        StationRegistry r = new StationRegistry();
        assertEquals(Placement.TOO_FAR, check(r, RADIUS + 1, 64, 0).outcome());
        assertEquals(Placement.TOO_FAR, check(r, 0, 64, -RADIUS - 1).outcome());
        assertEquals(Placement.TOO_FAR, check(r, -40_000, 64, 40_000).outcome());
        assertEquals(Placement.OK, check(r, RADIUS, 64, -RADIUS).outcome());
    }

    @Test
    @DisplayName("registry full is refused before anything else")
    void registryFull() {
        StationRegistry r = new StationRegistry();
        for (int i = 0; i < StationRegistry.MAX_STATIONS; i++) {
            assertNotNull(r.found(null, OWNER));
        }
        assertTrue(r.isFull());
        assertEquals(Placement.REGISTRY_FULL, check(r, 20_000, 64, 20_000).outcome());
        assertNull(r.foundAt("x", OWNER, new BlockPos(20_000, 64, 20_000)));
    }

    @Test
    @DisplayName("Y: the floor is the block under the feet, clamped to [64, 256]; X and Z are kept")
    void yClamp() {
        assertEquals(new BlockPos(10, 64, -10), StationFounding.snap(new BlockPos(10, 0, -10)));
        assertEquals(new BlockPos(10, 64, -10), StationFounding.snap(new BlockPos(10, 65, -10)));
        assertEquals(new BlockPos(-7, 99, 3), StationFounding.snap(new BlockPos(-7, 100, 3)));
        assertEquals(new BlockPos(10, 256, 10), StationFounding.snap(new BlockPos(10, 300, 10)));
        assertEquals(new BlockPos(10, 256, 10), StationFounding.snap(new BlockPos(10, 257, 10)));
        assertEquals(new BlockPos(10, 64, 10), StationFounding.snap(new BlockPos(10, -64, 10)));
    }

    @Test
    @DisplayName("the charter is consumed only on an accepted placement")
    void charterNotConsumedOnRefusal() throws Exception {
        for (Placement p : Placement.values()) {
            assertEquals(p == Placement.OK, StationFounding.consumesCharter(p), p.name());
        }
        String body = PodArrivalTest.body(itemSource(), "public static void foundFromUi(ServerPlayer player, String name, "
                + "boolean claimHere, boolean buildStarter)");
        int check = body.indexOf("StationFounding.consumesCharter(verdict.outcome())");
        int refusedReturn = body.indexOf("return; // refused: the charter stays in hand");
        int shrink = body.indexOf("held.shrink(1);");
        assertTrue(check > 0 && refusedReturn > check && shrink > refusedReturn,
                "the placement verdict must return before the charter is shrunk");
        assertEquals(1, body.split("held\\.shrink\\(", -1).length - 1, "one shrink, after every refusal");
        assertTrue(body.contains("StationFounding.snap(player.blockPosition())"), "position comes from the server player");
    }

    @Test
    @DisplayName("placed founding keeps the centre; grid auto-allocation is unchanged, skipping only a blocked slot")
    void gridAllocationUnchanged() {
        StationRegistry plain = new StationRegistry();
        for (int i = 0; i < 6; i++) {
            StationRegistry.StationEntry e = plain.found(null, OWNER);
            assertEquals(StationRegistry.centerFor(i), e.center(), "no placed stations: the 2.0 grid");
            assertEquals(i, e.slot());
            assertTrue(StationRegistry.isGridAllocated(e));
        }
        StationRegistry r = withHome();
        StationRegistry.StationEntry placed = r.foundAt("Mine", OWNER, StationRegistry.centerFor(2).offset(100, 0, 0));
        assertEquals(1, placed.slot());
        assertEquals(StationRegistry.centerFor(2).offset(100, 0, 0), placed.center());
        assertFalse(StationRegistry.isGridAllocated(placed));
        StationRegistry.StationEntry next = r.found(null, OWNER);
        assertEquals(3, next.slot(), "grid slot 2 would sit 100 blocks from Mine: its id is skipped");
        assertEquals(StationRegistry.centerFor(3), next.center());
        StationRegistry.StationEntry after = r.found(null, OWNER);
        assertEquals(4, after.slot());
        assertEquals(StationRegistry.centerFor(4), after.center());
        // A placed station right on a future grid centre makes auto-allocation skip that slot id.
        StationRegistry s = new StationRegistry();
        s.foundAt("Squatter", OWNER, StationRegistry.centerFor(1).offset(0, 0, 200));
        StationRegistry.StationEntry auto = s.found(null, OWNER);
        assertEquals(2, auto.slot());
        assertEquals(StationRegistry.centerFor(2), auto.center());
        for (StationRegistry.StationEntry e : s.all()) {
            for (StationRegistry.StationEntry o : s.all()) {
                if (e != o) {
                    assertTrue(StationFounding.chebyshev(e.center(), o.center()) >= StationRegistry.GRID_CLEARANCE);
                }
            }
        }
    }

    @Test
    @DisplayName("frame-only stations are never stamped with the starter room; every built station still is")
    void frameOnly() {
        assertTrue(StationStructure.frameOnly(true, false, true, false), "ring recorded, no core: frame only");
        assertFalse(StationStructure.frameOnly(true, false, false, false), "nothing recorded yet: build");
        assertFalse(StationStructure.frameOnly(true, true, true, true), "2.0 station");
        assertFalse(StationStructure.frameOnly(true, true, false, false), "1.x station (Core block, no modules)");
        assertFalse(StationStructure.frameOnly(false, false, true, false), "origin platform");
    }

    @Test
    @DisplayName("the three new keys exist with their defaults and are server-authoritative")
    void configKeys() {
        ConfigSchema schema = NerospaceConfig.schema();
        String[][] keys = { { "stations.minSpacing", "512" }, { "stations.maxRadius", "30000" },
                { "stations.freeDockRadius", "2048" } };
        for (String[] k : keys) {
            ConfigValue<?> v = schema.value(k[0]);
            assertNotNull(v, k[0]);
            assertEquals(k[1], v.defaultAsString(), k[0]);
            assertTrue(v.serverAuthoritative(), k[0]);
        }
    }

    static String itemSource() throws Exception {
        for (Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath(); p != null; p = p.getParent()) {
            Path c = p.resolve("common/src/main/java/za/co/neroland/nerospace/item/StationCharterItem.java");
            if (Files.isRegularFile(c)) {
                return Files.readString(c).replace("\r\n", "\n");
            }
        }
        throw new IllegalStateException("StationCharterItem.java not found");
    }
}
