package za.co.neroland.nerospace.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

/**
 * Nerospace's config, now backed by Neroland Core's shared {@link ConfigManager}. Core owns the single
 * {@code config/nerospace.properties} file (defaults, range validation, in-place key migration, the
 * {@code /neroland config reload} hot-reload, and server-authoritative client sync) — Nerospace just
 * declares the schema once and reads the typed {@link ConfigValue} handles through the same static getters
 * the rest of the mod already calls, so no machine code changed.
 *
 * <p>The key names are unchanged from the previous standalone properties file, so an existing
 * {@code nerospace.properties} migrates seamlessly (Core preserves user edits, only rewriting when a key
 * is missing).</p>
 *
 * <p><b>POPIA/GDPR:</b> the gameplay-balance multipliers are <i>server-authoritative</i> (a client uses
 * the server's values when connected). {@code telemetryEnabled} is deliberately <b>not</b>
 * server-authoritative — anonymous crash reporting is a per-client opt-out that a server must never force
 * on or off. The server-sync snapshot carries only config keys/values, never player data.</p>
 */
public final class NerospaceConfig {

    /** Multiplier range (mirrors the prior config): 0.1× .. 10×. */
    private static final double MULT_MIN = 0.1D;
    private static final double MULT_MAX = 10.0D;

    private static final ConfigSchema SCHEMA = ConfigSchema.create("nerospace",
            "Nerospace config (managed by Neroland Core). All multipliers 0.1..10, default 1.");

    private static final ConfigValue<Boolean> TELEMETRY = SCHEMA.bool("telemetryEnabled", true, false,
            "send anonymous, Nerospace-only crash reports (Sentry, EU servers) - stack trace, "
            + "mod/MC/loader/OS/Java versions, your other installed mods, this mod's config, recent in-game "
            + "actions, anonymous stability/timing; no IP, username, UUID, world data or chat; file paths "
            + "scrubbed of your account name. false = opt out of all of it. See PRIVACY.md");
    private static final ConfigValue<Double> ENERGY_RATE = SCHEMA.doubleRange("energyRateMultiplier",
            1.0D, MULT_MIN, MULT_MAX, true, "scales FE/tick of all generators (combustion, passive, solar)");
    private static final ConfigValue<Double> OXYGEN_DRAIN = SCHEMA.doubleRange("oxygenDrainMultiplier",
            1.0D, MULT_MIN, MULT_MAX, true, "scales how fast air drains off a safe zone");
    private static final ConfigValue<Double> OXYGEN_CAPACITY = SCHEMA.doubleRange("oxygenCapacityMultiplier",
            1.0D, MULT_MIN, MULT_MAX, true, "scales player + suit air capacity");
    private static final ConfigValue<Double> FUEL_COST = SCHEMA.doubleRange("fuelCostMultiplier",
            1.0D, MULT_MIN, MULT_MAX, true, "scales fuel burned per rocket launch (clamped to the tank)");
    private static final ConfigValue<Double> MACHINE_SPEED = SCHEMA.doubleRange("machineSpeedMultiplier",
            1.0D, MULT_MIN, MULT_MAX, true, "scales machine work speed (higher = faster)");
    private static final ConfigValue<Double> GRAVITY = SCHEMA.doubleRange("gravityMultiplier",
            1.0D, MULT_MIN, MULT_MAX, true, "global scale on per-dimension/per-biome gravity (higher = stronger)");
    private static final ConfigValue<Integer> QUARRY_MAX_SIDE = SCHEMA.intRange("quarryMaxSide",
            64, 4, 64, true, "max quarry landmark claim side in blocks (4..64, default 64)");
    private static final ConfigValue<Integer> QUARRY_FRAME_DECAY_TICKS = SCHEMA.intRange("quarryFrameDecayTicks",
            600, 20, 24000, true, "base delay in ticks before an orphaned quarry frame block crumbles and "
            + "drops its casing; each block waits base + rand(base*7), so the default 600 spreads the ring's "
            + "decay over ~30s..4min (20..24000)");
    private static final ConfigValue<Boolean> ALIEN_RAIDS = SCHEMA.bool("alienRaidsEnabled", true, true,
            "allow hostile mobs to raid alien villages at night (true by default; false = opt out)");
    private static final ConfigValue<Boolean> TERRAFORMER_FORCE_LOAD = SCHEMA.bool("terraformerForceLoadEnabled",
            false, true, "a running Terraformer keeps a small window of chunks loaded so it terraforms "
            + "while you are away (false by default; opt-in - costs server memory/TPS while it runs)");
    private static final ConfigValue<Boolean> LINK_MODULE = SCHEMA.bool("linkModuleEnabled", true, true,
            "expose Nerospace data to the NeroLink companion bridge (your own rockets, stations, planets, "
            + "life support and Star Guide progress - never another player's). true by default; false = the "
            + "companion app reports Nerospace as absent. Server-authoritative: the server decides");
    private static final ConfigValue<Boolean> WELCOME_MESSAGE = SCHEMA.bool("welcomeMessageEnabled",
            true, true, "show the two-line Nerospace welcome in chat when a player joins (the Star Guide "
            + "prompt plus the feedback link). true by default; false = join silently. "
            + "Server-authoritative: the server sends the message, so the server decides");

    // --- Cargo rockets (docs/CARGO-ROCKETS.md §5) — all server-authoritative -------------------
    private static final ConfigValue<Integer> CARGO_PAD_SLOTS = SCHEMA.intRange("cargoPadSlots",
            18, 1, 27, true, "usable cargo slots on a Cargo Pad = the payload a cargo rocket carries (1..27)");
    private static final ConfigValue<Integer> CARGO_FUEL_CAPACITY = SCHEMA.intRange("cargoFuelCapacity",
            16_000, 1_000, 64_000, true, "rocket fuel (mB) a Cargo Pad's pipe-fed fuel buffer holds");
    private static final ConfigValue<Double> CARGO_TRAVEL_TIME = SCHEMA.doubleRange("cargoTravelTimeMultiplier",
            1.0D, MULT_MIN, MULT_MAX, true, "scales cargo-rocket travel time (higher = slower flights)");
    private static final ConfigValue<Integer> CARGO_HOLD_RETRY_SECONDS = SCHEMA.intRange("cargoHoldRetrySeconds",
            30, 5, 600, true, "how often a holding cargo flight retries its landing (seconds)");
    private static final ConfigValue<Integer> CARGO_HOLD_TIMEOUT_MINUTES = SCHEMA.intRange("cargoHoldTimeoutMinutes",
            30, 1, 1440, true, "how long a cargo flight holds before its cargo is dropped as a crate at the "
            + "destination (minutes; cargo is never deleted)");
    private static final ConfigValue<Integer> CARGO_FLIGHT_RETENTION_DAYS = SCHEMA.intRange("cargoFlightRetentionDays",
            7, 1, 90, true, "in-game days a completed cargo flight record is kept before it is pruned");
    private static final ConfigValue<Integer> CARGO_MAX_FLIGHTS_PER_OWNER = SCHEMA.intRange("cargoMaxFlightsPerOwner",
            16, 1, 128, true, "concurrent live cargo flights one player may have");
    private static final ConfigValue<Integer> CARGO_MAX_PADS = SCHEMA.intRange("cargoMaxPads",
            256, 8, 4096, true, "hard cap on registered Cargo Pads (keeps a grief-spammed registry small)");

    // --- Economy (only used when the optional NeroEconomy mod is installed) ----------------------
    // Every amount defaults to 0 = off, so a server that does not opt in plays exactly as before.
    private static final ConfigValue<Integer> ECONOMY_FIRST_VISIT_REWARD = SCHEMA.intRange("economyFirstVisitReward",
            0, 0, 1_000_000, true, "credits paid once when a player first reaches each planet (0 = off; needs "
            + "NeroEconomy, paid from its Reserve and subject to its sibling budgets)");
    private static final ConfigValue<Integer> ECONOMY_LAUNCH_FEE = SCHEMA.intRange("economyLaunchFee",
            0, 0, 1_000_000, true, "credits charged per crewed or cargo rocket launch (0 = off; needs NeroEconomy; "
            + "a player who cannot pay is refused the launch)");
    private static final ConfigValue<Integer> ECONOMY_DOCKING_FEE = SCHEMA.intRange("economyDockingFee",
            0, 0, 1_000_000, true, "credits charged per flight bound for an orbital station, on top of the launch "
            + "fee (0 = off; needs NeroEconomy)");

    // --- 2.0 planets: clock, weather, solar and later-stage keys (all server-authoritative) --------------
    // Every 1.x key above keeps its name, type and default. These are appended; a 1.x file loads unchanged and
    // gains them on the next write.
    private static final ConfigValue<Boolean> BLOCKS_NYX_STONE_LIGHT_ABSORB = SCHEMA.bool("blocks.nyxStoneLightAbsorb",
            true, true, "Nyx Stone dims light around it (false = it only blocks light)");
    private static final ConfigValue<Boolean> BLOCKS_METHANE_ICE_BREAKS_BLOCKS = SCHEMA.bool("blocks.methaneIceBreaksBlocks",
            false, true, "Methane Ice explosions break blocks (false by default: they hurt entities only, no craters)");
    // Per-chunk caps ("config may lower, never raise"): default = max = the locked number.
    private static final ConfigValue<Integer> CAP_SPORE_MOSS = SCHEMA.intRange("blocks.sporeMossCap",
            48, 0, 48, true, "max Spore Moss per chunk (0..48)");
    private static final ConfigValue<Integer> CAP_SHADOW_LICHEN = SCHEMA.intRange("blocks.shadowLichenCap",
            32, 0, 32, true, "max Shadow Lichen faces per chunk (0..32)");
    private static final ConfigValue<Integer> CAP_REGOLITH_DRIFT = SCHEMA.intRange("blocks.regolithDriftCap",
            16, 0, 16, true, "max Red Regolith dune-drift moves per chunk per dust storm (0..16)");
    private static final ConfigValue<Integer> CAP_ASH_LAYER = SCHEMA.intRange("blocks.ashLayerCap",
            4, 0, 4, true, "max Ash Layer height ash fall builds up (0..4; players can still stack to 8)");
    private static final ConfigValue<Integer> CAP_FROST_CRYSTAL = SCHEMA.intRange("blocks.frostCrystalCap",
            64, 0, 64, true, "max Frost Crystal Clusters that grow per chunk when diamond dust ends (0..64)");
    private static final ConfigValue<Integer> CAP_DAWNBAND = SCHEMA.intRange("blocks.dawnbandCap",
            256, 0, 256, true, "max Dawnband Stone per chunk that light up per flare (0..256)");
    private static final ConfigValue<Integer> CAP_GRAVITY_OVERRIDE = SCHEMA.intRange("blocks.gravityOverrideCap",
            64, 0, 64, true, "max Gravity Shard Blocks + Gravity Plates per chunk (0..64)");
    private static final ConfigValue<Integer> CAP_METHANE_CHAIN = SCHEMA.intRange("blocks.methaneChainCap",
            4, 0, 4, true, "max Methane Ice explosions per chunk per 5 seconds (0..4)");
    private static final ConfigValue<Boolean> WEATHER_ENABLED = SCHEMA.bool("weather.enabled", true, true,
            "planet weather (dust storms, ash fall, diamond dust, spore blooms, flares). false = clear skies everywhere");
    private static final ConfigValue<Double> WEATHER_FREQUENCY = SCHEMA.doubleRange("weather.frequencyMultiplier",
            1.0D, MULT_MIN, MULT_MAX, true, "scales how often planet weather starts (higher = more often)");
    private static final ConfigValue<Double> WEATHER_DURATION = SCHEMA.doubleRange("weather.durationMultiplier",
            1.0D, MULT_MIN, MULT_MAX, true, "scales how long planet weather lasts");
    private static final ConfigValue<Integer> WEATHER_PARTICLE_BUDGET = SCHEMA.intRange("weather.particleBudget",
            1000, 0, 4000, true, "weather particles a client may spawn per second (0..4000)");
    private static final ConfigValue<Integer> WEATHER_EMBER_STRIKE_INTERVAL = SCHEMA.intRange(
            "weather.emberStrikeIntervalTicks", 100, 40, 12000, true,
            "ticks between Cindara ember strikes near each player during strong ash fall (40..12000)");
    // Per-planet weather switches, read by key through planetWeatherEnabled (each planet definition names its
    // key, weather.<planet>.enabled). Declared here, in this order, so they appear in the file.
    static {
        SCHEMA.bool("weather.greenxertz.enabled", true, true, "spore blooms on Greenxertz");
        SCHEMA.bool("weather.mars.enabled", true, true, "dust storms on Mars");
        SCHEMA.bool("weather.cindara.enabled", true, true, "ash fall on Cindara");
        SCHEMA.bool("weather.glacira.enabled", true, true, "diamond dust on Glacira");
        SCHEMA.bool("weather.nyxara.enabled", true, true, "flares on Nyxara");
    }
    private static final ConfigValue<Boolean> CLOCK_ENABLED = SCHEMA.bool("clock.enabled", true, true,
            "planet day/night clocks. false = every planet clock holds at noon (Greenxertz keeps its real day)");
    private static final ConfigValue<Boolean> SOLAR_CLOCK_DRIVEN = SCHEMA.bool("solar.clockDriven", true, true,
            "solar panels on planets follow the planet's day and weather (averaging at least the old output). "
            + "false = the 1.x flat double rate on every Nerospace world");
    private static final ConfigValue<Integer> LANDING_POD_SEARCH_RADIUS = SCHEMA.intRange("landing.podSearchRadius",
            24, 6, 24, true, "how far (blocks) an arriving rocket searches for a safe Landing Pod site (6..24)");
    private static final ConfigValue<Integer> STATIONS_MAX_MEMBERS = SCHEMA.intRange("stations.maxMembers",
            16, 0, 16, true, "members a station owner may add (0..16)");
    private static final ConfigValue<Integer> STATIONS_MAX_MODULES = SCHEMA.intRange("stations.maxModules",
            8, 0, 8, true, "modules a station may have (0..8)");
    private static final ConfigValue<Integer> STATIONS_MIN_SPACING = SCHEMA.intRange("stations.minSpacing",
            512, 128, 4096, true, "least distance (blocks, on each axis) between a station placed with a charter in "
            + "the station dimension and every other station centre or the origin platform (128..4096)");
    private static final ConfigValue<Integer> STATIONS_MAX_RADIUS = SCHEMA.intRange("stations.maxRadius",
            30000, 1024, 1000000, true, "how far from 0,0 (blocks, on each axis) a station may be placed with a "
            + "charter in the station dimension (1024..1000000)");
    private static final ConfigValue<Integer> STATIONS_FREE_DOCK_RADIUS = SCHEMA.intRange("stations.freeDockRadius",
            2048, 0, 8192, true, "how close (blocks) a hand-placed Docking Ring outside every station claim must be to "
            + "where a rocket last stood in the station dimension (or to the origin before its first station flight) "
            + "to appear in its dock list as a free dock; 0 turns free docks off (0..8192)");
    private static final ConfigValue<Integer> ROCKETS_MAX_CREW = SCHEMA.intRange("rockets.maxCrew",
            4, 1, 4, true, "players a crewed rocket seats: the first aboard is the pilot, the rest are crew (1..4; "
            + "cargo rockets stay uncrewed)");
    private static final ConfigValue<Integer> ROVER_CAPACITY = SCHEMA.intRange("rover.capacity",
            40000, 1000, 1000000, true, "FE a Mars rover's battery holds (1000..1000000)");
    private static final ConfigValue<Integer> ROVER_MAX_PER_PLAYER = SCHEMA.intRange("rover.maxPerPlayer",
            4, 1, 64, true, "rovers one player may have placed at once, counted over loaded rovers when "
            + "placing (1..64)");
    private static final ConfigValue<Integer> ROVER_DRAIN_PER_TICK = SCHEMA.intRange("rover.drainPerTick",
            5, 0, 1000, true, "FE a rover uses per tick while its driver holds the throttle; 0 = free "
            + "driving (0..1000)");
    private static final ConfigValue<Integer> ROVER_CHARGE_RATE = SCHEMA.intRange("rover.chargeRate",
            200, 0, 100000, true, "FE per tick a parked rover draws from Neroland energy blocks next to it; 0 = no "
            + "charging (0..100000)");
    private static final ConfigValue<Integer> NYXARA_TWILIGHT_BAND_WIDTH = SCHEMA.intRange("nyxara.twilightBandWidth",
            2048, 512, 16384, true, "full width (blocks) of Nyxara's habitable twilight band (512..16384); applies "
            + "to new worlds only - an existing world keeps the width it was created with");

    // --- Ambient planet particles (client-side, per player; never server-authoritative) ---------------------
    private static final ConfigValue<Boolean> PARTICLES_ENABLED = SCHEMA.bool("particles.enabled", true, false,
            "drifting planet particles around you (Mars dust, Cindara ash and embers, Greenxertz spores, Glacira "
            + "diamond dust, Nyxara nebula motes). Client-side only; false = none");
    private static final ConfigValue<Double> PARTICLES_DUST = particleRate("dust", 0.6D, "Mars dust");
    private static final ConfigValue<Double> PARTICLES_ASH = particleRate("ash", 0.5D, "Cindara ash");
    private static final ConfigValue<Double> PARTICLES_SPORE = particleRate("spore", 0.4D, "Greenxertz spores");
    private static final ConfigValue<Double> PARTICLES_DIAMOND_DUST = particleRate("diamond_dust", 0.8D,
            "Glacira diamond dust");
    private static final ConfigValue<Double> PARTICLES_EMBER = particleRate("ember", 0.2D,
            "Cindara embers near lava and magma");
    private static final ConfigValue<Double> PARTICLES_NEBULA_MOTE = particleRate("nebula_mote", 0.3D,
            "Nyxara nebula motes");

    // --- HUD (client-side, per player; never server-authoritative) ----------------------------------------
    private static final ConfigValue<Boolean> HUD_CLOCK_DIAL = SCHEMA.bool("hud.clockDial", true, false,
            "show the planet clock dial above the oxygen gauge on planets with a day cycle. Client-side only");
    private static final ConfigValue<Integer> HUD_CLOCK_DIAL_SCALE = SCHEMA.intRange("hud.clockDialScale", 1, 1, 2,
            false, "clock dial size: 1 = 16 px, 2 = 32 px. Client-side only");
    private static final ConfigValue<Boolean> HUD_WEATHER_LINE = SCHEMA.bool("hud.weatherLine", true, false,
            "show the current planet weather and its intensity above the oxygen gauge. Client-side only");

    // --- Sky (client-side, per player; never server-authoritative) ----------------------------------------
    private static final ConfigValue<Boolean> SKY_AURORA = SCHEMA.bool("sky.aurora", true, false,
            "draw aurora curtains in planet skies (Glacira, Nyxara's nightside, Mars after a dust storm, the Greenxertz "
            + "spore shimmer). Client-side only; false = none");
    private static final ConfigValue<Boolean> SKY_SHOOTING_STARS = SCHEMA.bool("sky.shootingStars", true, false,
            "draw shooting stars in planet skies. Client-side only; false = none");
    private static final ConfigValue<String> SKY_QUALITY = SCHEMA.string("sky.quality", "full", false,
            "planet sky detail: full, or lite (half the aurora layers and segments, no cloud deck, slightly smaller "
            + "stars) for low-end GPUs. Client-side only");

    private static ConfigValue<Double> particleRate(String type, double def, String what) {
        return SCHEMA.doubleRange("particles." + type + ".perPlayerPerTick", def, 0.0D, 8.0D, false,
                what + ": particles spawned around you per tick at full weather (0..8; also capped by "
                + "weather.particleBudget per second). Client-side only");
    }

    private static volatile boolean loaded;

    private NerospaceConfig() {
    }

    public static int cargoPadSlots() {
        return CARGO_PAD_SLOTS.get();
    }

    public static int cargoFuelCapacity() {
        return CARGO_FUEL_CAPACITY.get();
    }

    public static double cargoTravelTimeMultiplier() {
        return CARGO_TRAVEL_TIME.get();
    }

    public static int cargoHoldRetryTicks() {
        return CARGO_HOLD_RETRY_SECONDS.get() * 20;
    }

    public static long cargoHoldTimeoutTicks() {
        return CARGO_HOLD_TIMEOUT_MINUTES.get() * 1_200L;
    }

    public static long cargoFlightRetentionTicks() {
        return CARGO_FLIGHT_RETENTION_DAYS.get() * 24_000L;
    }

    public static int cargoMaxFlightsPerOwner() {
        return CARGO_MAX_FLIGHTS_PER_OWNER.get();
    }

    public static int cargoMaxPads() {
        return CARGO_MAX_PADS.get();
    }

    /** Credits for a player's first arrival at each planet; 0 = off. Used only with NeroEconomy. */
    public static int economyFirstVisitReward() {
        return ECONOMY_FIRST_VISIT_REWARD.get();
    }

    /** Credits charged per rocket launch; 0 = off. Used only with NeroEconomy. */
    public static int economyLaunchFee() {
        return ECONOMY_LAUNCH_FEE.get();
    }

    /** Credits charged per flight bound for an orbital station; 0 = off. Used only with NeroEconomy. */
    public static int economyDockingFee() {
        return ECONOMY_DOCKING_FEE.get();
    }

    public static boolean nyxStoneLightAbsorb() {
        return BLOCKS_NYX_STONE_LIGHT_ABSORB.get();
    }

    public static boolean methaneIceBreaksBlocks() {
        return BLOCKS_METHANE_ICE_BREAKS_BLOCKS.get();
    }

    public static int sporeMossCap() {
        return CAP_SPORE_MOSS.get();
    }

    public static int shadowLichenCap() {
        return CAP_SHADOW_LICHEN.get();
    }

    public static int regolithDriftCap() {
        return CAP_REGOLITH_DRIFT.get();
    }

    public static int ashLayerCap() {
        return CAP_ASH_LAYER.get();
    }

    public static int frostCrystalCap() {
        return CAP_FROST_CRYSTAL.get();
    }

    public static int dawnbandCap() {
        return CAP_DAWNBAND.get();
    }

    public static int gravityOverrideCap() {
        return CAP_GRAVITY_OVERRIDE.get();
    }

    public static int methaneChainCap() {
        return CAP_METHANE_CHAIN.get();
    }

    /** Ticks between Cindara ember-strike steps per level (default 100, min 40). */
    public static int emberStrikeIntervalTicks() {
        return WEATHER_EMBER_STRIKE_INTERVAL.get();
    }

    /** Master weather switch (default true). */
    public static boolean weatherEnabled() {
        return WEATHER_ENABLED.get();
    }

    /**
     * A per-planet weather switch read by its schema key ({@code weather.<planet>.enabled}, named by the planet
     * definition). A key missing from the schema is a wiring bug: it logs once and reads as enabled.
     */
    public static boolean planetWeatherEnabled(String key) {
        ConfigValue<?> value = SCHEMA.value(key);
        if (value != null && value.get() instanceof Boolean on) {
            return on;
        }
        if (MISSING_WEATHER_KEYS.add(key)) {
            za.co.neroland.nerospace.NerospaceCommon.LOGGER.warn("[Nerospace] no config key {}; weather stays on", key);
        }
        return true;
    }

    private static final java.util.Set<String> MISSING_WEATHER_KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static double weatherFrequencyMultiplier() {
        return WEATHER_FREQUENCY.get();
    }

    public static double weatherDurationMultiplier() {
        return WEATHER_DURATION.get();
    }

    public static int weatherParticleBudget() {
        return WEATHER_PARTICLE_BUDGET.get();
    }

    /** Whether planet clocks run (default true); false pins every PlanetClock planet at noon. */
    public static boolean clockEnabled() {
        return CLOCK_ENABLED.get();
    }

    /** Whether planet solar output follows the clock (default true); false = the 1.x flat 2x. */
    public static boolean solarClockDriven() {
        return SOLAR_CLOCK_DRIVEN.get();
    }

    public static int landingPodSearchRadius() {
        return LANDING_POD_SEARCH_RADIUS.get();
    }

    public static int stationsMaxMembers() {
        return STATIONS_MAX_MEMBERS.get();
    }

    public static int stationsMinSpacing() {
        return STATIONS_MIN_SPACING.get();
    }

    public static int stationsMaxRadius() {
        return STATIONS_MAX_RADIUS.get();
    }

    public static int stationsFreeDockRadius() {
        return STATIONS_FREE_DOCK_RADIUS.get();
    }

    /** Seats in a crewed rocket ({@code rockets.maxCrew}, 1..4); read through {@code rocket.Crew#maxCrew}. */
    public static int rocketsMaxCrew() {
        return ROCKETS_MAX_CREW.get();
    }

    /** Mars rover battery size ({@code rover.capacity}, FE). */
    public static int roverCapacity() {
        return ROVER_CAPACITY.get();
    }

    /** Rovers one player may have placed ({@code rover.maxPerPlayer}). */
    public static int roverMaxPerPlayer() {
        return ROVER_MAX_PER_PLAYER.get();
    }

    /** FE per tick of throttle ({@code rover.drainPerTick}). */
    public static int roverDrainPerTick() {
        return ROVER_DRAIN_PER_TICK.get();
    }

    /** FE per tick a parked rover may draw ({@code rover.chargeRate}). */
    public static int roverChargeRate() {
        return ROVER_CHARGE_RATE.get();
    }

    public static int stationsMaxModules() {
        return STATIONS_MAX_MODULES.get();
    }

    public static int nyxaraTwilightBandWidth() {
        return NYXARA_TWILIGHT_BAND_WIDTH.get();
    }

    /** Client-side: whether the HUD clock dial shows. */
    public static boolean hudClockDial() {
        return HUD_CLOCK_DIAL.get();
    }

    /** Client-side: the HUD clock dial scale, 1 or 2. */
    public static int hudClockDialScale() {
        return HUD_CLOCK_DIAL_SCALE.get();
    }

    /** Client-side: whether the HUD weather line shows. */
    public static boolean hudWeatherLine() {
        return HUD_WEATHER_LINE.get();
    }

    /** Client-side: whether planet skies draw aurora curtains. */
    public static boolean skyAurora() {
        return SKY_AURORA.get();
    }

    /** Client-side: whether planet skies draw shooting stars. */
    public static boolean skyShootingStars() {
        return SKY_SHOOTING_STARS.get();
    }

    /** Client-side: whether the sky runs at {@code lite} quality ({@code sky.quality=lite}; anything else is full). */
    public static boolean skyLite() {
        return isLite(SKY_QUALITY.get());
    }

    /** {@code sky.quality} parsing: {@code lite} (any case, trimmed) is lite, everything else full. */
    public static boolean isLite(String quality) {
        return quality != null && "lite".equalsIgnoreCase(quality.trim());
    }

    /** Client-side: whether ambient planet particles spawn at all. */
    public static boolean particlesEnabled() {
        return PARTICLES_ENABLED.get();
    }

    /** Client-side spawn rate (particles per player per tick at full weather) for a particle type id. */
    public static double particleRate(String type) {
        return switch (type) {
            case "dust" -> PARTICLES_DUST.get();
            case "ash" -> PARTICLES_ASH.get();
            case "spore" -> PARTICLES_SPORE.get();
            case "diamond_dust" -> PARTICLES_DIAMOND_DUST.get();
            case "ember" -> PARTICLES_EMBER.get();
            case "nebula_mote" -> PARTICLES_NEBULA_MOTE.get();
            default -> 0.0D;
        };
    }

    /** The schema (read-only use: tests and diagnostics). */
    public static ConfigSchema schema() {
        return SCHEMA;
    }

    public static boolean isTelemetryEnabled() {
        return TELEMETRY.get();
    }

    public static double energyRateMultiplier() {
        return ENERGY_RATE.get();
    }

    public static double oxygenDrainMultiplier() {
        return OXYGEN_DRAIN.get();
    }

    public static double oxygenCapacityMultiplier() {
        return OXYGEN_CAPACITY.get();
    }

    public static double fuelCostMultiplier() {
        return FUEL_COST.get();
    }

    public static double machineSpeedMultiplier() {
        return MACHINE_SPEED.get();
    }

    /** Global multiplier applied to every resolved gravity factor (default 1.0 = unchanged). */
    public static double gravityMultiplier() {
        return GRAVITY.get();
    }

    /** Configured quarry footprint side, clamped to a practical 4..64 block range. */
    public static int quarryMaxSide() {
        return QUARRY_MAX_SIDE.get();
    }

    /**
     * Base scheduled-tick delay before an orphaned quarry frame block crumbles (each block adds a
     * random spread of up to 7× this base, so the ring decays block by block; default 600 ≈ 30s–4min).
     */
    public static int quarryFrameDecayTicks() {
        return QUARRY_FRAME_DECAY_TICKS.get();
    }

    /** Whether config-gated night raids on alien villages are enabled (default true; opt-out). */
    public static boolean alienRaidsEnabled() {
        return ALIEN_RAIDS.get();
    }

    /** Whether the Terraformer actively force-loads a small window of chunks while running (default false; opt-in). */
    public static boolean terraformerForceLoadEnabled() {
        return TERRAFORMER_FORCE_LOAD.get();
    }

    /**
     * Whether Nerospace registers its NeroLink module (player-scoped data sections, the two safe actions and
     * the live events). Default true. Checked both when the module registers and again on every snapshot,
     * action and publish, so a mid-session {@code /neroland config reload} takes effect immediately.
     */
    public static boolean linkModuleEnabled() {
        return LINK_MODULE.get();
    }

    /**
     * Whether the on-join welcome message (the Star Guide prompt and the feedback link) is sent to a
     * joining player. Default true; set false to join silently. Read on every join, so a mid-session
     * {@code /neroland config reload} takes effect immediately.
     */
    public static boolean welcomeMessageEnabled() {
        return WELCOME_MESSAGE.get();
    }

    /**
     * Inverse-scales a base work interval by the machine-speed multiplier: a higher speed yields a
     * SHORTER interval, clamped to ≥1 tick (so 10× can't produce a zero-tick interval). Mirrors the root
     * {@code Tuning} interval-clamp contract.
     */
    public static int scaleInterval(int baseTicks, double speedMultiplier) {
        return Math.max(1, (int) Math.round(baseTicks / Math.max(0.01D, speedMultiplier)));
    }

    /**
     * Applies a balance multiplier to a base integer rate, clamped to a minimum of 1 so an extreme low
     * multiplier (0.1×) can never zero a rate (mirrors the root {@code Tuning} clamping contract).
     */
    public static int scale(int base, double multiplier) {
        return Math.max(1, (int) Math.round(base * multiplier));
    }

    /**
     * Registers the schema with Neroland Core, which reads {@code nerospace.properties} immediately
     * (creating it with defaults if absent). Idempotent — safe to call once at mod init, before telemetry
     * checks {@link #isTelemetryEnabled()}.
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        ConfigManager.register(SCHEMA);
    }
}
