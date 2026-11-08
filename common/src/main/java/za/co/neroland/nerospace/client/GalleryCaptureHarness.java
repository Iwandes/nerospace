package za.co.neroland.nerospace.client;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * CLIENT, creative-debug: an automated, fully-reproducible screenshot pass over the
 * {@code /nerospace gallery} scene (the §9.4 shot list — see RELEASE_CHECKLIST.md).
 * {@code /nerospace gallery} builds the scene server-side; this harness then drives the camera, hides
 * every overlay and writes a PNG per shot via {@link Screenshot#grab}, so the gallery can be
 * re-rendered identically after texture/model changes instead of hand-framing every shot.
 *
 * <p><b>Cross-loader.</b> This is loader-agnostic: it only touches vanilla client classes. Each loader
 * wires it from its own client setup — {@link #registerClientCommands(CommandDispatcher)} from the
 * loader's client-command hook (NeoForge/Forge {@code RegisterClientCommandsEvent}, Fabric
 * {@code ClientCommandRegistrationCallback}) and {@link #tick()} from the loader's per-client-tick hook
 * (next to {@code MeteorTrackerHud.tick()}). The command bodies never read the command source, so the
 * registration is generic in the brigadier source type {@code <S>} and the same call serves all three
 * loaders.
 *
 * <p><b>Commands</b> (client-side — they never reach the server dispatcher, which owns
 * {@code /nerospace gallery|station}). The root is {@code /nsgallery}, NOT {@code /nerospace}: a
 * client-dispatcher root that shares its name with a server command SHADOWS the server subtree — the
 * client parser matches the root, fails on the server-only child ("Incorrect argument for command")
 * and never forwards to the server. Keep the roots distinct.
 * <ul>
 *   <li>{@code /nsgallery capture [time]} — the gallery pass: teleports into the flat
 *       {@code nerospace:capture} dimension at a fixed origin (identical backdrop every run),
 *       rebuilds the gallery there from scratch ({@code gallery clear} then {@code gallery} — so a
 *       rerun never leaves lingering blocks/entities or stale machine progress), freezes
 *       time/weather/clouds, then shoots the reframed multi-angle shot list.</li>
 *   <li>{@code /nsgallery capture planets [time]} — the dimension vistas, each staged as a SELF-CONTAINED
 *       controlled scene (a themed floating platform + frozen signature mobs built fresh at a fixed
 *       origin in the real dimension), so they're 100% reproducible in ANY world — no fixed seed, no
 *       terrain tuning. Each scene clears its own footprint before rebuilding.</li>
 *   <li>{@code /nsgallery capture all [time]} — gallery pass then planet pass in one run.</li>
 *   <li>{@code /nsgallery capture shot <name>} — capture the CURRENT view once (overlays hidden) to
 *       {@code nerospace/<name>.png}. Use this to grab a hand-framed money shot.</li>
 * </ul>
 *
 * <p>{@code time} accepts {@code day}/{@code noon}/{@code night}/{@code midnight} or a tick number; it
 * only applies in dimensions with a clock (the capture dim + Greenxertz). The space dimensions
 * (Cindara/Glacira/Station) render against their starfield by design.
 *
 * <p>Outputs land in {@code .minecraft/screenshots/nerospace/<shot>.png} at the game window's native
 * resolution with the HUD, clouds and view-bob all suppressed for the duration of the run. Since native
 * res can be 2.5k+ wide / 5-8 MB, run {@code ./gradlew compressScreenshots} afterwards to cap each shot
 * at 1920 px / &lt;4 MB before committing (see RELEASE_CHECKLIST.md §9.4).
 */
public final class GalleryCaptureHarness {

    /** Ticks to let chunks/entities settle and a fresh frame render before grabbing. */
    private static final int SETTLE_TICKS = 12;

    /** Ticks to wait after teleporting + triggering the build for the scene to load and sync. */
    private static final int BUILD_WARMUP_TICKS = 120;

    /** Ticks to wait after teleporting into a planet dimension (cross-dim load + chunk gen). */
    private static final int PLANET_WARMUP_TICKS = 100;

    /**
     * Ticks to wait AFTER the build/kill commands before the cleanup pass. Lets entity-kill death
     * animations (smoke puffs) finish and any dropped items settle onto the ground, so the cleanup
     * sweep actually catches them and they're gone well before the shot.
     */
    private static final int CLEANUP_TICKS = 60;

    /** Deterministic flat backdrop (data/nerospace/dimension/capture.json) + the fixed build origin. */
    private static final String CAPTURE_DIMENSION = "nerospace:capture";
    private static final int ORIGIN_X = 0;
    private static final int ORIGIN_Y = 64;
    private static final int ORIGIN_Z = 0;

    /**
     * One framed capture. {@code setup} = server commands run pre-warmup (teleport into the dimension,
     * pin time/weather); {@code build} = server commands run AFTER warmup, once chunks are loaded
     * (clear, place blocks, kill old entities, summon); {@code cleanup} = server commands run after a
     * further {@link #CLEANUP_TICKS} pause (sweep up dropped items / dead-entity litter so the ground is
     * clean before the shot); {@code warmup} = ticks to wait after setup for the scene to load/sync;
     * {@code camera}/{@code target} = the pose ({@code null} poses → "keep current view").
     */
    private record Shot(String name, List<String> setup, List<String> build, List<String> cleanup,
            int warmup, Vec3 camera, Vec3 target) {
    }

    private enum Phase { MOVE, WARMUP, CLEANUP, SETTLE, SHOOT }

    private static final Deque<Shot> QUEUE = new ArrayDeque<>();
    private static boolean running;
    private static boolean hudWasHidden;
    private static boolean bobViewWas;
    private static boolean flyingWas;
    private static CloudStatus cloudsWere;
    private static Phase phase = Phase.MOVE;
    private static int warmup;
    private static int cleanupWait;
    private static int settle;
    private static Shot current;

    // The HUD-hide flag and the main render target both DIVERGE across 26.1.2 ↔ 26.2, so they're
    // resolved reflectively (no compile-time reference to version-specific symbols), the same approach
    // OxygenHud uses. 26.1.x: Options#hideGui (boolean) + Minecraft#getMainRenderTarget(). 26.2:
    // Gui#hud → Hud#isHidden()/toggle() + GameRenderer#mainRenderTarget().
    private static boolean hudResolved;
    private static java.lang.reflect.Field optHideGuiField;     // 26.1.x
    private static java.lang.reflect.Field guiHudField;         // 26.2
    private static java.lang.reflect.Method hudIsHiddenMethod;  // 26.2
    private static java.lang.reflect.Method hudToggleMethod;    // 26.2
    private static boolean rtResolved;
    private static java.lang.reflect.Method mcGetRenderTarget;  // 26.1.x
    private static java.lang.reflect.Method grMainRenderTarget; // 26.2

    private GalleryCaptureHarness() {
    }

    // ------------------------------------------------------------------------------------------------
    // Command registration (generic in the brigadier source type — bodies never read the source).
    // ------------------------------------------------------------------------------------------------

    /**
     * Register the client-side {@code /nsgallery capture …} tree on the given dispatcher. Generic in
     * the source type {@code S} so the SAME method serves NeoForge/Forge ({@code CommandSourceStack})
     * and Fabric ({@code FabricClientCommandSource}); the command bodies drive the local client only.
     * The root must NOT be {@code nerospace} — see the class javadoc (it would shadow the server tree).
     */
    public static <S> void registerClientCommands(CommandDispatcher<S> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("nsgallery")
                        .then(LiteralArgumentBuilder.<S>literal("capture")
                                .executes(ctx -> startCapture("noon"))
                                .then(LiteralArgumentBuilder.<S>literal("planets")
                                        .executes(ctx -> startPlanets("noon"))
                                        .then(RequiredArgumentBuilder.<S, String>argument("time", StringArgumentType.word())
                                                .executes(ctx -> startPlanets(StringArgumentType.getString(ctx, "time")))))
                                .then(LiteralArgumentBuilder.<S>literal("all")
                                        .executes(ctx -> startAll("noon"))
                                        .then(RequiredArgumentBuilder.<S, String>argument("time", StringArgumentType.word())
                                                .executes(ctx -> startAll(StringArgumentType.getString(ctx, "time")))))
                                .then(LiteralArgumentBuilder.<S>literal("shot")
                                        .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                                                .executes(ctx -> shotHere(StringArgumentType.getString(ctx, "name")))))
                                .then(RequiredArgumentBuilder.<S, String>argument("time", StringArgumentType.word())
                                        .executes(ctx -> startCapture(StringArgumentType.getString(ctx, "time"))))));
    }

    // ------------------------------------------------------------------------------------------------
    // Run starters.
    // ------------------------------------------------------------------------------------------------

    private static int startCapture(String time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        if (running) {
            mc.player.sendSystemMessage(Component.translatable("message.nerospace.capture.already_running"));
            return 0;
        }
        QUEUE.clear();
        QUEUE.addAll(galleryQueue(time));
        begin(mc);
        mc.player.sendSystemMessage(Component.translatable("message.nerospace.capture.gallery_started",
                time, QUEUE.size()));
        return Command.SINGLE_SUCCESS;
    }

    private static int startPlanets(String time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        if (running) {
            mc.player.sendSystemMessage(Component.translatable("message.nerospace.capture.busy"));
            return 0;
        }
        QUEUE.clear();
        QUEUE.addAll(buildPlanetShots(time));
        begin(mc);
        mc.player.sendSystemMessage(Component.translatable("message.nerospace.capture.planets_started",
                QUEUE.size(), time));
        return Command.SINGLE_SUCCESS;
    }

    private static int startAll(String time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        if (running) {
            mc.player.sendSystemMessage(Component.translatable("message.nerospace.capture.busy"));
            return 0;
        }
        QUEUE.clear();
        QUEUE.addAll(galleryQueue(time));
        QUEUE.addAll(buildPlanetShots(time));
        begin(mc);
        mc.player.sendSystemMessage(Component.translatable("message.nerospace.capture.all_started",
                QUEUE.size(), time));
        return Command.SINGLE_SUCCESS;
    }

    private static int shotHere(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || running) {
            return 0;
        }
        QUEUE.clear();
        QUEUE.add(new Shot(name, List.of(), List.of(), List.of(), 0, null, null)); // grab the current view as-is
        begin(mc);
        return Command.SINGLE_SUCCESS;
    }

    /** The gallery shot list with the one-time rebuild + environment-pin setup attached to shot 0. */
    private static List<Shot> galleryQueue(String time) {
        List<Shot> shots = new ArrayList<>(buildShots(ORIGIN_X, ORIGIN_Y, ORIGIN_Z));
        // Shot 0 carries the one-time scene rebuild. SETUP (pre-warmup) only teleports into the flat
        // capture dimension and pins the environment — NO building yet. After the warmup (chunks loaded),
        // BUILD rebuilds the gallery FROM SCRATCH (clear → build, so reruns never stack blocks/entities or
        // keep stale machine progress) and strips the floating debug labels. Doing this post-warmup avoids
        // placing into unloaded chunks. CLEANUP (after a further pause) sweeps up the items the label kill
        // drops, so the ground is clean. These are server commands (they need the creative world's cheats).
        List<String> setup = List.of(
                "execute in " + CAPTURE_DIMENSION + " run tp @s "
                        + (ORIGIN_X + 0.5) + " " + ORIGIN_Y + " " + (ORIGIN_Z + 0.5),
                "gamerule advance_time false",    // 26.1 renamed doDaylightCycle → advance_time
                "gamerule advance_weather false", // …and doWeatherCycle → advance_weather
                "time set " + time,               // capture dim is overworld-type → has a clock
                "weather clear");
        List<String> build = List.of(
                "nerospace gallery clear", // wipe any prior footprint so the rebuild can't stack duplicates
                "nerospace gallery",       // fresh build → fresh machine progress, no lingering artifacts
                // Strip the floating debug cluster labels (invisible name-tag stands) for clean marketing
                // shots — the visible suit stands keep their name tags (they aren't Invisible).
                "kill @e[type=armor_stand,nbt={Invisible:1b}]");
        Shot first = shots.get(0);
        shots.set(0, new Shot(first.name(), setup, build, groundClear(), BUILD_WARMUP_TICKS,
                first.camera(), first.target()));
        return shots;
    }

    private static void begin(Minecraft mc) {
        // screenshots/ is created by Screenshot.grab; the nerospace/ subfolder is not, so make it.
        new File(mc.gameDirectory, "screenshots/nerospace").mkdirs();
        hudWasHidden = isHudHidden(mc);
        setHudHidden(mc, true);
        cloudsWere = mc.options.cloudStatus().get();
        mc.options.cloudStatus().set(CloudStatus.OFF); // clouds scroll with game time → freeze them out of frame
        bobViewWas = mc.options.bobView().get();
        mc.options.bobView().set(false); // kill view-bob so a pinned pose renders the exact same frame
        // Fly the player so they hover at the teleport spot instead of falling through the (not-yet-built)
        // scene while we wait — keeps the right chunks loaded and the camera where we put it.
        flyingWas = mc.player != null && mc.player.getAbilities().flying;
        setFlying(mc, true);
        running = true;
        phase = Phase.MOVE; // each shot's setup carries any teleport/build; warmup is per-shot
        current = null;
    }

    /** Toggle creative flight so the camera hovers (no gravity drift) for the duration of a run. */
    private static void setFlying(Minecraft mc, boolean fly) {
        if (mc.player == null) {
            return;
        }
        mc.player.getAbilities().flying = fly && mc.player.getAbilities().mayfly;
        mc.player.onUpdateAbilities();
    }

    // ------------------------------------------------------------------------------------------------
    // Per-client-tick state machine (driven from each loader's client-tick hook).
    // ------------------------------------------------------------------------------------------------

    /** Called once per client tick from each loader's own client-tick hook. No-op unless a run is live. */
    public static void tick() {
        if (!running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            finish(mc);
            return;
        }

        switch (phase) {
            case MOVE -> {
                final Shot shot = QUEUE.poll();
                current = shot;
                if (shot == null) {
                    finish(mc);
                    return;
                }
                for (String cmd : shot.setup()) { // pre-warmup: teleport into the dimension + pin time/weather
                    player.connection.sendCommand(cmd);
                }
                warmup = shot.warmup();
                phase = Phase.WARMUP;
            }
            case WARMUP -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player); // keep flying + motionless so the player doesn't fall during the wait
                if (--warmup <= 0) { // teleport done + chunks now loaded → safe to clear / build / kill
                    for (String cmd : shot.build()) { // block placement needs loaded chunks (else "not loaded")
                        player.connection.sendCommand(cmd);
                    }
                    // Pause so any kill death-animations finish + dropped items settle before the sweep.
                    cleanupWait = shot.cleanup().isEmpty() ? 0 : CLEANUP_TICKS;
                    phase = Phase.CLEANUP;
                }
            }
            case CLEANUP -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player); // stay aloft while the death-smoke clears + items are swept
                if (--cleanupWait <= 0) {
                    for (String cmd : shot.cleanup()) { // sweep dropped items / dead-entity litter off the ground
                        player.connection.sendCommand(cmd);
                    }
                    applyPose(player, shot);
                    settle = SETTLE_TICKS;
                    phase = Phase.SETTLE;
                }
            }
            case SETTLE -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                applyPose(player, shot); // re-pin every tick so gravity/AI can't drift the camera
                if (--settle <= 0) {
                    phase = Phase.SHOOT;
                }
            }
            case SHOOT -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                grab(mc, shot.name());
                phase = Phase.MOVE;
            }
        }
    }

    /** Keep the player flying + motionless during a wait so gravity doesn't drift the camera. */
    private static void holdStill(Minecraft mc, LocalPlayer player) {
        if (!player.getAbilities().flying && player.getAbilities().mayfly) {
            setFlying(mc, true); // re-assert if the server toggled it off
        }
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** Snap the player (the render camera) to the shot's pose, holding it still. */
    private static void applyPose(LocalPlayer player, Shot shot) {
        if (shot == null) {
            return;
        }
        Vec3 cam = shot.camera();
        Vec3 tgt = shot.target();
        if (cam == null || tgt == null) {
            return; // "keep current view" shot
        }
        double dx = tgt.x - cam.x;
        double dy = tgt.y - cam.y;
        double dz = tgt.z - cam.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        player.snapTo(cam.x, cam.y, cam.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void grab(Minecraft mc, String name) {
        RenderTarget target = mainRenderTarget(mc);
        if (target == null) {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.translatable("message.nerospace.capture.no_target"));
            }
            return;
        }
        Screenshot.grab(mc.gameDirectory, "nerospace/" + name + ".png", target, 1,
                msg -> {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(msg);
                    }
                });
    }

    private static void finish(Minecraft mc) {
        setHudHidden(mc, hudWasHidden);
        if (cloudsWere != null) {
            mc.options.cloudStatus().set(cloudsWere);
        }
        mc.options.bobView().set(bobViewWas);
        setFlying(mc, flyingWas); // restore the player's flight state
        running = false;
        current = null;
        QUEUE.clear();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("message.nerospace.capture.done"));
        }
    }

    // --- HUD hide + render target (version-divergent → reflective) ----------------------------------

    private static void resolveHud() {
        if (hudResolved) {
            return;
        }
        hudResolved = true;
        try { // 26.1.x: Options#hideGui boolean field
            optHideGuiField = net.minecraft.client.Options.class.getDeclaredField("hideGui");
            optHideGuiField.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            optHideGuiField = null;
        }
        if (optHideGuiField == null) {
            try { // 26.2: Gui#hud → Hud#isHidden()/toggle()
                guiHudField = net.minecraft.client.gui.Gui.class.getDeclaredField("hud");
                guiHudField.setAccessible(true);
                Class<?> hudType = guiHudField.getType();
                hudIsHiddenMethod = hudType.getMethod("isHidden");
                hudToggleMethod = hudType.getMethod("toggle");
            } catch (ReflectiveOperationException | RuntimeException e) {
                guiHudField = null;
                hudIsHiddenMethod = null;
                hudToggleMethod = null;
            }
        }
    }

    private static boolean isHudHidden(Minecraft mc) {
        resolveHud();
        try {
            if (optHideGuiField != null) {
                return optHideGuiField.getBoolean(mc.options);
            }
            if (guiHudField != null && hudIsHiddenMethod != null) {
                Object hud = guiHudField.get(mc.gui);
                return hud != null && Boolean.TRUE.equals(hudIsHiddenMethod.invoke(hud));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // fall through to "not hidden"
        }
        return false;
    }

    private static void setHudHidden(Minecraft mc, boolean hidden) {
        resolveHud();
        try {
            if (optHideGuiField != null) {
                optHideGuiField.setBoolean(mc.options, hidden);
                return;
            }
            if (guiHudField != null && hudIsHiddenMethod != null && hudToggleMethod != null) {
                Object hud = guiHudField.get(mc.gui);
                if (hud != null && !Boolean.valueOf(hidden).equals(hudIsHiddenMethod.invoke(hud))) {
                    hudToggleMethod.invoke(hud); // toggle only when not already in the wanted state
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // best-effort; a missing flag just means the HUD shows in the shot
        }
    }

    /** The main framebuffer: 26.1.x {@code Minecraft#getMainRenderTarget()}, 26.2 {@code GameRenderer#mainRenderTarget()}. */
    private static RenderTarget mainRenderTarget(Minecraft mc) {
        if (!rtResolved) {
            rtResolved = true;
            try {
                mcGetRenderTarget = Minecraft.class.getMethod("getMainRenderTarget");
            } catch (NoSuchMethodException | RuntimeException e) {
                mcGetRenderTarget = null;
            }
            if (mcGetRenderTarget == null) {
                try {
                    grMainRenderTarget = mc.gameRenderer.getClass().getMethod("mainRenderTarget");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    grMainRenderTarget = null;
                }
            }
        }
        try {
            if (mcGetRenderTarget != null) {
                return (RenderTarget) mcGetRenderTarget.invoke(mc);
            }
            if (grMainRenderTarget != null) {
                return (RenderTarget) grMainRenderTarget.invoke(mc.gameRenderer);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // fall through
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------
    // Gallery shot list — reframed + multi-angle. Coordinates mirror the cluster bases in the
    // server-side NerospaceCommands.buildGallery (origin = capture-dim origin). Tune the two together.
    // ------------------------------------------------------------------------------------------------

    private static List<Shot> buildShots(int ox, int oy, int oz) {
        List<Shot> shots = new ArrayList<>();

        // Framing principles (after a review of the first pass, which sat too far back with huge dead sky):
        //   • get CLOSE so the subject fills the frame; aim the target at the subject's MID-height so the
        //     horizon sits high and there's little empty sky/floor;
        //   • shoot wide rows DOWN THE LINE from near one end at near-eye height, so they recede with
        //     perspective (the nearest piece reads large) instead of as distant broadside specks.
        // Coordinates mirror the cluster bases in NerospaceCommands.buildGallery — tune the two together.

        // BLOCK GRID (EAST, base ox+38 / oz-9, displays float at oy+3; grid centre ≈ (51, oy+3, 3)).
        shots.add(shot("blocks", 34, oy + 15, -13, 52, oy + 3, 4));          // tight high 3/4 over the grid
        shots.add(shot("blocks_angle", 35, oy + 5, -11, 52, oy + 3.5, 6));   // low corner skim

        // MACHINE STRIP (SOUTH, z+48, clusters along x −13..9). Shoot down the line + two detail clusters.
        shots.add(shot("machines", -16, oy + 3, 45.5, 7, oy + 1.4, 48));            // perspective down the strip
        shots.add(shot("machines_power", -15.5, oy + 2.8, 45.5, -11.5, oy + 1.5, 48)); // combustion→grinder line
        shots.add(shot("machines_refinery", -7.5, oy + 2.8, 45.5, -4.5, oy + 1.5, 48)); // battery→refinery→tank

        // PIPES (WEST, x −50..−46, four resource rows stacked over z −4..5). Elevated SE 3/4 so all 4 read.
        shots.add(shot("pipes", -44, oy + 6, 9, -49, oy + 1.4, 0.5));

        // SUITS + STAR GUIDE (NORTH-WEST, z−34; suits face south). Head-on from the south, tight.
        shots.add(shot("suits", -36, oy + 3.2, -28, -36, oy + 2, -34));
        shots.add(shot("star_guide", -28, oy + 2.6, -30, -28, oy + 2.2, -34));

        // ROCKET ROW (NORTH, z−48, tiers at x −13/−5/3/12; tall). Low 3/4 looking up so they tower,
        // plus two tier hero details + the live O2 line.
        shots.add(shot("rockets", -13, oy + 3, -30, 2, oy + 7, -48));
        shots.add(shot("rocket_t1", -12, oy + 2.6, -43.5, -12.5, oy + 4.5, -48));
        shots.add(shot("rocket_t4", 15, oy + 2.6, -41, 12, oy + 7, -48.5));     // Heavy Launch Complex + gantry
        shots.add(shot("rocket_o2", -9, oy + 2.4, -41.5, -9, oy + 2, -45));     // live O2 fuelling line
        shots.add(shot("travel_node", 16, oy + 2.2, -42, 16, oy + 1.4, -45));

        // LAUNCH CONTROLLER (centre-NW, ctrl at −27/oy+1/−18 facing south; hologram projects south).
        shots.add(shot("launch_controller", -23, oy + 3, -10, -27, oy + 2, -18));

        // CREATURES (SOUTH-EAST, z+34, twelve frozen along x 18..62). Down the line, low, near-eye height.
        shots.add(shot("creatures", 14, oy + 2, 36, 62, oy + 1.6, 34));
        shots.add(shot("creatures_closeup", 16.5, oy + 2.2, 31, 24, oy + 1.5, 34));

        // METEOR SITE (SOUTH-WEST, −28/+30; meteor hovers at oy+11). Close low angle: crater + core + meteor.
        shots.add(shot("meteor_site", -22, oy + 3.5, 25, -27.5, oy + 5, 30));

        // QUARRIES (NORTH-EAST). Landmark L, an operating 9×9 angled into the pit, and the big 33×33 claim.
        shots.add(shot("quarry_landmarks", 33, oy + 4, -33, 30, oy + 1.6, -38));
        shots.add(shot("quarry_operating", 40, oy + 6, -30, 46, oy - 1, -37));
        shots.add(shot("quarry_big", 90, oy + 18, -38, 102, oy - 2, -56));

        // SOLAR ARRAYS (SOUTH-WEST, base −50/+36). 3/4 across the tilted decks + a closer field angle.
        shots.add(shot("solar", -33, oy + 4, 45, -44, oy + 2, 38));
        shots.add(shot("solar_field", -46, oy + 4, 46, -44, oy + 2, 40));

        // MARS EXPEDITION (EAST-SOUTH-EAST, centre +84/+38): rover, Salvage Drone, Dust Strider, held Dust Devil.
        shots.add(shot("mars_expedition", 72, oy + 5, 28, 84, oy + 1.5, 38));
        shots.add(shot("mars_rover", 72.5, oy + 2.6, 35, 78.5, oy + 1, 38.5));   // rover close-up, from the west

        // CREW + DOCKING (NORTH-NORTH-EAST, T2 pad corner +16/−64): crewed rocket + an empty Docking Ring.
        shots.add(shot("crew_rocket", 21, oy + 4, -54, 21, oy + 2.5, -64));
        shots.add(shot("crew_seats", 17.5, oy + 5, -59.5, 17.5, oy + 4, -64)); // looking into the seated hull

        // ITEM WALL (WEST-SOUTH-WEST, wall x −54 facing east, z +8 onward). Broadside + a close look at the start.
        shots.add(shot("item_wall", -38, oy + 4.5, 21, -53, oy + 3.5, 21));
        shots.add(shot("item_wall_closeup", -47, oy + 3.5, 11, -53, oy + 3.5, 11));

        return shots;
    }

    // ------------------------------------------------------------------------------------------------
    // Planet vistas — SELF-CONTAINED controlled scenes (no terrain/seed dependence). Each builds a
    // fresh themed platform + frozen signature mobs at a fixed origin in the real dimension and clears
    // its own footprint first, so a rerun in ANY world produces the identical shot.
    // ------------------------------------------------------------------------------------------------

    private static List<Shot> buildPlanetShots(String time) {
        List<Shot> shots = new ArrayList<>();

        // Greenxertz (overworld-type → has a clock): green steel platform + xertz quartz / nerosteel
        // accents, with the Greenxertz fauna frozen on deck.
        shots.add(planetScene("greenxertz_vista", "nerospace:greenxertz", true, time,
                0, 110, 0, "nerospace:nerosteel_block", "nerospace:xertz_quartz_ore", "nerospace:alien_lamp",
                List.of("nerospace:xertz_stalker", "nerospace:quartz_crawler", "nerospace:greenling")));

        // Cindara (space-type → no clock, volcanic dark): cindrite platform + cindrite ore + meteor rock.
        shots.add(planetScene("cindara_basin", "nerospace:cindara", false, time,
                0, 110, 0, "nerospace:cindrite_block", "nerospace:cindrite_ore", "nerospace:meteor_rock",
                List.of("nerospace:cinder_stalker", "nerospace:ember_strutter")));

        // Glacira (space-type → no clock, frozen): glacite platform + glacite ore + blue ice.
        shots.add(planetScene("glacira_frost", "nerospace:glacira", false, time,
                0, 110, 0, "nerospace:glacite_block", "nerospace:glacite_ore", "minecraft:blue_ice",
                List.of("nerospace:frost_strider", "nerospace:woolly_drift")));

        // Orbital Station (space-type → starfield): a station floor pad with two walls + a Station Core.
        shots.add(stationScene("orbital_station", time));

        // Terraform before/after — staged on SUNLIT Greenxertz so the "living" palette reads bright.
        // Two patches at the same fixed origin, each fully rebuilt + cleared (so before never bleeds
        // into after). BEFORE = barren coarse-dirt; AFTER = grass on the neon terraformed_meadow biome
        // with two trees and a Meadow Loper.
        shots.add(terraformScene("terraform_before", time, false));
        shots.add(terraformScene("terraform_after", time, true));

        return shots;
    }

    /**
     * A self-contained planet scene, built rich enough to feel like a real outpost: a 15×15 themed deck
     * with a central ore patch, a corner ore deposit and two tall back-corner accent pillars, then a small
     * herd of the signature mobs. The mob kills happen in BUILD (post-warmup) but the fresh summons happen
     * in CLEANUP — after the {@link #CLEANUP_TICKS} pause — so no kill death-smoke lingers around the new
     * mobs. Framed close + near-deck so the herd reads large against the real dimension's sky.
     */
    private static Shot planetScene(String name, String dim, boolean hasClock, String time,
            int ox, int oy, int oz, String platform, String ore, String accent, List<String> mobs) {
        List<String> setup = teleportSetup(dim, hasClock, time, ox + 0.5, oy + 5, oz - 10);
        List<String> build = new ArrayList<>();
        // Clear the working box (incl. any prior platform) so reruns leave no artifacts, then kill the
        // prior herd (its death-smoke clears during the cleanup pause before we summon a fresh one).
        build.add(fill(ox - 9, oy - 1, oz - 9, ox + 9, oy + 24, oz + 9, "minecraft:air"));
        killMobs(build, mobs);
        build.add(fill(ox - 7, oy, oz - 7, ox + 7, oy, oz + 7, platform));         // 15×15 deck
        build.add(fill(ox - 2, oy, oz - 2, ox + 2, oy, oz + 2, ore));              // central ore patch
        build.add(fill(ox - 7, oy, oz - 7, ox - 5, oy, oz - 5, ore));              // a corner ore deposit
        build.add(fill(ox - 6, oy + 1, oz + 6, ox - 6, oy + 4, oz + 6, accent));   // two back-corner pillars
        build.add(fill(ox + 6, oy + 1, oz + 6, ox + 6, oy + 4, oz + 6, accent));
        // Fresh herd summoned post-smoke: each signature mob in a front and a back row → a lively group.
        List<String> cleanup = new ArrayList<>(groundClear());
        for (int i = 0; i < mobs.size(); i++) {
            int mxp = ox - 4 + i * 4;
            cleanup.add(summon(mobs.get(i), mxp, oy + 1, oz + 2));
            cleanup.add(summon(mobs.get(i), mxp - 1, oy + 1, oz - 2));
        }
        Vec3 cam = new Vec3(ox - 7, oy + 4, oz - 8);
        Vec3 tgt = new Vec3(ox + 1, oy + 2, oz + 1);
        return new Shot(name, setup, build, cleanup, PLANET_WARMUP_TICKS, cam, tgt);
    }

    /** The orbital station scene: a floor pad with two walls and a bound Station Core against the void. */
    private static Shot stationScene(String name, String time) {
        int ox = 0;
        int oy = 96;
        int oz = 0;
        List<String> setup = teleportSetup("nerospace:station", false, time, ox - 4, oy + 5, oz - 4);
        List<String> build = new ArrayList<>();
        build.add(fill(ox - 2, oy - 1, oz - 2, ox + 8, oy + 8, oz + 8, "minecraft:air")); // clear prior platform
        build.add(fill(ox, oy, oz, ox + 6, oy, oz + 6, "nerospace:station_floor"));      // 7×7 floor
        // Full perimeter walls (a proper little outpost, not two loose walls) + the bound Core, plus a
        // docking port and a landing pod so the platform reads as a real station against the starfield.
        build.add(fill(ox, oy + 1, oz, ox + 6, oy + 1, oz, "nerospace:station_wall"));
        build.add(fill(ox, oy + 1, oz + 6, ox + 6, oy + 1, oz + 6, "nerospace:station_wall"));
        build.add(fill(ox, oy + 1, oz, ox, oy + 1, oz + 6, "nerospace:station_wall"));
        build.add(fill(ox + 6, oy + 1, oz, ox + 6, oy + 1, oz + 6, "nerospace:station_wall"));
        build.add(setblock(ox + 3, oy + 1, oz + 3, "nerospace:station_core"));
        build.add(setblock(ox + 1, oy + 1, oz + 5, "nerospace:docking_port"));
        build.add(setblock(ox + 5, oy + 1, oz + 1, "nerospace:landing_pod"));
        // Elevated 3/4 over a near wall so the floor, walls and glowing Core all fill the frame.
        Vec3 cam = new Vec3(ox - 3, oy + 5, oz - 3);
        Vec3 tgt = new Vec3(ox + 3, oy + 1, oz + 3);
        return new Shot(name, setup, build, groundClear(), PLANET_WARMUP_TICKS, cam, tgt);
    }

    /**
     * Terraform before/after on sunlit Greenxertz, at a fixed origin, fully rebuilt + cleared each run.
     * {@code after=false} → barren coarse-dirt on the native biome; {@code after=true} → grass on the
     * neon {@code terraformed_meadow} biome with two oaks and a Meadow Loper.
     */
    private static Shot terraformScene(String name, String time, boolean after) {
        int bx = 40;
        int by = 110;
        int bz = 40;
        int x2 = bx + 12;
        int z2 = bz + 12;
        List<String> setup = teleportSetup("nerospace:greenxertz", true, time, bx - 5, by + 7, bz - 5);
        List<String> build = new ArrayList<>();
        build.add(fill(bx - 2, by, bz - 2, x2 + 2, by + 12, z2 + 2, "minecraft:air"));
        build.add("kill @e[type=nerospace:meadow_loper]");
        // Reset the biome each run so the prior pass never lingers.
        build.add("fillbiome " + bx + " " + (by - 1) + " " + bz + " " + x2 + " " + (by + 10) + " " + z2
                + " " + (after ? "nerospace:terraformed_meadow" : "nerospace:greenxertz"));
        build.add(fill(bx, by - 1, bz, x2, by - 1, z2, "minecraft:dirt")); // sub-base
        List<String> cleanup = new ArrayList<>(groundClear());
        if (after) {
            build.add(fill(bx, by, bz, x2, by, z2, "minecraft:grass_block"));
            build.add("place feature minecraft:oak " + (bx + 3) + " " + (by + 1) + " " + (bz + 3));
            build.add("place feature minecraft:oak " + (bx + 9) + " " + (by + 1) + " " + (bz + 8));
            build.add("place feature minecraft:oak " + (bx + 6) + " " + (by + 1) + " " + (bz + 2));
            // Loper herd summoned post-smoke (after the prior pass's kill-smoke has cleared).
            cleanup.add(summon("nerospace:meadow_loper", bx + 5, by + 1, bz + 6));
            cleanup.add(summon("nerospace:meadow_loper", bx + 8, by + 1, bz + 5));
        } else {
            build.add(fill(bx, by, bz, x2, by, z2, "minecraft:coarse_dirt"));
        }
        // Lower 3/4 so the grass, trees and Meadow Lopers read against the sky (not a flat top-down).
        Vec3 cam = new Vec3(bx - 2, by + 4, bz - 2);
        Vec3 tgt = new Vec3(bx + 8, by + 1.5, bz + 8);
        return new Shot(name, setup, build, cleanup, PLANET_WARMUP_TICKS, cam, tgt);
    }

    // ------------------------------------------------------------------------------------------------
    // Small command/shot builders.
    // ------------------------------------------------------------------------------------------------

    /** Pre-warmup setup: cross-dim teleport to the camera vantage, pin time/weather (clock dims only). */
    private static List<String> teleportSetup(String dim, boolean hasClock, String time,
            double camX, double camY, double camZ) {
        List<String> setup = new ArrayList<>();
        setup.add("execute in " + dim + " run tp @s " + camX + " " + camY + " " + camZ);
        setup.add("gamerule advance_time false");   // 26.1: replaces doDaylightCycle
        setup.add("gamerule advance_weather false"); // 26.1: replaces doWeatherCycle
        setup.add("weather clear");
        if (hasClock) {
            setup.add("time set " + time); // only dimensions with a clock accept this
        }
        return setup;
    }

    private static void killMobs(List<String> out, List<String> mobs) {
        for (String mob : mobs) {
            out.add("kill @e[type=" + mob + "]");
        }
    }

    private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block;
    }

    private static String setblock(int x, int y, int z, String block) {
        return "setblock " + x + " " + y + " " + z + " " + block;
    }

    private static String summon(String type, int x, int y, int z) {
        return "summon " + type + " " + (x + 0.5) + " " + y + " " + (z + 0.5)
                + " {NoAI:1b,PersistenceRequired:1b}";
    }

    /** A camera-only gallery shot (no setup/build/cleanup; the rebuild rides on shot 0). */
    private static Shot shot(String name, double cx, double cy, double cz, double tx, double ty, double tz) {
        return new Shot(name, List.of(), List.of(), List.of(), 0, new Vec3(cx, cy, cz), new Vec3(tx, ty, tz));
    }

    /** Sweep dropped items + XP-orb litter off the ground (run after a {@link #CLEANUP_TICKS} pause). */
    private static List<String> groundClear() {
        return List.of("kill @e[type=item]", "kill @e[type=experience_orb]");
    }
}
