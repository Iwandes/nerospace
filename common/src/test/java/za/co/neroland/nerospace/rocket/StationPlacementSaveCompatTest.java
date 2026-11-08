package za.co.neroland.nerospace.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerospace.world.station.DockingPolicy;
import za.co.neroland.nerospace.world.station.ModuleEntry;
import za.co.neroland.nerospace.world.station.ModuleKind;

/**
 * A 2.0.0 {@code nerospace:stations} store loads unchanged under 2.1 (plain-JVM NBT round trip), the store shape gains
 * no key (so a 2.0.0 build can still read a 2.1 save with placed stations), the rocket's new keys are optional, and the
 * origin platform is never a listed station.
 */
class StationPlacementSaveCompatTest {

    /** Every key a 2.0.0 station entry may write. */
    static final Set<String> ENTRY_KEYS_200 = Set.of("slot", "name", "center", "owner", "class", "members", "modules",
            "founded_at", "class_changed_at", "docking");

    /** A 2.0.0-shaped store: three grid stations with modules, a member, a MEMBERS policy and a gap in the slots. */
    static StationRegistry store200() {
        StationRegistry r = new StationRegistry();
        StationRegistry.StationEntry home = r.found("Home", StationPlacementTest.OWNER);
        r.addModule(home.slot(), new ModuleEntry(ModuleKind.CORE, 0, home.center()));
        r.addModule(home.slot(), new ModuleEntry(ModuleKind.DOCKING_RING, 0, home.center().offset(13, 0, 0)));
        r.addMember(home.slot(), StationLookupTest.B);
        StationRegistry.StationEntry gone = r.found("Gone", null);
        r.unregister(gone.slot());
        StationRegistry.StationEntry club = r.found("Club", StationLookupTest.B);
        r.put(club.withDocking(DockingPolicy.MEMBERS));
        return r;
    }

    static Tag encode(StationRegistry r) {
        return StationRegistry.codec().encodeStart(NbtOps.INSTANCE, r).getOrThrow();
    }

    static StationRegistry decode(Tag tag) {
        return StationRegistry.codec().parse(new Dynamic<>(NbtOps.INSTANCE, tag)).getOrThrow();
    }

    @Test
    @DisplayName("a 2.0.0 station store loads unchanged and round-trips to the same NBT")
    void store200LoadsUnchanged() {
        Tag saved = encode(store200());
        StationRegistry loaded = decode(saved);
        assertEquals(saved, encode(loaded), "fixed point");
        assertEquals(2, loaded.count());
        for (StationRegistry.StationEntry e : loaded.all()) {
            assertTrue(StationRegistry.isGridAllocated(e), "2.0.0 stations stay on the grid");
            assertEquals(e, StationRegistry.lookup(loaded.all(), e.center()));
        }
        assertEquals(DockingPolicy.MEMBERS, loaded.get(2).docking());
        assertEquals(List.of(StationLookupTest.B), loaded.get(0).members());
        // The next charter used outside the station dimension takes the next grid slot exactly as 2.0.0 would.
        StationRegistry.StationEntry next = loaded.found(null, StationPlacementTest.OWNER);
        assertEquals(3, next.slot());
        assertEquals(StationRegistry.centerFor(3), next.center());
    }

    @Test
    @DisplayName("the store shape is unchanged: 2.0 and placed entries write only 2.0.0 keys")
    void noNewKeys() {
        StationRegistry r = store200();
        r.foundAt("Placed", StationPlacementTest.OWNER, new BlockPos(-9000, 130, 7000));
        CompoundTag root = (CompoundTag) encode(r);
        assertEquals(Set.of("stations", "next_slot"), TagReads.keys(root));
        ListTag stations = TagReads.listOrEmpty(root, "stations");
        assertEquals(3, stations.size());
        for (int i = 0; i < stations.size(); i++) {
            Set<String> keys = TagReads.keys(TagReads.compoundAt(stations, i));
            assertTrue(ENTRY_KEYS_200.containsAll(keys), "unexpected key in " + keys);
        }
        StationRegistry back = decode(root);
        assertEquals(new BlockPos(-9000, 130, 7000), back.get(3).center(), "the chosen centre is the saved centre");
        assertFalse(StationRegistry.isGridAllocated(back.get(3)));
    }

    @Test
    @DisplayName("rocket: FreeDock and LastStation are optional with 2.0-safe defaults")
    void rocketKeysOptional() throws Exception {
        String rocket = PodArrivalTest.source("RocketEntity.java");
        assertTrue(rocket.contains("this.freeDockId = input.getIntOr(\"FreeDock\", -1);"));
        assertTrue(rocket.contains("input.getBooleanOr(\"HasLastStation\", false)"));
        assertTrue(rocket.contains("output.putInt(\"FreeDock\", this.freeDockId);"));
        // A free dock leaves StationSlot on the origin, so a 2.0.0 build that drops FreeDock docks at the origin.
        String select = PodArrivalTest.body(rocket, "public DestinationSelection.Outcome selectStation(");
        assertTrue(select.contains("this.entityData.set(DATA_STATION, DockSelection.ORIGIN);"));
        CompoundTag rocket200 = RocketDockSaveCompatTest.rocket200(DockSelection.ORIGIN);
        assertEquals(-1, TagReads.intOr(rocket200, "FreeDock", -1));
        assertFalse(TagReads.booleanOr(rocket200, "HasLastStation", false));
    }

    @Test
    @DisplayName("the origin platform is never a listed station, keeps its guard and is labelled as shared")
    void originNeverListed() throws Exception {
        String api = mainSource("api/NerospaceStations.java");
        String all = PodArrivalTest.body(api, "public static List<StationInfo> all(MinecraftServer server)");
        assertTrue(all.contains("for (StationEntry entry : StationRegistry.get(server).all())"));
        assertFalse(all.contains("PLATFORM_Y") || all.contains("ORIGIN"), "no synthetic origin entry");
        StationRegistry r = store200();
        r.foundAt("Placed", StationPlacementTest.OWNER, new BlockPos(1024, 64, 1024));
        for (StationRegistry.StationEntry e : r.all()) {
            assertTrue(e.slot() >= 0, "the origin (slot -1) is never in the registry");
        }
        assertEquals(StationFounding.Placement.TOO_CLOSE, StationFounding.checkPlacement(new BlockPos(0, 64, 0),
                List.of(), 512, 30_000, false).outcome(), "no station can be founded on the origin");
        assertTrue(OriginPlatformGuard.alreadyBuilt(false, true, false));
        assertTrue(OriginPlatformGuard.alreadyBuilt(false, false, true));
        assertFalse(OriginPlatformGuard.alreadyBuilt(false, false, false));
        assertEquals("Shared platform (origin)", enUs().get("gui.nerospace.dock.origin").getAsString());
        assertFalse(PodArrivalTest.source("RocketMenu.java").contains("\"Origin Platform\""));
        assertFalse(mainSource("client/ClientStations.java").contains("\"Origin Platform\""));
    }

    static String mainSource(String rel) throws Exception {
        for (Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath(); p != null; p = p.getParent()) {
            Path c = p.resolve("common/src/main/java/za/co/neroland/nerospace").resolve(rel);
            if (Files.isRegularFile(c)) {
                return Files.readString(c).replace("\r\n", "\n");
            }
        }
        throw new IllegalStateException("source not found: " + rel);
    }

    static JsonObject enUs() throws Exception {
        try (InputStream in = StationPlacementSaveCompatTest.class
                .getResourceAsStream("/assets/nerospace/lang/en_us.json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
