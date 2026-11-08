package za.co.neroland.nerospace.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerospace.rocket.CrewLanding;

/**
 * The 2.1 POPIA/GDPR review's regression tests: telemetry breadcrumbs carry ints and enum names only, the link
 * module never widens scope (no roster of pads, docks, rovers or crew), the crew rejoin map cannot leak across
 * single-player worlds, and the privacy export covers the new rover owner record.
 */
class Compliance21Test {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));

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

    /** Every {@code breadcrumb(...)} call in a source, whitespace-collapsed. */
    static List<String> breadcrumbs(String src) {
        List<String> out = new ArrayList<>();
        int at = 0;
        while ((at = src.indexOf("breadcrumb(", at)) >= 0) {
            int end = src.indexOf(");", at);
            out.add(String.join(" ", src.substring(at, end + 1).split("\\s+")));
            at = end;
        }
        return out;
    }

    /**
     * The only non-literal operands a 2.1-touched breadcrumb may concatenate: ints (tier, slot, crew size, charge %,
     * landing step, the 0/1 flag), enum names, and the destination's dimension name. Any new operand fails here and
     * has to be reviewed.
     */
    private static final Map<String, String> ALLOWED = Map.of(
            "getTier()", "2",
            "Destinations.name(targetKey)", "mars",
            "crew.size()", "4",
            "landingStep(decision)", "3",
            "entry.slot()", "7",
            "energyPercent()", "55",
            "rover.energyPercent()", "100",
            "verdict.outcome().name()", "TOO_CLOSE",
            "(placed ? 1 : 0)", "1");

    private static final Pattern UUID_LIKE =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /** Two or three signed integers in a row, as a coordinate pair or triple would render. */
    private static final Pattern POSITION_LIKE = Pattern.compile("-?\\d+\\s*[, ]\\s*-?\\d+(\\s*[, ]\\s*-?\\d+)?");
    private static final Pattern COORD_KEY = Pattern.compile("(?i)\\b(x|y|z|pos|position|centre|center|owner|player|"
            + "uuid|name|pilot)=");

    @Test
    @DisplayName("telemetry: every 2.1 breadcrumb is ints and enum names only - no UUID, name or position")
    void breadcrumbScrub() throws Exception {
        Map<String, Integer> expected = Map.of(
                "rocket/RocketEntity.java", 3,
                "entity/RoverEntity.java", 1,
                "item/RoverItem.java", 1,
                "item/StationCharterItem.java", 2);
        for (Map.Entry<String, Integer> file : expected.entrySet()) {
            List<String> calls = breadcrumbs(source(file.getKey()));
            assertEquals(file.getValue(), calls.size(), "new breadcrumb in " + file.getKey() + " needs review: " + calls);
            for (String call : calls) {
                String args = call.substring(call.indexOf('(') + 1, call.length() - 1);
                String message = args.substring(args.indexOf(',') + 1).trim();
                StringBuilder rendered = new StringBuilder();
                for (String operand : message.split(" \\+ ")) {
                    String op = operand.trim();
                    if (op.startsWith("\"") && op.endsWith("\"")) {
                        rendered.append(op, 1, op.length() - 1);
                    } else {
                        String sample = ALLOWED.get(op);
                        assertNotNull(sample, "breadcrumb operand not on the ints/enums allowlist: " + op + " in " + call);
                        rendered.append(sample);
                    }
                }
                String out = rendered.toString();
                assertFalse(UUID_LIKE.matcher(out).find(), "UUID in " + out);
                assertFalse(POSITION_LIKE.matcher(out).find(), "position-like numbers in " + out);
                assertFalse(COORD_KEY.matcher(out).find(), "identity/position key in " + out);
            }
        }
        // The crew threshold carries a head count under a fixed scope, never an identity.
        String worlds = source("progression/WorldsProgress.java");
        String crew = body(worlds, "public static void crewLaunch(int crew)");
        assertTrue(crew.contains("fire(ROCKET_CREW_LAUNCH, \"crew\", crew, 2L)"));
        assertFalse(crew.contains("UUID") || crew.contains("Player"));
    }

    @Test
    @DisplayName("link: rovers are owner-only inside the 128-block online scan; no pad, dock, rover or crew roster")
    void linkScope() throws Exception {
        String snap = source("link/NerospaceLinkSnapshots.java");
        String rockets = body(snap, "private static JsonObject rockets(MinecraftServer server, UUID playerId)");
        int guard = rockets.indexOf("if (player == null || !(player.level() instanceof ServerLevel level))");
        int scan = rockets.indexOf("level.getEntitiesOfClass(RoverEntity.class");
        assertTrue(guard > 0 && scan > guard, "rovers are scanned only for an online requester, in their own level");
        String roverScan = rockets.substring(scan, rockets.indexOf(";", scan));
        assertTrue(roverScan.contains("inflate(NerospaceLinkAccess.SCAN_RADIUS)"), "same 128-block scan as rockets");
        assertTrue(roverScan.contains("r -> r.isOwnedBy(playerId)"), "only the requester's own rovers");
        String rocketRow = body(snap, "private static JsonObject rocketRow(");
        assertTrue(rocketRow.contains("\"crew_count\", za.co.neroland.nerospace.rocket.Crew.count(rocket)"));
        // rocket.getUUID() is the entity's own id (world content); any other UUID or name would be a person's.
        assertEquals(1, rocketRow.split("getUUID\\(", -1).length - 1, "only the rocket's own entity id");
        assertTrue(rocketRow.contains("rocket.getUUID()"));
        for (String leak : new String[] { "getPassengers", "getFirstPassenger", "seated(", "pilot(", "getName",
                "getGameProfile" }) {
            assertFalse(rocketRow.contains(leak), "rocket row leaks crew identity: " + leak);
        }
        for (String file : new String[] { "link/NerospaceLinkSnapshots.java", "link/NerospaceLinkModule.java",
                "link/NerospaceLinkActions.java", "link/NerospaceLinkEvents.java", "link/NerospaceLinkAccess.java" }) {
            String src = source(file);
            assertFalse(src.contains("getNearestPlayer"), file + ": never authorise by nearest player");
            assertFalse(src.contains("FreeDocks") || src.contains("FreeDockSync"), file + ": no free-dock list");
            assertFalse(src.contains("CrewLanding"), file + ": no crew rejoin data");
            assertFalse(src.contains("countOwned") || src.contains("forgetOwnerEverywhere"), file + ": no rover roster");
        }
    }

    @Test
    @DisplayName("free docks: the client payload carries no ring position")
    void freeDocksSendNoPosition() throws Exception {
        String payload = source("network/FreeDockSyncPayload.java");
        String write = body(payload, "public static void write(");
        assertFalse(write.contains("writeInt") || write.contains("writeBlockPos") || write.contains("writeLong"),
                "no coordinates on the wire");
        String of = body(payload, "public static FreeDockSyncPayload of(");
        assertFalse(of.contains("centre()"), "the ring centre never reaches a row");
    }

    @Test
    @DisplayName("crew rejoin spots never cross into another single-player world")
    void crewLandingBoundToServer() {
        Object worldA = new Object();
        Object worldB = new Object();
        UUID p = UUID.fromString("00000000-0000-4000-8000-00000000c0de");
        CrewLanding.bindTo(worldA);
        CrewLanding.remember(p, OVERWORLD, new BlockPos(3, 70, 4));
        CrewLanding.bindTo(worldA);
        assertTrue(CrewLanding.holds(p), "same server keeps the entry");
        CrewLanding.bindTo(worldB);
        assertFalse(CrewLanding.holds(p), "a different world drops every entry");
        assertNull(CrewLanding.take(p));
        String src;
        try {
            src = source("rocket/CrewLanding.java");
            String join = body(src, "public static void onJoin(ServerPlayer player)");
            assertTrue(join.indexOf("bindTo(") < join.indexOf("take("), "bound before any entry is used");
            String rocket = source("rocket/RocketEntity.java");
            int remembers = rocket.split("CrewLanding\\.remember\\(", -1).length - 1;
            int binds = rocket.split("CrewLanding\\.bindTo\\(", -1).length - 1;
            assertEquals(remembers, binds, "every remember is preceded by a bind");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("privacy export lists the requester's own rovers; the collector filters by owner")
    void exportRovers() throws Exception {
        JsonArray rows = NerospacePrivacyExport.rovers(List.of(
                new NerospacePrivacyExport.Rover("nerospace:mars", new BlockPos(10, 70, -5), 80)));
        assertEquals(1, rows.size());
        JsonObject row = rows.get(0).getAsJsonObject();
        assertEquals("nerospace:mars", row.get("dimension").getAsString());
        assertEquals(80, row.get("charge_percent").getAsInt());
        assertEquals(Set.of("dimension", "pos", "charge_percent"), row.keySet(), "no rider or owner field");
        String collect = body(source("data/NerospacePrivacyExport.java"),
                "public static JsonObject collect(MinecraftServer server, ServerPlayer player)");
        assertTrue(collect.contains("r -> r.isOwnedBy(me)"), "only the requester's rovers");
        assertTrue(collect.contains("\"rovers_owned_loaded\""));
    }
}
