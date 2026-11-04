package za.co.neroland.nerospace.client.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The planet skies, particles and ambience are data: this pins that every file they name exists, that every sky
 * profile decodes through {@link SkyProfile#CODEC} (and that profiles written before the 2.0.0 fields still do), that
 * the dimension-type colour floor uses the formats the 26.x attribute codecs accept, and that the NeoForge 26.3
 * dimension-type overlay is exactly the shared file plus the planet skybox attribute
 * ({@code tools/gen_sky_overlays.py}).
 */
class SkyAssetsTest {

    private static final List<String> SKIES = List.of("luna", "mars", "cindara", "glacira", "greenxertz", "nyxara",
            "station");
    private static final List<String> PARTICLES = List.of("dust", "ash", "spore", "diamond_dust", "ember",
            "nebula_mote");
    /** Textures the 26.3 sky renderer binds itself (not named in a profile). */
    private static final List<String> RENDERER_TEXTURES = List.of("horizon_ramp", "stars", "aurora_0", "aurora_1");
    /** Entity skins and glow layers the renderers name. */
    private static final List<String> ENTITY_TEXTURES = List.of("moon_hopper", "dust_strider", "dusk_moth",
            "dusk_moth_glow", "salvage_drone", "salvage_drone_glow", "dust_devil", "alien_villager_cindara",
            "alien_villager_cindara_glow", "alien_villager_glacira", "alien_villager_glacira_glow",
            "alien_villager_duskborn", "alien_villager_duskborn_glow", "alien_villager_glow");
    /**
     * Vanilla sound events the ambience aliases, each checked present in the 26.1.2, 26.2 and 26.3 SoundEvents
     * registries. A new alias must be checked the same way and added here.
     */
    private static final Set<String> VANILLA_EVENTS = Set.of("ambient.soul_sand_valley.loop",
            "ambient.basalt_deltas.additions", "item.elytra.flying", "ambient.basalt_deltas.loop", "block.lava.ambient",
            "block.fire.ambient", "ambient.basalt_deltas.mood", "ambient.nether_wastes.loop",
            "block.amethyst_block.chime", "block.powder_snow.step", "block.amethyst_block.resonate",
            "ambient.crimson_forest.loop", "ambient.crimson_forest.additions", "block.big_dripleaf.tilt_down",
            "ambient.crimson_forest.mood", "ambient.warped_forest.loop", "ambient.warped_forest.additions",
            "ambient.warped_forest.mood", "block.beacon.ambient", "block.respawn_anchor.charge", "block.piston.extend",
            "block.iron_door.open");
    private static final Set<String> ARGB_ATTRIBUTES = Set.of("minecraft:visual/cloud_color",
            "minecraft:visual/sunrise_sunset_color");
    private static final Set<String> RGB_ATTRIBUTES = Set.of("minecraft:visual/sky_color",
            "minecraft:visual/fog_color", "minecraft:visual/ambient_light_color");

    private static Path repo() {
        for (Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("common/src/main/resources/assets/nerospace"))) {
                return p;
            }
        }
        return null;
    }

    private static Path assets() {
        Path r = repo();
        Assumptions.assumeTrue(r != null, "repo not found");
        return r.resolve("common/src/main/resources/assets/nerospace");
    }

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static SkyProfile decode(JsonElement json) {
        return SkyProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void horizonGradientsClampTheirOpaqueEdgeInsteadOfRepeatingTheTransparentEdge() throws IOException {
        for (String ramp : List.of("horizon_ramp", "horizon_ramp_soft")) {
            Path texture = assets().resolve("textures/sky/" + ramp + ".png");
            JsonObject sampling = read(Path.of(texture + ".mcmeta")).getAsJsonObject("texture");
            assertTrue(sampling.get("clamp").getAsBoolean(), ramp + " must not wrap at UV=1 below the horizon");
            assertTrue(sampling.get("blur").getAsBoolean(), ramp + " must interpolate its gradient");
            var image = ImageIO.read(texture.toFile());
            assertNotNull(image, ramp);
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(0, image.getRGB(x, 0) >>> 24, ramp + " transparent zenith edge");
                assertTrue((image.getRGB(x, image.getHeight() - 1) >>> 24) >= 250, ramp + " near-opaque lower sky");
            }
        }
    }

    @Test
    void planetDimensionCloudsAreTransparent() throws IOException {
        Path root = repo();
        Assumptions.assumeTrue(root != null, "repo not found");
        for (String dimension : List.of("space", "luna", "mars", "cindara", "glacira", "greenxertz", "nyxara")) {
            Path file = root.resolve("common/src/main/resources/data/nerospace/dimension_type/" + dimension + ".json");
            assertEquals("#00000000", read(file).getAsJsonObject("attributes")
                    .get("minecraft:visual/cloud_color").getAsString(), dimension);
        }
        Path overlay = root.resolve("common/src/main/resources-26.3/data/nerospace/dimension_type/greenxertz.json");
        assertEquals("#00000000", read(overlay).getAsJsonObject("attributes")
                .get("minecraft:visual/cloud_color").getAsString());
    }

    @Test
    @DisplayName("every sky profile decodes, and its textures exist")
    void skyProfiles() throws IOException {
        Path a = assets();
        for (String sky : SKIES) {
            SkyProfile p = decode(read(a.resolve("sky/" + sky + ".json")));
            assertFalse(p.vanillaSkybox(), sky + " still asks for the vanilla sky");
            assertTrue(p.gradient().size() >= 2, sky + " has no gradient");
            List<SkyProfile.Body> bodies = new ArrayList<>(p.sun());
            bodies.addAll(p.moons());
            bodies.addAll(p.bodies());
            assertFalse(p.sun().isEmpty(), sky + " has no sun");
            for (SkyProfile.Body b : bodies) {
                assertTrue(b.texture().isPresent(), sky + "/" + b.id() + " has no texture");
                assertTexture(a, b.texture().get().getPath(), sky + "/" + b.id());
            }
            p.galaxy().flatMap(SkyProfile.Galaxy::texture).ifPresent(t -> assertTexture(a, t.getPath(), sky + " galaxy"));
            for (String key : p.weatherTints().keySet()) {
                assertNotNull(za.co.neroland.nerospace.world.planet.WeatherEvent.valueOf(key), key);
            }
            // The scene must compute at every phase without throwing, with sane alphas.
            for (int i = 0; i <= 20; i++) {
                SkyScene s = SkyScene.compute(p, i / 20.0, i / 20.0, 0.5, "NONE", 0.0F, 0.0, 2048, i * 600.0);
                assertTrue(s.starAlpha >= 0.0F && s.starAlpha <= 1.0F, sky);
                assertEquals(bodies.size(), s.bodies.size(), sky);
            }
        }
        for (String t : RENDERER_TEXTURES) {
            assertTexture(a, "textures/sky/" + t + ".png", "renderer");
        }
    }

    @Test
    @DisplayName("profiles written before the 2.0.0 fields still decode")
    void oldProfilesDecode() {
        SkyProfile placeholder = decode(JsonParser.parseString("{\"vanillaSkybox\": true}"));
        assertTrue(placeholder.vanillaSkybox());
        assertTrue(placeholder.gradient().isEmpty());
        assertTrue(placeholder.positionBlend().isEmpty());
        assertTrue(placeholder.terminatorScroll().isEmpty());
        SkyProfile old = decode(JsonParser.parseString("{\"background\": {\"zenith\": \"#102030\", \"blend\": "
                + "\"constant\"}, \"stars\": {\"count\": 10}, \"sun\": [{\"id\": \"s\", \"size\": 3.0}]}"));
        assertEquals(0x102030, SkyScene.compute(old, 0.25, 0.25, 1.0, "NONE", 0.0F, 0.0, 2048, 0.0).zenith);
        assertEquals(SkyProfile.VANILLA, decode(JsonParser.parseString("{\"vanillaSkybox\": true, \"clouds\": "
                + "\"none\"}")));
    }

    @Test
    @DisplayName("body angles follow the contract: east at 0, high at 0.25, west at 0.5; fast moons reversed")
    void bodyAngles() {
        SkyProfile.Body sun = body(1.0F, 0.0F, 0.0F);
        float[] rise = SkyScene.bodyAngles(sun, 0.0);
        assertEquals(270.0F, rise[0], 0.5F);
        assertEquals(0.0F, rise[1], 0.5F);
        assertEquals(90.0F, SkyScene.bodyAngles(sun, 0.25)[1], 0.5F);
        float[] set = SkyScene.bodyAngles(sun, 0.5);
        assertEquals(90.0F, set[0], 0.5F);
        assertEquals(0.0F, set[1], 0.5F);
        // Phobos (0.32 d) rises in the west.
        assertEquals(90.0F, SkyScene.bodyAngles(body(0.32F, 0.0F, 0.0F), 0.0)[0], 0.5F);
        // Inclination: horizon crossing at the inclination, culmination beside the zenith (south for positive).
        SkyProfile.Body inclined = body(1.0F, 0.0F, 6.0F);
        assertEquals(6.0F, SkyScene.bodyAngles(inclined, 0.0)[1], 0.5F);
        float[] noon = SkyScene.bodyAngles(inclined, 0.25);
        assertEquals(84.0F, noon[1], 0.5F);
        float[] noonDir = SkyScene.direction(noon[0], noon[1]);
        assertTrue(noonDir[2] > 0.05F && Math.abs(noonDir[0]) < 1e-3F, "an inclined noon leans south");
        // Luna's Earth (offset 0.22) rises at clock phase 0.78.
        assertEquals(12.0F, SkyScene.bodyAngles(body(1.0F, 0.22F, 12.0F), 0.78)[1], 0.5F);
        // Fixed angle wins.
        SkyProfile.Body fixed = new SkyProfile.Body("x", java.util.Optional.empty(), 4.0F, "#FFFFFF", 0.0F, 0.0F,
                0.0F, 0.0F, java.util.Optional.of(new SkyProfile.FixedAngle(90.0F, 2.0F)), false);
        assertEquals(90.0F, SkyScene.bodyAngles(fixed, 0.6)[0], 1e-4F);
        assertEquals(2.0F, SkyScene.bodyAngles(fixed, 0.6)[1], 1e-4F);
        // Yaw convention: 90 = west (-X), 270 = east (+X).
        assertTrue(SkyScene.direction(90.0F, 0.0F)[0] < -0.99F);
        assertTrue(SkyScene.direction(270.0F, 0.0F)[0] > 0.99F);
    }

    @Test
    @DisplayName("no body jumps when the clock phase wraps at sunrise, and every orbiting body rises")
    void bodiesContinuousAcrossTheDayWrap() throws IOException {
        Path a = assets();
        for (String sky : SKIES) {
            SkyProfile p = decode(read(a.resolve("sky/" + sky + ".json")));
            List<SkyProfile.Body> bodies = new ArrayList<>(p.sun());
            bodies.addAll(p.moons());
            bodies.addAll(p.bodies());
            for (SkyProfile.Body b : bodies) {
                String where = sky + "/" + b.id();
                // Across each of the first 30 sunrises (phase 1 -> 0) the body moves less than 1 degree,
                // through the same path the renderer uses (wrapped phase for colours, continuous days for bodies).
                for (int day = 1; day <= 30; day++) {
                    double before = day - 1e-4;
                    double after = day + 1e-4;
                    SkyScene s0 = SkyScene.compute(p, before - Math.floor(before), before, 0.5, "NONE", 0.0F, 0.0,
                            2048, 0.0);
                    SkyScene s1 = SkyScene.compute(p, after - Math.floor(after), after, 0.5, "NONE", 0.0F, 0.0, 2048,
                            0.0);
                    SkyScene.BodyDraw d0 = find(s0, b.id());
                    SkyScene.BodyDraw d1 = find(s1, b.id());
                    assertTrue(separation(d0, d1) < 1.0, where + " jumps " + separation(d0, d1) + " deg at sunrise "
                            + day);
                }
                if (b.orbitPeriodDays() == 0.0F || b.fixedAngle().isPresent()) {
                    continue;
                }
                // Every orbiting body is above the horizon at some point within two of its own periods.
                double span = Math.max(2.0, 2.0 * b.orbitPeriodDays());
                boolean rises = false;
                for (double d = 0.0; d <= span && !rises; d += 0.005) {
                    rises = SkyScene.bodyAngles(b, d)[1] > 1.0F;
                }
                assertTrue(rises, where + " never rises");
            }
        }
        // Cindara: the dark moon meets the red giant every day at 0.45 and is below the west horizon at dawn.
        SkyProfile cindara = decode(read(a.resolve("sky/cindara.json")));
        SkyProfile.Body sun = cindara.sun().get(0);
        SkyProfile.Body moon = cindara.moons().get(0);
        for (int day = 0; day < 4; day++) {
            float[] s = SkyScene.bodyAngles(sun, day + 0.45);
            float[] m = SkyScene.bodyAngles(moon, day + 0.45);
            assertTrue(separation(s, m) < 0.5, "Cindara conjunction on day " + day);
            float[] dawn = SkyScene.bodyAngles(moon, day + 0.0);
            assertTrue(dawn[1] < -20.0F, "Cindara dark moon above the horizon at dawn " + day);
        }
    }

    private static SkyScene.BodyDraw find(SkyScene s, String id) {
        return s.bodies.stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    private static double separation(SkyScene.BodyDraw a, SkyScene.BodyDraw b) {
        return separation(new float[] {a.yaw(), a.pitch()}, new float[] {b.yaw(), b.pitch()});
    }

    private static double separation(float[] a, float[] b) {
        float[] u = SkyScene.direction(a[0], a[1]);
        float[] v = SkyScene.direction(b[0], b[1]);
        double dot = Math.max(-1.0, Math.min(1.0, u[0] * v[0] + u[1] * v[1] + u[2] * v[2]));
        return Math.toDegrees(Math.acos(dot));
    }

    private static SkyProfile.Body body(float period, float offset, float incl) {
        return new SkyProfile.Body("b", java.util.Optional.empty(), 5.0F, "#FFFFFF", 0.0F, period, offset, incl,
                java.util.Optional.empty(), false);
    }

    @Test
    @DisplayName("gradient keys wrap and position blend picks the side")
    void gradientAndBlend() throws IOException {
        List<SkyProfile.GradientKey> keys = List.of(new SkyProfile.GradientKey(0.25F, "#FF0000", "#FF0000", "#FF0000"),
                new SkyProfile.GradientKey(0.75F, "#0000FF", "#0000FF", "#0000FF"));
        assertEquals(0xFF0000, SkyScene.gradientAt(keys, 0.25)[0]);
        assertEquals(0x0000FF, SkyScene.gradientAt(keys, 0.75)[0]);
        // Wraps: 0.0 is halfway between 0.75 and 1.25.
        assertEquals(SkyScene.lerpRgb(0x0000FF, 0xFF0000, 0.5F), SkyScene.gradientAt(keys, 0.0)[0]);
        SkyProfile nyx = decode(read(assets().resolve("sky/nyxara.json")));
        SkyProfile.PositionBlend b = nyx.positionBlend().orElseThrow();
        assertEquals(SkyProfile.rgb(b.west().horizon()), SkyScene.compute(nyx, 0.0, 0.0, 0.6, "NONE", 0.0F, -5000.0, 2048,
                0.0).horizon);
        assertEquals(SkyProfile.rgb(b.band().horizon()), SkyScene.compute(nyx, 0.0, 0.0, 0.6, "NONE", 0.0F, 0.0, 2048,
                0.0).horizon);
        assertEquals(SkyProfile.rgb(b.east().horizon()), SkyScene.compute(nyx, 0.0, 0.0, 0.6, "NONE", 0.0F, 5000.0, 2048,
                0.0).horizon);
    }

    @Test
    @DisplayName("every ambient particle type has its definition and four frames")
    void particles() throws IOException {
        Path a = assets();
        for (String type : PARTICLES) {
            JsonObject def = read(a.resolve("particles/" + type + ".json"));
            assertEquals(4, def.getAsJsonArray("textures").size(), type);
            for (JsonElement t : def.getAsJsonArray("textures")) {
                String id = t.getAsString();
                assertTexture(a, "textures/particle/" + id.substring(id.indexOf(':') + 1) + ".png", type);
            }
        }
    }

    @Test
    @DisplayName("every entity skin and glow layer the renderers name exists")
    void entityTextures() {
        Path a = assets();
        for (String t : ENTITY_TEXTURES) {
            assertTexture(a, "textures/entity/" + t + ".png", t);
        }
    }

    @Test
    @DisplayName("ambient sounds: every event has a subtitle, aliases a checked vanilla event, and every reference exists")
    void sounds() throws IOException {
        Path a = assets();
        JsonObject sounds = read(a.resolve("sounds.json"));
        JsonObject en = read(a.resolve("lang/en_us.json"));
        int ambient = 0;
        for (Map.Entry<String, JsonElement> e : sounds.entrySet()) {
            JsonObject ev = e.getValue().getAsJsonObject();
            if (ev.has("subtitle")) {
                assertTrue(en.has(ev.get("subtitle").getAsString()), "no subtitle text for " + e.getKey());
            }
            if (!e.getKey().startsWith("ambient.")) {
                continue;
            }
            ambient++;
            for (JsonElement s : ev.getAsJsonArray("sounds")) {
                JsonObject o = s.getAsJsonObject();
                assertEquals("event", o.get("type").getAsString(), e.getKey());
                String name = o.get("name").getAsString().replace("minecraft:", "");
                assertTrue(VANILLA_EVENTS.contains(name), e.getKey() + " aliases an unchecked event " + name);
            }
        }
        assertEquals(22, ambient);
        // Every nerospace:* sound named by a dimension type or biome attribute exists in sounds.json.
        Path data = repo().resolve("common/src/main/resources/data/nerospace");
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(data.resolve("dimension_type"))) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(files::add);
        }
        try (Stream<Path> s = Files.walk(data.resolve("worldgen/biome"))) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(files::add);
        }
        for (Path f : files) {
            JsonObject attrs = read(f).getAsJsonObject("attributes");
            if (attrs != null && attrs.has("minecraft:visual/ambient_particles")) {
                // 26.x has no "append" modifier for this list: it must be a plain list of registered types.
                JsonElement list = attrs.get("minecraft:visual/ambient_particles");
                assertTrue(list.isJsonArray(), f.getFileName() + " ambient_particles must be a list");
                for (JsonElement e : list.getAsJsonArray()) {
                    String type = e.getAsJsonObject().getAsJsonObject("particle").get("type").getAsString();
                    assertTrue(!type.startsWith("nerospace:") || PARTICLES.contains(type.substring(10)),
                            f.getFileName() + " names " + type);
                }
            }
            for (String ref : soundRefs(read(f))) {
                assertTrue(sounds.has(ref.substring("nerospace:".length())), f.getFileName() + " names " + ref);
            }
        }
    }

    private static List<String> soundRefs(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && e.getAsString().startsWith("nerospace:ambient.")) {
            out.add(e.getAsString());
        } else if (e.isJsonObject()) {
            e.getAsJsonObject().entrySet().forEach(x -> out.addAll(soundRefs(x.getValue())));
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(x -> out.addAll(soundRefs(x)));
        }
        return out;
    }

    @Test
    @DisplayName("dimension-type colour floor uses the 26.x attribute formats, and the NeoForge 26.3 overlay is in sync")
    void dimensionTypes() throws IOException {
        Path r = repo();
        Assumptions.assumeTrue(r != null, "repo not found");
        Path base = r.resolve("common/src/main/resources/data/nerospace/dimension_type");
        Path shared263 = r.resolve("common/src/main/resources-26.3/data/nerospace/dimension_type");
        Path neo263 = r.resolve("neoforge/src/main/resources-26.3/data/nerospace/dimension_type");
        Gson gson = new GsonBuilder().create();
        List<Path> types = new ArrayList<>();
        try (Stream<Path> s = Files.list(base)) {
            s.filter(p -> p.toString().endsWith(".json")).sorted().forEach(types::add);
        }
        assertEquals(7, types.size());
        for (Path t : types) {
            for (Path f : new Path[] {t, shared263.resolve(t.getFileName())}) {
                if (!Files.isRegularFile(f)) {
                    continue;
                }
                JsonObject attrs = read(f).getAsJsonObject("attributes");
                assertNotNull(attrs, f + " has no attributes");
                for (Map.Entry<String, JsonElement> e : attrs.entrySet()) {
                    String k = e.getKey();
                    if (ARGB_ATTRIBUTES.contains(k)) {
                        assertTrue(e.getValue().getAsString().matches("#[0-9A-Fa-f]{8}"), f + " " + k);
                    } else if (RGB_ATTRIBUTES.contains(k)) {
                        assertTrue(e.getValue().getAsString().matches("#[0-9A-Fa-f]{6}"), f + " " + k);
                    }
                    // Fog distances are blocks; a fraction here would put a fog wall at the player's face.
                    assertFalse(k.endsWith("fog_start_distance") || k.endsWith("fog_end_distance"), f + " " + k);
                    if (k.equals("minecraft:visual/moon_phase")) {
                        assertTrue(e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString(), f + " " + k);
                    }
                }
            }
            Path src = Files.isRegularFile(shared263.resolve(t.getFileName())) ? shared263.resolve(t.getFileName()) : t;
            JsonObject expected = read(src);
            JsonObject attrs = expected.getAsJsonObject("attributes").deepCopy();
            attrs.addProperty("neoforge:custom_skybox", "nerospace:planet");
            expected.add("attributes", attrs);
            Path overlay = neo263.resolve(t.getFileName());
            assertTrue(Files.isRegularFile(overlay), "missing NeoForge 26.3 overlay " + overlay
                    + " (run tools/gen_sky_overlays.py)");
            assertEquals(canonical(gson, expected), canonical(gson, read(overlay)),
                    "stale NeoForge 26.3 overlay " + overlay + " (run tools/gen_sky_overlays.py)");
        }
        // The NeoForge-only attribute must never reach the shared resources.
        for (Path t : types) {
            assertFalse(read(t).getAsJsonObject("attributes").has("neoforge:custom_skybox"), t.toString());
        }
    }

    private static String canonical(Gson gson, JsonElement e) {
        return gson.toJson(sort(e));
    }

    private static JsonElement sort(JsonElement e) {
        if (e.isJsonObject()) {
            Map<String, JsonElement> m = new TreeMap<>();
            e.getAsJsonObject().entrySet().forEach(x -> m.put(x.getKey(), sort(x.getValue())));
            JsonObject o = new JsonObject();
            m.forEach(o::add);
            return o;
        }
        return e;
    }

    // ------------------------------------------------------------------------------------------------ sky upgrade

    private static SkyProfile profile(String sky) throws IOException {
        return decode(read(assets().resolve("sky/" + sky + ".json")));
    }

    private static za.co.neroland.nerospace.world.planet.PlanetDefinition planet(String langId) {
        return za.co.neroland.nerospace.world.planet.Planets.ALL.stream().filter(p -> p.langId().equals(langId))
                .findFirst().orElseThrow();
    }

    /** A scene for a planet (by lang id) at a clock phase, weather and position, like the gallery state shows it. */
    private static SkyScene sceneAt(String langId, double phase, String weather, float intensity, double x,
            SkyScene.Config config) throws IOException {
        za.co.neroland.nerospace.world.planet.PlanetDefinition p = planet(langId);
        SkyProfile profile = profile(p.traits().skyProfile().getPath());
        double days = 3.0 + phase;
        return SkyScene.compute(profile, new SkyScene.Inputs(phase, days, p.daylight().at(phase), weather, intensity,
                "NONE", 0.0F, x, 2048, 48_000.0, 24_000L, true, 7L, config));
    }

    private static boolean hasQuad(List<SkyScene.Quad> quads, String texture, float minAlpha) {
        return quads.stream().anyMatch(q -> q.texture().getPath().endsWith("/" + texture + ".png")
                && q.alpha() >= minAlpha);
    }

    @Test
    @DisplayName("sky upgrade: every texture a profile names exists, and the owner's star rulings hold")
    void upgradeTexturesExist() throws IOException {
        Path a = assets();
        for (String sky : SKIES) {
            SkyProfile p = profile(sky);
            List<String> where = new ArrayList<>();
            List<net.minecraft.resources.Identifier> tex = new ArrayList<>();
            p.background().rampTexture().ifPresent(tex::add);
            List<SkyProfile.Body> bodies = new ArrayList<>(p.sun());
            bodies.addAll(p.moons());
            bodies.addAll(p.bodies());
            for (SkyProfile.Body b : bodies) {
                b.glowTexture().ifPresent(tex::add);
                b.limbGlow().ifPresent(g -> tex.add(g.texture()));
                b.horizonGlow().ifPresent(g -> tex.add(g.texture()));
                b.nightShade().ifPresent(n -> {
                    tex.add(n.texture());
                    for (int f = 0; f < SkyScene.SHADE_FRAMES; f++) {
                        tex.add(SkyScene.shadeTexture(n.texture(), f));
                    }
                    n.lightsTexture().ifPresent(tex::add);
                });
            }
            p.aurora().ifPresent(au -> {
                tex.addAll(au.textures());
                au.fringe().ifPresent(f -> tex.add(f.texture()));
            });
            p.eclipse().ifPresent(e -> {
                e.corona().ifPresent(c -> tex.add(c.texture()));
                e.diamondRing().ifPresent(r -> tex.add(r.texture()));
            });
            p.decals().forEach(d -> tex.add(d.texture()));
            p.cloudLayer().ifPresent(c -> tex.add(c.texture()));
            p.shootingStars().ifPresent(s -> tex.add(s.texture()));
            for (net.minecraft.resources.Identifier t : tex) {
                assertTexture(a, t.getPath(), sky);
            }
            assertEquals(0.75F, p.stars().sizeScale(), 1e-6F, sky + " star size (owner ruling: 0.75 everywhere)");
            assertEquals("natural", p.stars().palette(), sky);
            for (SkyProfile.Decal d : p.decals()) {
                assertTrue(Set.of("fixed", "sun", "antisun", "body").contains(d.anchor()), sky + "/" + d.id());
                d.visibility().flatMap(SkyProfile.Visibility::weather).ifPresent(g ->
                        assertNotNull(za.co.neroland.nerospace.world.planet.WeatherEvent.valueOf(g.event()), g.event()));
            }
        }
        assertTexture(a, "textures/sky/stars_natural.png", "renderer");
        assertTexture(a, "textures/sky/horizon_ramp_soft.png", "renderer");
        // Kept for resource packs and the defaults (owner ruling).
        assertTexture(a, "textures/sky/earth.png", "kept");
        assertTexture(a, "textures/sky/earth_disk.png", "kept");
        // The station's homeworld sits east and below so the sun rises through its limb (owner ruling).
        SkyProfile.Body home = profile("station").bodies().get(0);
        assertEquals(270.0F, home.fixedAngle().orElseThrow().yaw(), 1e-6F);
        assertEquals(-50.0F, home.fixedAngle().orElseThrow().pitch(), 1e-6F);
        assertEquals(3.0F, profile("luna").shootingStars().orElseThrow().ratePerMinute(), 1e-6F);
        assertFalse(profile("luna").shootingStars().orElseThrow().nightOnly());
    }

    @Test
    @DisplayName("sky upgrade: a 2.0.0 profile decodes with every new field at its default, and profiles round-trip")
    void upgradeDefaultsAndRoundTrip() throws IOException {
        Path r = repo();
        Assumptions.assumeTrue(r != null);
        SkyProfile old = decode(read(r.resolve("common/src/test/resources/sky/glacira-2.0.0.json")));
        assertTrue(old.decals().isEmpty());
        assertTrue(old.cloudLayer().isEmpty());
        assertTrue(old.shootingStars().isEmpty());
        assertEquals(55.0F, old.background().rampTopDeg(), 1e-6F);
        assertTrue(old.background().rampTexture().isEmpty());
        assertEquals("white", old.stars().palette());
        assertEquals(1.0F, old.stars().sizeScale(), 1e-6F);
        assertEquals(0.0F, old.stars().twinkle(), 1e-6F);
        SkyProfile.Aurora au = old.aurora().orElseThrow();
        assertTrue(au.textures().isEmpty());
        assertEquals(2, au.layers());
        assertEquals(180.0F, au.yaw(), 1e-6F);
        assertTrue(au.fringe().isEmpty() && au.drift().isEmpty() && au.weather().isEmpty());
        for (SkyProfile.Body b : old.moons()) {
            assertTrue(b.nightShade().isEmpty() && b.limbGlow().isEmpty() && b.glowTexture().isEmpty());
            assertEquals(0.0F, b.earthshine(), 1e-6F);
        }
        // The old scene still computes: legacy aurora strips tinted by the colour cycle.
        SkyScene s = SkyScene.compute(old, 0.75, 3.75, 0.0, "NONE", 0.0F, 0.0, 2048, 0.0);
        assertEquals(2, s.auroraLayers.size());
        assertTrue(s.auroraLayers.get(0).texture().getPath().endsWith("aurora_0.png"));
        // Encode then decode gives the same profile (the paired map codec writes one flat object).
        for (String sky : SKIES) {
            SkyProfile p = profile(sky);
            JsonElement json = SkyProfile.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow();
            assertTrue(json.isJsonObject() && json.getAsJsonObject().has("background"), sky);
            assertEquals(p, decode(json), sky + " round trip");
        }
    }

    @Test
    @DisplayName("sky upgrade: the aurora runs on game time (regression guard): no drift or curtain jump, layers stay "
            + "apart, a clock set or freeze changes nothing, the curtains keep moving")
    void auroraRunsOnGameTime() throws IOException {
        double[] a = new double[4];
        double[] b = new double[4];
        for (String sky : SKIES) {
            SkyProfile p = profile(sky);
            if (p.aurora().isEmpty()) {
                continue;
            }
            SkyProfile.Aurora au = p.aurora().get();
            for (long dayLength : new long[] {24_000L, 24_600L, 48_000L, 72_000L, 672_000L}) {
                for (int n = 1; n <= 5; n++) {
                    double t0 = n * dayLength - 1e-3;
                    double t1 = n * dayLength + 1e-3;
                    assertEquals(SkyScene.auroraYaw(au, t0, dayLength), SkyScene.auroraYaw(au, t1, dayLength), 1e-3,
                            sky + " drift jumps at sunrise " + n);
                    for (int layer = 0; layer < 3; layer++) {
                        SkyScene.curtain(au, layer, 0.5, SkyScene.auroraSeconds(t0), a);
                        SkyScene.curtain(au, layer, 0.5, SkyScene.auroraSeconds(t1), b);
                        for (int k = 0; k < 4; k++) {
                            assertEquals(a[k], b[k], 1e-3, sky + " curtain " + k + " jumps at sunrise " + n);
                        }
                    }
                }
            }
            // A clock set (the day count jumps) or a freeze (frozen flag) at the same game time changes nothing.
            SkyScene before = SkyScene.compute(p, new SkyScene.Inputs(0.10, 4.10, 0.0, "NONE", 0.0F, "NONE", 0.0F, 5000.0,
                    2048, 123_456.0, 72_000L, false, 1L, SkyScene.Config.FULL));
            SkyScene after = SkyScene.compute(p, new SkyScene.Inputs(0.75, 9.75, 0.0, "NONE", 0.0F, "NONE", 0.0F, 5000.0,
                    2048, 123_456.0, 72_000L, true, 1L, SkyScene.Config.FULL));
            assertEquals(before.auroraSeconds, after.auroraSeconds, 1e-9, sky);
            assertEquals(before.auroraYaw, after.auroraYaw, 1e-6F, sky);
            // What can regress: the drift is continuous in game time ...
            if (au.drift().isPresent()) {
                for (long dayLength : new long[] {0L, 24_000L, 72_000L}) {
                    for (double t : new double[] {0.0, 12_345.6, 999_999.9, 5_000_000.0}) {
                        assertEquals(SkyScene.auroraYaw(au, t, dayLength), SkyScene.auroraYaw(au, t + 1e-3, dayLength),
                                1e-3, sky + " drift is not continuous at " + t);
                    }
                }
            }
            // ... and the per-layer phase offsets keep layers 0..2 apart at the same (un, t).
            double[][] layers = new double[3][4];
            for (int layer = 0; layer < 3; layer++) {
                SkyScene.curtain(au, layer, 0.3, 437.0, layers[layer]);
            }
            for (int i = 0; i < 3; i++) {
                for (int j = i + 1; j < 3; j++) {
                    boolean apart = false;
                    for (int k = 0; k < 4; k++) {
                        apart |= Math.abs(layers[i][k] - layers[j][k]) > 1e-3;
                    }
                    assertTrue(apart, sky + " aurora layers " + i + " and " + j + " collapsed onto each other");
                }
            }
            // And it moves with game time (the curtains animate, also on Nyxara's fixed clock and when frozen).
            SkyScene.curtain(au, 0, 0.5, SkyScene.auroraSeconds(0.0), a);
            SkyScene.curtain(au, 0, 0.5, SkyScene.auroraSeconds(2_000.0), b);
            assertTrue(Math.abs(a[3] - b[3]) > 1e-3 || Math.abs(a[0] - b[0]) > 1e-3, sky + " aurora never moves");
        }
    }

    @Test
    @DisplayName("sky upgrade: no glow, shade or decal jumps across sunrise")
    void quadsContinuousAcrossTheDayWrap() throws IOException {
        for (String sky : SKIES) {
            SkyProfile p = profile(sky);
            for (int day = 1; day <= 10; day++) {
                double before = day - 1e-4;
                double after = day + 1e-4;
                SkyScene s0 = SkyScene.compute(p, new SkyScene.Inputs(before - Math.floor(before), before, 0.5, "NONE",
                        0.0F, "NONE", 0.0F, 0.0, 2048, 1000.0, 24_000L, false, 1L, SkyScene.Config.FULL));
                SkyScene s1 = SkyScene.compute(p, new SkyScene.Inputs(after - Math.floor(after), after, 0.5, "NONE",
                        0.0F, "NONE", 0.0F, 0.0, 2048, 1000.0, 24_000L, false, 1L, SkyScene.Config.FULL));
                for (SkyScene.Quad q : s0.quads) {
                    boolean near = s1.quads.stream().anyMatch(o -> sameLayer(o.texture(), q.texture())
                            && separation(new float[] {o.yaw(), o.pitch()}, new float[] {q.yaw(), q.pitch()}) < 1.0
                            && Math.abs(o.alpha() - q.alpha()) < 0.05F);
                    assertTrue(near || q.alpha() < 0.05F, sky + " " + q.texture() + " jumps at sunrise " + day);
                }
            }
        }
    }

    @Test
    @DisplayName("sky upgrade: every gallery sky state shows its set piece")
    void galleryStatesShowTheirSetPieces() throws IOException {
        SkyScene.Config full = SkyScene.Config.FULL;
        // Luna Earthrise: Earth low in the east with its night shade and city lights.
        SkyScene earthrise = sceneAt("luna", state("luna", "earthrise"), "NONE", 0.0F, 0.0, full);
        SkyScene.BodyDraw earth = earthrise.bodies.stream().filter(b -> b.id().equals("earth")).findFirst().orElseThrow();
        assertTrue(earth.pitch() > 0.0F && earth.pitch() < 45.0F && SkyScene.direction(earth.yaw(), earth.pitch())[0] > 0.3F,
                "Earth rises in the east");
        // A gibbous Earth: a late phase-sheet frame, and its city lights faded by the lit fraction.
        assertTrue(shadeFrameOf(earthrise.quads) >= 9, "Earthrise shows a mostly lit Earth");
        assertTrue(hasQuad(earthrise.quads, "earth_lights", 0.0F));
        assertFalse(earthrise.shootingStars == null);
        // Mars blue sunset: a clearly blue aureole at full strength; much weaker at dusk.
        SkyScene blue = sceneAt("mars", state("mars", "blue_sunset"), "NONE", 0.0F, 0.0, full);
        SkyScene.Quad aureole = blue.quads.stream().filter(q -> q.texture().getPath().endsWith("sun_aureole.png")
                && q.size() > 30.0F).findFirst().orElseThrow();
        int r = (aureole.rgb() >> 16) & 0xFF;
        int bl = aureole.rgb() & 0xFF;
        assertTrue(bl > r + 120 && aureole.alpha() > 0.8F, "the Mars sunset aureole must read blue");
        int horizon = blue.horizon;
        assertTrue((horizon & 0xFF) > ((horizon >> 16) & 0xFF), "the blue-sunset horizon leans blue");
        SkyScene dusk = sceneAt("mars", state("mars", "dusk"), "NONE", 0.0F, 0.0, full);
        float duskGlow = (float) dusk.quads.stream().filter(q -> q.texture().getPath().endsWith("sun_aureole.png")
                && q.size() > 30.0F).mapToDouble(SkyScene.Quad::alpha).max().orElse(0.0);
        assertTrue(duskGlow < aureole.alpha() * 0.5F, "Mars dusk is not the blue sunset");
        // Mars dust storm hides the stars and bodies; its recovery shows a faint aurora at night.
        SkyScene storm = sceneAt("mars", state("mars", "dust_storm"), "DUST_STORM", 1.0F, 0.0, full);
        assertEquals(0.0F, storm.starAlpha, 1e-6F);
        SkyProfile mars = profile("mars");
        SkyScene recovering = SkyScene.compute(mars, new SkyScene.Inputs(0.75, 3.75, planet("mars").daylight().at(0.75),
                "NONE", 0.0F, "DUST_STORM", 1.0F, 0.0, 2048, 0.0, 24_600L, false, 1L, full));
        assertFalse(recovering.auroraLayers.isEmpty(), "Mars dust-storm recovery aurora");
        SkyScene calm = SkyScene.compute(mars, new SkyScene.Inputs(0.75, 3.75, planet("mars").daylight().at(0.75),
                "NONE", 0.0F, "NONE", 0.0F, 0.0, 2048, 0.0, 24_600L, false, 1L, full));
        assertTrue(calm.auroraLayers.isEmpty(), "no Mars aurora without a storm");
        // Cindara eclipse: corona; ash fall does not add a cloud deck.
        assertTrue(hasQuad(sceneAt("cindara", state("cindara", "eclipse"), "NONE", 0.0F, 0.0, full).quads, "corona",
                0.3F));
        SkyScene ash = sceneAt("cindara", state("cindara", "ash_fall"), "ASH_FALL", 1.0F, 0.0, full);
        assertTrue(ash.cloud.isEmpty());
        // Glacira: three curtains at night; the rose fringe joins them in diamond dust; the halo by day.
        assertEquals(3, sceneAt("glacira", state("glacira", "aurora"), "NONE", 0.0F, 0.0, full).auroraLayers.size());
        SkyScene dd = sceneAt("glacira", state("glacira", "diamond_dust"), "DIAMOND_DUST", 1.0F, 0.0, full);
        assertTrue(dd.auroraLayers.stream().anyMatch(l -> l.texture().getPath().endsWith("aurora_fringe_rose.png")));
        assertTrue(hasQuad(sceneAt("glacira", state("glacira", "noon"), "NONE", 0.0F, 0.0, full).quads, "halo_22",
                0.1F));
        // Greenxertz: the spore shimmer by day in a bloom; two suns at twin noon.
        assertFalse(sceneAt("greenxertz", state("greenxertz", "spore_bloom"), "SPORE_BLOOM", 1.0F, 0.0, full)
                .auroraLayers.isEmpty());
        SkyScene twin = sceneAt("greenxertz", state("greenxertz", "twin_noon"), "NONE", 0.0F, 0.0, full);
        assertTrue(twin.bodies.stream().filter(SkyScene.BodyDraw::emissive).allMatch(b -> b.pitch() > 45.0F));
        // Nyxara: flare ray fans in the band, the bow in the band, the aurora only on the nightside.
        SkyScene flare = sceneAt("nyxara", 0.6, "FLARE", 1.0F, 0.0, full);
        assertEquals(2, flare.quads.stream().filter(q -> q.texture().getPath().endsWith("flare_rays.png")).count());
        assertTrue(hasQuad(sceneAt("nyxara", 0.6, "NONE", 0.0F, 0.0, full).quads, "rainbow_arc", 0.2F));
        assertFalse(sceneAt("nyxara", 0.6, "NONE", 0.0F, 3000.0, full).auroraLayers.isEmpty(), "galaxy side aurora");
        assertTrue(sceneAt("nyxara", 0.6, "NONE", 0.0F, -3000.0, full).auroraLayers.isEmpty(), "no dayside aurora");
        // Station: the sun rises through the homeworld's limb; the night side shows city lights.
        SkyScene sunrise = sceneAt("station", state("station", "sunrise"), "NONE", 0.0F, 0.0, full);
        assertTrue(sunrise.quads.stream().anyMatch(q -> q.texture().getPath().endsWith("sun_aureole.png")
                && q.size() > 40.0F && q.alpha() > 0.5F), "limb sunrise aureole");
        SkyScene planetNight = sceneAt("station", state("station", "planet_night"), "NONE", 0.0F, 0.0, full);
        assertTrue(hasQuad(planetNight.quads, "earth_disk_lights", 0.3F));
        // The homeworld's near side is dark at night (frame <= 2) and at the limb sunrise (<= 3).
        assertTrue(shadeFrameOf(planetNight.quads) <= 2, "station planet_night frame " + shadeFrameOf(planetNight.quads));
        assertTrue(shadeFrameOf(sunrise.quads) <= 3, "station sunrise frame " + shadeFrameOf(sunrise.quads));
    }

    /**
     * Same texture, or neighbouring frames of the night-shade phase sheet (the frame steps by one when the lit
     * fraction crosses a rounding boundary; that is the sheet's resolution, not a jump).
     */
    private static boolean sameLayer(net.minecraft.resources.Identifier a, net.minecraft.resources.Identifier b) {
        if (a.equals(b)) {
            return true;
        }
        String pa = a.getPath();
        String pb = b.getPath();
        int ia = pa.lastIndexOf('_');
        int ib = pb.lastIndexOf('_');
        if (ia < 0 || ib < 0 || !pa.substring(0, ia).equals(pb.substring(0, ib)) || !pa.substring(0, ia)
                .endsWith("/terminator")) {
            return false;
        }
        int fa = Integer.parseInt(pa.substring(ia + 1, pa.length() - 4));
        int fb = Integer.parseInt(pb.substring(ib + 1, pb.length() - 4));
        return Math.abs(fa - fb) <= 1;
    }

    /** The night-shade phase-sheet frame drawn in {@code quads} ({@code terminator_NN}), or -1. */
    private static int shadeFrameOf(List<SkyScene.Quad> quads) {
        for (SkyScene.Quad q : quads) {
            String path = q.texture().getPath();
            int at = path.lastIndexOf("/terminator_");
            if (at >= 0 && path.endsWith(".png")) {
                return Integer.parseInt(path.substring(at + "/terminator_".length(), path.length() - 4));
            }
        }
        return -1;
    }

    private static double state(String planet, String name) {
        return za.co.neroland.nerospace.command.GallerySkyStates.find(planet, name).orElseThrow().phase();
    }

    @Test
    void mineralFilamentsAppearAtNightAndFadeInDaylight() throws IOException {
        for (String planet : List.of("mars", "glacira", "greenxertz")) {
            SkyScene night = sceneAt(planet, 0.75, "NONE", 0.0F, 0.0, SkyScene.Config.FULL);
            SkyScene day = sceneAt(planet, 0.25, "NONE", 0.0F, 0.0, SkyScene.Config.FULL);
            assertTrue(night.galaxyAlpha > 0.1F, planet);
            assertTrue(day.galaxyAlpha < night.galaxyAlpha * 0.25F, planet);
            assertTrue(night.fixedQuads.stream().anyMatch(q -> q.texture().getPath().contains("/dust_")), planet);
            assertFalse(day.fixedQuads.stream().anyMatch(q -> q.texture().getPath().contains("/dust_")
                    && q.alpha() > 0.03F), planet);
        }
    }

    @Test
    void dustStormOccludesTheGalaxyAndFilaments() throws IOException {
        SkyScene clear = sceneAt("mars", 0.75, "NONE", 0.0F, 0.0, SkyScene.Config.FULL);
        SkyScene storm = sceneAt("mars", 0.75, "DUST_STORM", 1.0F, 0.0, SkyScene.Config.FULL);
        assertTrue(clear.galaxyAlpha > 0.1F);
        assertEquals(0.0F, storm.galaxyAlpha, 1e-6F);
        double clearDust = clear.fixedQuads.stream().filter(q -> q.texture().getPath().contains("/dust_"))
                .mapToDouble(SkyScene.Quad::alpha).sum();
        double stormDust = storm.fixedQuads.stream().filter(q -> q.texture().getPath().contains("/dust_"))
                .mapToDouble(SkyScene.Quad::alpha).sum();
        assertTrue(clearDust > 0.1);
        assertTrue(stormDust < clearDust * 0.2);
    }

    @Test
    @DisplayName("sky upgrade: the client switches turn off aurora and shooting stars, lite trims the layers")
    void clientSwitches() throws IOException {
        SkyScene off = sceneAt("glacira", 0.75, "NONE", 0.0F, 0.0, new SkyScene.Config(false, false, false));
        assertTrue(off.auroraLayers.isEmpty());
        assertEquals(0.0F, off.auroraAlpha, 1e-6F);
        SkyScene lunaOff = sceneAt("luna", 0.75, "NONE", 0.0F, 0.0, new SkyScene.Config(true, false, false));
        assertTrue(lunaOff.shootingStars.isEmpty());
        SkyScene lite = sceneAt("glacira", 0.75, "NONE", 0.0F, 0.0, new SkyScene.Config(true, true, true));
        assertEquals(1, lite.auroraLayers.size());
        assertEquals(SkyScene.AURORA_SEGMENTS_LITE, lite.auroraSegments);
        assertEquals(0.75F * 0.85F, lite.starSizeScale, 1e-5F);
        assertTrue(sceneAt("cindara", 0.25, "NONE", 0.0F, 0.0, new SkyScene.Config(true, true, true)).cloud.isEmpty());
        assertTrue(sceneAt("cindara", 0.25, "NONE", 0.0F, 0.0, SkyScene.Config.FULL).cloud.isEmpty());
        assertTrue(za.co.neroland.nerospace.config.NerospaceConfig.isLite(" Lite "));
        assertFalse(za.co.neroland.nerospace.config.NerospaceConfig.isLite("full"));
        assertFalse(za.co.neroland.nerospace.config.NerospaceConfig.isLite("bogus"));
    }

    @Test
    @DisplayName("sky upgrade: shooting stars keep their rate and never exceed the concurrency cap")
    void shootingStarSchedule() throws IOException {
        SkyProfile.ShootingStars s = profile("luna").shootingStars().orElseThrow();
        int starts = 0;
        boolean wasVisible = false;
        int minutes = 20;
        for (int t = 0; t < minutes * 1200; t++) {
            List<SkyScene.Quad> out = new ArrayList<>();
            SkyScene.shootingStars(s, t, 42L, 0.0, out);
            assertTrue(out.size() <= s.maxConcurrent());
            boolean visible = !out.isEmpty();
            if (visible && !wasVisible) {
                starts++;
            }
            wasVisible = visible;
            for (SkyScene.Quad q : out) {
                assertTrue(q.pitch() > -15.0F && q.alpha() <= 1.0F);
                assertEquals(s.lengthDeg() / 8.0F, q.size(), 1e-5F);
            }
        }
        double perMinute = starts / (double) minutes;
        assertTrue(perMinute > s.ratePerMinute() * 0.5 && perMinute < s.ratePerMinute() * 1.5, "rate " + perMinute);
    }

    @Test
    @DisplayName("sky upgrade: the night shade picks its phase-sheet frame by lit fraction and turns away from the sun")
    void nightShade() throws IOException {
        float[] sun = SkyScene.direction(270.0F, 0.0F);
        // A body opposite the sun is full: the last (clear) frame.
        float[] full = SkyScene.shade(90.0F, 0.0F, sun);
        assertEquals(1.0F, full[1], 1e-4F);
        assertEquals(15, SkyScene.shadeFrame(full[1]));
        // A body beside the sun is new: the first (all dark) frame.
        float[] newMoon = SkyScene.shade(270.0F, 1.0F, sun);
        assertEquals(0.0F, newMoon[1], 1e-3F);
        assertEquals(0, SkyScene.shadeFrame(newMoon[1]));
        // Quarter: half lit, the middle of the sheet; local +x toward the sun.
        float[] quarter = SkyScene.shade(0.0F, 0.0F, sun);
        assertEquals(0.5F, quarter[1], 1e-4F);
        int mid = SkyScene.shadeFrame(quarter[1]);
        assertTrue(mid == 7 || mid == 8, "quarter frame " + mid);
        // The sheet frames exist and get darker as the lit fraction falls; the lit side faces local +x (the sun).
        net.minecraft.resources.Identifier base = net.minecraft.resources.Identifier.fromNamespaceAndPath("nerospace",
                "textures/sky/terminator.png");
        Path a = assets();
        int previous = -1;
        for (int f = SkyScene.SHADE_FRAMES - 1; f >= 0; f--) {
            net.minecraft.resources.Identifier frame = SkyScene.shadeTexture(base, f);
            assertTrue(frame.getPath().endsWith(String.format("terminator_%02d.png", f)), frame.toString());
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(a.resolve(frame.getPath()).toFile());
            assertNotNull(img, frame.toString());
            int dark = 0;
            for (int y = 0; y < img.getHeight(); y += 4) {
                for (int x = 0; x < img.getWidth(); x += 4) {
                    dark += (img.getRGB(x, y) >>> 24) > 128 ? 1 : 0;
                }
            }
            assertTrue(dark >= previous, "frame " + f + " is not darker than frame " + (f + 1));
            previous = dark;
        }
        java.awt.image.BufferedImage crescent = javax.imageio.ImageIO.read(a.resolve(SkyScene.shadeTexture(base, 2)
                .getPath()).toFile());
        int c = crescent.getHeight() / 2;
        assertTrue((crescent.getRGB(crescent.getWidth() - 8, c) >>> 24) < (crescent.getRGB(8, c) >>> 24),
                "the lit crescent is on the sun side (+x)");
        assertTrue((SkyScene.shadeTexture(base, 15).getPath()).endsWith("terminator_15.png"));
        float[][] axes = SkyScene.axes(0.0F, 0.0F);
        double r = Math.toRadians(quarter[0]);
        float[] toward = {(float) (axes[0][0] * Math.cos(r) + axes[1][0] * Math.sin(r)),
                (float) (axes[0][1] * Math.cos(r) + axes[1][1] * Math.sin(r)),
                (float) (axes[0][2] * Math.cos(r) + axes[1][2] * Math.sin(r))};
        assertTrue(toward[0] * sun[0] + toward[1] * sun[1] + toward[2] * sun[2] > 0.99F);
    }

    @Test
    @DisplayName("sky upgrade: the world fog pushed on every cell is the scene's fog colour")
    void fogColourFollowsScene() throws IOException {
        for (za.co.neroland.nerospace.world.planet.PlanetDefinition planet
                : za.co.neroland.nerospace.world.planet.Planets.ALL) {
            SkyProfile p = profile(planet.traits().skyProfile().getPath());
            for (double phase : new double[] {0.02, 0.25, 0.48, 0.75}) {
                PlanetSkyRenderer.Frame frame = new PlanetSkyRenderer.Frame(planet, p, phase, 2.0 + phase,
                        planet.daylight().at(phase), za.co.neroland.nerospace.world.planet.WeatherEvent.NONE, 0.0F,
                        2048, 24_000L, false, za.co.neroland.nerospace.world.planet.WeatherEvent.NONE, 0.0F,
                        SkyScene.Config.FULL);
                double time = 1000.0 + phase * 100.0;
                int fog = PlanetSkyRenderer.scene(frame, 0.0, time).fog;
                assertEquals(SkyScene.compute(p, PlanetSkyRenderer.inputs(frame, 0.0, time)).fog, fog,
                        planet.langId() + " at " + phase);
                assertEquals(SkyScene.compute(p, phase, 2.0 + phase, planet.daylight().at(phase), "NONE", 0.0F, 0.0,
                        2048, time).fog, fog, planet.langId() + " fog is the 2.0.0 scene fog");
            }
        }
    }

    private static void assertTexture(Path assets, String path, String what) {
        Path png = assets.resolve(path);
        assertTrue(Files.isRegularFile(png), what + ": missing " + png);
    }
}
