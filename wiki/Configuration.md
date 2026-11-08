# Configuration

Nerospace's config is small on purpose. The mod keeps its base balance numbers in code. Packs and
servers tune them with **six multipliers**. The other keys are simple switches and limits.

> **Managed by [Neroland Core](Neroland-Core).** The config file is `config/nerospace.properties`.
> Core owns it. Reload it live with `/neroland config reload` (no restart needed). Most keys are
> **server-authoritative**: when you join a server, your game uses the server's values. The one
> exception is `telemetryEnabled`. It is a personal choice on each client, and a server can never
> change it. Existing files migrate in place.
>
> **Only the keys on this page exist.** Older wiki pages and older versions listed many more keys
> (oxygen field, atmosphere, client visuals, terraformer and meteor keys). Those keys are gone. The
> values they used to set are now fixed in code.

## The six multipliers

All six default to `1.0`. Each one can be set from **0.1 to 10.0**. Every scaled value is kept at
1 or more, so an extreme setting never stops a machine or breaks progression.

| Key | What it scales | Base values (at 1.0) |
| --- | --- | --- |
| `energyRateMultiplier` | The FE/t that generators make: Combustion Generator, Passive Generator and Solar Panels. It also scales solar panel buffers. It does **not** change the Fuel Tank or the Grinder buffer. | Combustion 20 FE/t · Passive 8 FE/t · Solar 20 / 100 / 400 FE/t (Tier 1 / 2 / 3) |
| `oxygenDrainMultiplier` | How fast air drains when you are not in a safe zone. Higher = harsher. | Bare lungs 30 air per check · full suit 3 air per check (one check = 10 ticks) · ×4 in an unshielded hazard |
| `oxygenCapacityMultiplier` | How much air the player and suits hold. | No suit 300 · any full suit 900 |
| `fuelCostMultiplier` | Fuel and energy costs: fuel per rocket launch (never more than the tank), oxygen per air at an airlock, and machine energy costs. Also cargo fuel. | Launch: 2,500 mB to another dimension, or 500 mB + 60 mB per 100 blocks in the same dimension · airlock 2 mB per air · Grinder 20 FE/t · O₂ Generator 20 FE per mB |
| `machineSpeedMultiplier` | How fast machines work. Higher = faster (shorter work time). | Grinder 200 ticks per item |
| `gravityMultiplier` | A global scale on every world's [gravity](Planet-Gravity): players, mobs, items, projectiles and falling meteors. Below 1 = floatier, above 1 = heavier. | Station ~0.1× · Glacira ~0.4× · Greenxertz ~0.6× · Cindara ~0.8× · Overworld and terraformed ground 1.0× |

Examples: `oxygenDrainMultiplier = 2.0` makes air last half as long. `machineSpeedMultiplier = 2.0`
makes the grinder finish an item in 100 ticks. `energyRateMultiplier = 0.5` halves generator output.

At `10.0`, a machine's input limit (for example the O₂ Generator's 1,000 FE/t) may become the real
limit before the multiplied rate does. This is on purpose.

## Fixed base values

These numbers are fixed in code. They are listed here so you can plan builds.

| Thing | Value |
| --- | --- |
| Universal Pipe (each layer) | holds 8,000 · moves up to 1,000 FE/t or 1,000 mB/t |
| Universal Pipe (items) | 8 ticks per block · pulls 16 items every 4 ticks |
| Oxygen Generator | makes 4 mB/t · accepts up to 1,000 FE/t · 50,000 FE buffer · 8,000 mB tank |
| Airlock | 90 air per check (every 10 ticks) · radius 4 blocks |
| Rocket fuel tanks | 3,000 / 6,000 / 12,000 / 24,000 mB (Tier 1 / 2 / 3 / 4) |
| Terraformer | up to 48 columns per tick · accepts up to 2,000 FE/t |
| Meteors | about one every 9,000 s · 30 s warning · 200–500 blocks away · up to 4 sites |

## Other keys

### Telemetry

| Key | Default | Meaning |
| --- | --- | --- |
| `telemetryEnabled` | `true` | Anonymous, Nerospace-only crash reports (Sentry, EU servers, POPIA/GDPR-scrubbed — see `PRIVACY.md`). Set `false` to opt out. Applies on config reload. Each player decides this for their own client. |

### Terraformer

| Key | Default | Meaning |
| --- | --- | --- |
| `terraformerForceLoadEnabled` | `false` | A running [Terraformer](Terraformer) keeps a small window of chunks loaded, so it keeps working while you are away. Off by default because it costs server memory and speed. |

### Stations

Settings for [Orbital Stations](Orbital-Stations). The server decides all of them.

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `stations.maxMembers` | `16` | 0–16 | Members a station owner may add. |
| `stations.maxModules` | `8` | 0–8 | Modules a station may have. |
| `stations.minSpacing` | `512` | 128–4,096 | Blocks (on each axis) a station [placed where you stand](Orbital-Stations#placing-your-own-station) must keep from every other station and from the shared platform at 0,0. |
| `stations.maxRadius` | `30000` | 1,024–1,000,000 | How far from 0,0 (on each axis) a station may be placed. |
| `stations.freeDockRadius` | `2048` | 0–8,192 | How close a [free dock](Orbital-Stations#free-docks) must be to where a rocket last stood in the station dimension to appear in its dock list. `0` turns free docks off. |

### Rockets

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `rockets.maxCrew` | `4` | 1–4 | Players one rocket seats. The first aboard is the pilot. `1` gives the old one-seat rocket. Cargo rockets never carry anyone. The server decides. |

### Mars rover

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `rover.capacity` | `40000` | 1,000–1,000,000 | FE a [Mars Rover](Rover)'s battery holds. The server decides. |
| `rover.maxPerPlayer` | `4` | 1–64 | Rovers one player may have out at once. Counted over the rovers in loaded chunks when placing a new one. The server decides. |
| `rover.drainPerTick` | `5` | 0–1,000 | FE a rover uses each tick while its driver holds the throttle. `0` makes driving free. The server decides. |
| `rover.chargeRate` | `200` | 0–100,000 | FE per tick a parked rover draws from Neroland energy blocks next to it. `0` turns charging off. The server decides. |

### NeroLink

| Key | Default | Meaning |
| --- | --- | --- |
| `linkModuleEnabled` | `true` | Share Nerospace data with the NeroLink companion app: your own rockets, stations, planets, life support and Star Guide progress (never another player's). Set `false` and the app shows Nerospace as absent. The server decides. |

### Cargo rockets

Settings for [Cargo Pads](Cargo-Pad) and cargo flights. The server decides all of them.

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `cargoPadSlots` | `18` | 1–27 | Usable hold slots on a Cargo Pad — the payload one cargo rocket carries. |
| `cargoFuelCapacity` | `16000` | 1,000–64,000 | Rocket fuel (mB) a Cargo Pad's pipe-fed buffer holds. |
| `cargoTravelTimeMultiplier` | `1.0` | 0.1–10 | Scales cargo flight time (higher = slower). |
| `cargoHoldRetrySeconds` | `30` | 5–600 | How often a holding flight tries to land again. |
| `cargoHoldTimeoutMinutes` | `30` | 1–1,440 | How long a flight holds before its cargo is dropped as a Cargo Crate at the destination (never deleted). |
| `cargoFlightRetentionDays` | `7` | 1–90 | In-game days a finished flight record is kept. |
| `cargoMaxFlightsPerOwner` | `16` | 1–128 | Live flights one player may have at once. |
| `cargoMaxPads` | `256` | 8–4,096 | Most Cargo Pads that can be registered. |

`fuelCostMultiplier` also scales cargo fuel.

### Economy (optional — needs NeroEconomy)

Credit rewards and fees. They only do something when the **NeroEconomy** mod is installed. Every
amount defaults to `0`, which turns that feature off. The server decides all of them.

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `economyFirstVisitReward` | `0` | 0–1,000,000 | Credits paid once, the first time a player reaches each planet. Paid from NeroEconomy's Reserve and limited by its daily payout limits. A refused reward is not tried again. |
| `economyLaunchFee` | `0` | 0–1,000,000 | Credits charged per crewed or cargo launch, after all other launch checks pass. A player who cannot pay cannot launch (a Cargo Pad shows *Cannot pay the flight fee*). |
| `economyDockingFee` | `0` | 0–1,000,000 | Credits charged per flight to an orbital station, on top of the launch fee. If the launch fee is then refused, the docking fee is given back. |

Fees go to NeroEconomy's Reserve. Without NeroEconomy these keys do nothing.

### Quarry

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `quarryMaxSide` | `64` | 4–64 | Longest side (in blocks) of a quarry's landmark area. |
| `quarryFrameDecayTicks` | `600` | 20–24,000 | Base delay before a left-behind frame block crumbles and drops its casing. Each block waits `base + random(base × 7)` ticks, so with the default the ring falls apart over about 30 s to 4 min. |

### Alien villages

| Key | Default | Meaning |
| --- | --- | --- |
| `alienRaidsEnabled` | `true` | Hostile mobs raid claimed alien villages at night. Set `false` to turn raids off. Applies on config reload. |

### Chat

| Key | Default | Meaning |
| --- | --- | --- |
| `welcomeMessageEnabled` | `true` | Send the two-line Nerospace welcome (the Star Guide Book prompt and the feedback link) when a player joins. Set `false` to join silently. Applies on config reload. The server decides. |

### Sky (client-side)

Each player sets these for themselves; a server never overrides them. They only change what the full planet sky
draws (NeoForge for Minecraft 26.3).

| Key | Default | Meaning |
| --- | --- | --- |
| `sky.aurora` | `true` | Aurora curtains (Glacira, Nyxara's Nightside, Mars after a dust storm, the Greenxertz spore shimmer). `false` hides them. |
| `sky.shootingStars` | `true` | Shooting stars. `false` hides them. |
| `sky.quality` | `full` | `lite` halves the aurora layers and their detail and draws slightly smaller stars, for low-end graphics cards. |

## Removed keys (for modpack authors)

These keys no longer exist. If they are still in your file, they do nothing.

- Folded into the multipliers or fixed in code: `atmosphereDamage`, `oxygenMax`, `oxygenDrainPerTick`,
  `oxygenSuitDrain`, `oxygenSuitT2Max`, `oxygenAirlockRadius`, `oxygenAirlockRefillPerCheck`,
  `oxygenAirlockMbPerAir`, `terraformEnergyPerBlock`, `terraformWorkIntervalTicks`,
  `energyPipeCapacity`, `energyPipeThroughput`, `fluidPipeCapacity`, `fluidPipeThroughput`,
  `gasPipeCapacity`, `gasPipeThroughput`, `itemPipeTicksPerBlock`, `itemPipeExtractAmount`,
  `itemPipeExtractPeriod`, `batteryCapacity`, `fluidTankCapacity`, `gasTankCapacity`,
  `combustionGeneratorFePerTick`, `passiveGeneratorFePerTick`.
- Atmosphere and oxygen field: `atmosphereDamageEnabled`, `atmosphereSafeRadius`,
  `oxygenBubbleRadius`, `oxygenMaxConcentration`, `oxygenBreathableThreshold`,
  `oxygenDiffusionRate`, `oxygenDecayPerStep`, `oxygenSimIntervalTicks`,
  `oxygenMaxActiveCellsPerSource`, `oxygenLeakRange`, `oxygenEvaporateSeconds`, `oxygenSyncRadius`.
- Client visuals: `oxygenVisualQuality`, `oxygenParticleIntensity`, `oxygenHazeIntensity`,
  `oxygenBoundaryIntensity`, `oxygenDebugLog`.
- Terraformer: `terraformPlantsEnabled`, `terraformWaterEnabled`, `terraformWaterMaxDepth`,
  `terraformDriftEnabled`, `terraformDriftPerSecond`, `terraformFaunaEnabled`,
  `terraformResourcesEnabled`, `terraformResourceOres`, `terraformMaxColumnsPerTick`,
  `terraformForceLoadChunks`, `terraformMaxForcedChunks`, `terraformPlantChance`,
  `terraformResourceChance`.
- Meteors: `meteorNaturalSpawn`, `meteorAvgIntervalSeconds`, `meteorWarningSeconds`,
  `meteorMinDistance`, `meteorMaxDistance`, `meteorCraterRadius`, `meteorMaxActiveSites`,
  `meteorLootBonusRolls`, `meteorDebugLog`.
- Template leftovers: `logDirtBlock`, `magicNumber`, `magicNumberIntroduction`, `items`,
  `oxygenSealedRoomMax`.
