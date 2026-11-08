package za.co.neroland.nerospace.progression;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import za.co.neroland.nerolandcore.event.ThresholdEvents;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.api.PlanetVisitEvents;
import za.co.neroland.nerospace.progression.StarGuide.Chapter;
import za.co.neroland.nerospace.progression.StarGuide.Step;
import za.co.neroland.nerospace.world.planet.PlanetClock;
import za.co.neroland.nerospace.world.planet.PlanetDefinition;
import za.co.neroland.nerospace.world.planet.PlanetWeather;
import za.co.neroland.nerospace.world.planet.Planets;

/**
 * The Worlds progression (and the quest pack contract): grants the code-driven Worlds steps of the Star Guide, opens
 * the matching player-scope <em>record</em> gates a quest pack reads through Core, and publishes Nerospace's three
 * {@link ThresholdEvents} channels.
 *
 * <h2>Channels (all anonymous — the scope is a place or a system, never a player)</h2>
 * <ul>
 *   <li>{@link #PLANET_FIRST_VISIT}: someone set foot on a planet for the first time. Scope = planet id; value =
 *       the world's running count of first visits (all planets).</li>
 *   <li>{@link #WEATHER_SURVIVED}: someone weathered a planet's trial (its weather event; Luna's night). Scope =
 *       planet id; value 1.</li>
 *   <li>{@link #STATION_MODULE_BUILT}: a station module was built. Scope = {@code module/<kind>}; value = the
 *       station's module count after the build.</li>
 *   <li>{@link #ROCKET_CREW_LAUNCH} (2.1): a rocket lifted off with a crew. Scope = {@code crew}; value = the number
 *       of players aboard (2..4).</li>
 * </ul>
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 * The exposure clock lives only in memory, keyed weakly by the live player object (gone on logout, death or
 * restart); nothing here is saved or logged, and no crossing carries a name or UUID.
 */
public final class WorldsProgress {

    public static final Identifier PLANET_FIRST_VISIT = ns("planet_first_visit");
    public static final Identifier WEATHER_SURVIVED = ns("weather_survived");
    public static final Identifier STATION_MODULE_BUILT = ns("station_module_built");
    /** 2.1: a rocket lifted off with two or more players aboard. Scope {@code crew}; value = the head count. */
    public static final Identifier ROCKET_CREW_LAUNCH = ns("rocket_crew_launch");

    /** Mod hook: a quest reward that briefs the player on the next Worlds step (a hint; awards nothing). */
    public static final Identifier WORLDS_BRIEFING = ns("hook/worlds_briefing");

    /** The planets with a Worlds chapter, in Star Guide order (the roster: luna, mars, cindara, glacira, greenxertz, nyxara). */
    public static final List<String> WORLDS = List.of("luna", "mars", "cindara", "glacira", "greenxertz", "nyxara");

    private record Exposure(ResourceKey<Level> dimension, long since) {
    }

    /** Transient exposure clocks (see the class note): weak keys, never persisted. Server thread only. */
    private static final Map<ServerPlayer, Exposure> EXPOSURE = new WeakHashMap<>();

    private WorldsProgress() {
    }

    private static Identifier ns(String path) {
        return Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, path);
    }

    /** The player-scope record gate a Worlds step opens: {@code nerospace:worlds/<planet>/<step>}. */
    public static Identifier gate(String planet, String step) {
        return ns("worlds/" + planet + "/" + step);
    }

    /** Registers the first-visit producer. Called once from common init. */
    public static void init() {
        PlanetVisitEvents.onVisit(visit -> {
            net.minecraft.server.MinecraftServer server = visit.player().level().getServer();
            long count = server == null ? 1L : Math.max(1L, PlanetVisitState.get(server).sequence());
            fire(PLANET_FIRST_VISIT, visit.planet().asString(), count, 1L);
        });
    }

    /**
     * A crewed launch (2.1): fires {@link #ROCKET_CREW_LAUNCH} with the crew count only (threshold 2), never who
     * flew. A solo launch fires nothing.
     */
    public static void crewLaunch(int crew) {
        if (crew >= 2) {
            fire(ROCKET_CREW_LAUNCH, "crew", crew, 2L);
        }
    }

    /** Station modules (4B's builder calls this after a fresh module is appended). */
    public static void stationModuleBuilt(String kindId, int moduleCount) {
        fire(STATION_MODULE_BUILT, "module/" + kindId, moduleCount, 1L);
    }

    private static void fire(Identifier channel, String scope, long value, long threshold) {
        ThresholdEvents.fire(NerospaceCommon.MOD_ID,
                new ThresholdEvents.ThresholdCrossing(channel, scope, value, threshold, true));
    }

    /**
     * Per-player Worlds check, driven from {@link StarGuideGrants#tick} (already throttled). {@code terraformStage}
     * is the effective stage of the chunk the player stands in.
     */
    public static void tick(ServerPlayer player, int terraformStage) {
        ServerLevel level = player.level();
        PlanetDefinition planet = Planets.get(level.dimension());
        if (planet == null || !WORLDS.contains(planet.langId())) {
            EXPOSURE.remove(player);
            return;
        }
        String p = planet.langId();
        if (terraformStage >= 1) {
            StarGuideGrants.grant(player, "worlds/" + p + "/terraform");
            ProgressionGates.open(player, gate(p, "terraform")); // record gate; re-opening is a no-op
        }
        tickWeather(player, level, p);
    }

    private static void tickWeather(ServerPlayer player, ServerLevel level, String planet) {
        String path = "worlds/" + planet + "/weather";
        if (StarGuideGrants.isDone(player, path)) {
            EXPOSURE.remove(player);
            return;
        }
        boolean active = player.isAlive() && WeatherSurvivalRules.trialActive(PlanetWeather.event(level),
                "luna".equals(planet), PlanetClock.daylight(level), level.canSeeSky(player.blockPosition().above()));
        if (!active) {
            EXPOSURE.remove(player);
            return;
        }
        long now = level.getGameTime();
        Exposure e = EXPOSURE.get(player);
        if (e == null || !e.dimension().equals(level.dimension())) {
            EXPOSURE.put(player, new Exposure(level.dimension(), now));
            return;
        }
        if (WeatherSurvivalRules.survived(e.since(), now)) {
            EXPOSURE.remove(player);
            StarGuideGrants.grant(player, path);
            ProgressionGates.open(player, gate(planet, "weather"));
            fire(WEATHER_SURVIVED, Planets.get(level.dimension()).id().asString(), 1L, 1L);
        }
    }

    /** The {@link #WORLDS_BRIEFING} hook: names the next incomplete Worlds step, preferring the current planet. */
    public static void briefing(ServerPlayer player) {
        PlanetDefinition here = Planets.get(player.level().dimension());
        Step next = null;
        Chapter in = null;
        for (Chapter chapter : StarGuide.CHAPTERS) {
            if (!chapter.id().startsWith(StarGuide.WORLDS_PREFIX)) {
                continue;
            }
            Step candidate = firstOpen(player, chapter);
            if (candidate == null) {
                continue;
            }
            boolean local = here != null && chapter.id().equals(StarGuide.WORLDS_PREFIX + here.langId());
            if (next == null || local) {
                next = candidate;
                in = chapter;
                if (local) {
                    break;
                }
            }
        }
        Component tag = Component.literal("[Nerospace] ").withStyle(ChatFormatting.LIGHT_PURPLE);
        if (next == null || in == null) {
            player.sendSystemMessage(Component.empty().append(tag).append(
                    Component.translatable("message.nerospace.hook.worlds_complete").withStyle(ChatFormatting.GRAY)));
            return;
        }
        player.sendSystemMessage(Component.empty().append(tag).append(Component.translatable(
                "message.nerospace.hook.star_guide_hint",
                Component.translatable(in.titleKey()).withStyle(ChatFormatting.AQUA),
                Component.translatable(next.titleKey()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.translatable(next.textKey()).withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    private static Step firstOpen(ServerPlayer player, Chapter chapter) {
        for (Step step : chapter.steps()) {
            if (!step.hidden() && !StarGuideProgress.isComplete(player, step)) {
                return step; // a hidden step is never hinted
            }
        }
        return null;
    }
}
