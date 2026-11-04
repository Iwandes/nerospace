# Changelog

All notable changes to **Nerospace** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.0-beta.1] - 2026-11-04

The first 2.0 beta combines the planets and stations rework with the subsequent compatibility and visual changes. It is a test build: planet performance and sky rendering still need work, and Nyxara auroras are not currently visible in the NeoForge 26.3 client. Back up worlds before testing.

### Added

- **Richer planet skies** (the NeoForge 26.3 renderer is still being verified). Folding, drifting **aurora curtains** with
  scrolling rays on Glacira (green to teal, with a rose fringe in diamond dust), over Nyxara's Nightside (violet and
  cyan) and, for about a minute after a dust storm clears, faintly over Mars; a pink spore shimmer round the
  Greenxertz horizon during a bloom. Set pieces: Earth on Luna with a blue rim, true phases and city lights on its
  night side; Mars' **blue sunset** and Phobos and Deimos with phases; Cindara's eclipse **corona and diamond ring**;
  Glacira's 22° halo and sun dogs; Nyxara's flare ray fans, a
  crystal-refracted arc in the band and two nebulae; the station's homeworld below to the east, its night side dark
  with city lights that fade as it turns to day, the sun rising through its rim. Everywhere: natural-colour stars
  (smaller, twinkling where there is air), shooting stars, a softer horizon gradient, and weather that fades in and
  out instead of popping. New client settings `sky.aurora`, `sky.shootingStars` and `sky.quality` (`full` / `lite`).

- `/nerospace gallery sky` (operators): holds a world's sky in a showcase state for screenshots. `gallery sky` lists
  every world and its states; `gallery sky <planet> <state>` sets that world's clock to the state's time and holds it,
  and starts its weather at full strength or clears it, for everyone on that world at once; add `here` to be moved to
  a viewing spot on that world; `gallery sky reset [<planet>]` lets the clock run again and clears the weather. Tab
  completion offers only the chosen world's states (for example `earthrise` on Luna, `dust_storm` and `blue_sunset` on
  Mars, `eclipse` on Cindara, `aurora` on Glacira, `galaxy_side` and `flare` on Nyxara, `planet_night` on the station).
- **Nyxara is now a glassy crystal world.** Each region floors with its own crystal over Nyx Stone (stone shows on
  cliffs): sun-bleached **Sunglass Crystal** with **Crazed Crystal** patches and loose **Shard Drift** on the Dayside;
  violet **Twilight Crystal** with **Rose Crystal**, **Crystal Spire** forests (some tipped with a **Nyx Prism**),
  **Nyx Crystal Clusters** and geode hollows with **Budding Nyx Crystal** in the Twilight Band; dark **Smoke Crystal**
  and flat **Mirror Crystal** lakes on the Nightside. Eleven new blocks and the **Crystal Shard**, each with its own
  trick: Sunglass overhead lowers ambient radiation by one, Crazed Crystal shatters under a fall from 3+ blocks (and
  halves the fall damage), Shard Drift cuts bare feet, Twilight Crystal glows with the sky light it soaks up, Rose
  Crystal lights up where you walk, Smoke Crystal silences footsteps and vibrations, Mirror Crystal glows under the
  Nightside sky, Budding Nyx Crystal grows clusters (faster in a flare), a full cluster gives a pickaxe two shards
  (one by hand), spires chime when struck, and a Nyx Prism blazes in a flare and casts a beam that doubles the flare's
  radiation bump. Recipes: 4 Crystal Shards make a Twilight Crystal; 8 shards around a Voidglass make a Nyx Prism; 2
  shards and a glass block make 2 Mirror Crystals. Crystal Shards carry the Neroland Core materials slot highlight.
  Nyxara crystal blocks and clusters have translucent facets; Void Crystal pulses gently and Smoke and Mirror Crystal
  highlights animate. Ore rates, the Twilight Band,
  Duskborn villages and Dawnband flares are unchanged. Italian and Chinese names are provisional.
- **Sleep through a planet's night.** On Luna, Mars, Cindara and Glacira a bed works in the planet's own night; once
  enough players on that world are asleep (the `players_sleeping_percentage` game rule, counted per world) its clock
  jumps to sunrise and the day count goes up by one. On Nyxara you can sleep only on the Nightside (you rest, nothing
  is skipped: the sun never moves); the band and the Dayside refuse. The station never sleeps. Every bed still sets
  your respawn point and none explodes. Greenxertz keeps the Overworld's own night skip. Sleeping never ends planet
  weather, a frozen clock (`/nerospace clock freeze`, `/nerospace gallery sky`) lets you rest without skipping the
  night, and vanilla's `advance_time` rule has no say on a planet. Italian and Chinese messages are provisional.

### Changed

- The world fog on planets now follows the sky: time of day, weather, the eclipse and (on Nyxara) where you stand, on
  every loader and Minecraft version (before, only the 26.3 NeoForge sky followed it); with Night Vision the usual
  brightened fog stays. Ambient particles and weather sounds now ease in and out with the sky.
  `/nerospace gallery sky ... here` faces each set piece.
- `/nerospace clock <planet> set` now always moves a planet's clock forward to the next time it reads that phase
  (setting an earlier phase starts the next day) instead of sometimes back into the same day.
- The wiki now says where open water freezes (Luna, Mars, Glacira, the Nyxara Nightside and the station) and what
  that does to sugar cane farms.

### Compatibility and publishing updates

#### Changed

- Release uploads now target CurseForge and GitHub Releases only.
- Removed automatic project description and gallery uploads.
- Renamed the website overview source to `art/description.md` and refreshed public documentation links.

#### Fixed

- **Minecraft 1.21.x**: the planets and the station now load on every 1.21.x build (their world data is converted to
  each version's format when the mod is built), and the radiation-lining upgrades for oxygen suits now load on 1.21.1
  and 1.21.4.
- Planet skies on Minecraft 1.21.1 to 1.21.10 are simpler, because those versions can't show everything: each planet
  keeps its sky and fog colours and its fixed sun position, but sunrise colours, star brightness and cloud colours are
  the game's defaults (clouds are white on 1.21.1–1.21.5 and disabled on 1.21.8 and later),
  Nyxara's moon sits opposite its sun, and the station's sky uses the game's default colour. On 1.21.11 only the
  faint Greenxertz ambient light tint is missing.

### Planets and stations

The planets and stations update: three new worlds (Luna, Mars and Nyxara), every world rebuilt with its own blocks,
ores, biomes, sky, clock, weather and terraforming, and orbital stations that grow into habitats. See the wiki page
*Upgrading to 2.0* before updating a 1.x world. Nerospace 2.0 also runs on Minecraft 1.21.x.

#### Compatibility

- **The alpha is intended to preserve existing worlds, but migration still needs runtime verification.** Every block, item,
  dimension and saved-data id from 1.6.0 keeps its id; saved stations, pads, rockets, terraformers,
  oxygen fields, routes, Star Guide progress and config files load unchanged. Back up your world before
  upgrading, as always.
- **Requires Neroland Core 1.17.0 or newer.** Client and server must run the same Nerospace version: 2.0 adds
  network messages and Rocket screen data that a 1.x client does not understand.
- **Old terrain is never rewritten.** Chunks already generated on Greenxertz, Cindara and Glacira keep their terrain;
  only newly generated chunks use the new worldgen, so a visible seam between old and new terrain is expected.
  Greenxertz and Glacira keep their 1.x terrain shape so the land lines up across the seam; new Cindara land has its
  own shape.
- **Greenxertz keeps its real day/night cycle.** Its new dimension type is an exact copy of the Overworld's: days
  still last 20 minutes, beds still skip the night, raids still come after dark and mobs still spawn by real
  darkness. Nerosteel and Xertz Quartz generate at the same rate as before, and existing villages, Village Cores,
  oxygen fields and pads carry on unchanged. Hamlets, Ruins and Mega-Cities keep generating where they always did.
- **Rockets:** destination numbers are append-only (Luna, Mars and Nyxara take 5, 6 and 7), every rocket tier keeps
  its 1.x destinations and its 1.x default selection, and a rocket saved mid-flight still lands where it was going.
  Tiers only gain destinations: each tier now also reaches every destination of the tiers below it.
  A rocket saved in 1.6.0 on its way to found a new station is honoured once without a charter.
- **Landing pads** registered in 1.6.0 load as launch pads and learn their centre and front the first time a rocket
  lands on them (their position never changes). Rocket and pad saves only gain optional fields.
- **Stations:** every 1.6.0 station loads as a **Waystation** with no members, no modules and its owner unchanged.
  The original room is recorded as the module "Core (original)" the first time it loads in 2.0, is never rebuilt or
  touched, and gets breathable air if nothing already supplies it. A Waystation has one more route than before
  (9), so every migrated station can hold one extra cargo route. Station saves only gain optional fields.
- **Terraformers** already running on Glacira carry on from their current stage exactly as before. Ground terraformed
  before 2.0 keeps its old biome; only ground a Terraformer reaches from now on takes the new per-world biomes.
  Permafrost only exists in newly generated land.
- **Villagers keep their species:** a villager from 1.6.0 stays Xertz, Ember-kin or Frostbound, and the Xertz trade
  table and reputation tiers are unchanged. **Village Cores no longer store the owner's name:** a claimed core keeps
  its owner, and the display name 1.x saved beside it is dropped the next time the core's chunk saves.
- **Config:** all 24 existing keys keep their names and defaults; new keys are added with safe defaults.
- **New saved data** (`nerospace:planet_clock`, `nerospace:planet_weather`, `nerospace:nyxara_band`) is per planet,
  holds no player data and starts fresh on an upgraded world. Nyxara stores its Twilight Band width with the world, so
  a later config edit never moves the band in an existing world.
- **Erasure covers old and new data alike.** `/neroland data eraseme` (and an operator's `/neroland data erase`)
  still clears everything Nerospace kept in 1.x, and now also removes you from every station's member list and drops
  your launch cooldown. A world saved by 1.6.0 needs nothing done to it.
- **For mod authors:** the public API only grows. `PlanetTraits` keeps its 1.x constructor and accessors and gains
  archetype, temperature band, radiation, day length, sky profile, terraform target, villager culture and colony
  profile; `Hazard` gains `RADIATION` at the end (treat unknown hazard names as "no hazard");
  `NerospacePlanets.all()` keeps the 1.x order and appends the new worlds; `NerospacePlanets.hazardAt(level, pos)`
  gives the hazard at an exact spot. `StationInfo` keeps its 1.x constructor and gains `stationClass()`;
  `routeCapacity()` now follows the class (Waystation 9, Trade 10, Research and Industrial 8).

#### Added

#### Minecraft 1.21.x builds

- **Nerospace now also ships for Minecraft 1.21.x**, next to 26.1.2, 26.2 and 26.3: builds for Minecraft 1.21.1, 1.21.4,
  1.21.5, 1.21.8 (also runs on 1.21.7), 1.21.10 (also runs on 1.21.9) and 1.21.11, each on NeoForge, Forge and Fabric.
  These builds need Java 21 (the 26.x builds keep Java 25). Worlds, items and progression are the same as on 26.x.
- On Minecraft 1.21.x a few optional extras are not available, because the mod or game feature they rely on does
  not exist there:
  - **EMI recipe pages** are not included on 1.21.4 to 1.21.11 (EMI has no build for these Minecraft versions).
    JEI pages work.
  - **Fabric on 1.21.1 to 1.21.5**: the oxygen gauge is shown, but vanilla's air bubbles are not hidden on airless worlds.

#### New worlds

- **Luna** (Tier 1): a grey, airless moon with three regions (Lunar Highlands, Lunar Maria and Crater Field) and a
  day of about 28 Overworld days.
  - **Regolith** keeps your footprints until something disturbs them and puffs when you land on it.
  - **Lunar Basalt**, the most blast-resistant stone in the mod; placed in Luna sunlight it slowly sinters into
    **Polished Lunar Basalt** (also stonecuttable).
  - **Anorthosite**: a 3×3 bed directly under a solar panel adds 25 % output.
  - **Crater Glass**: sparkling translucent glass; a light source touching it shines one level brighter through it.
  - **Ilmenite Ore** → Raw Ilmenite → **Titanium Ingot**; **Helium-3 Pocket** → Helium-3 Canister (mine it with Silk
    Touch or next to a Gas Tank, or half of it leaks).
  - Something else, somewhere in the craters.
- **Mars** (Tier 2): a cold desert world with a 24.6-hour sol and three biomes: Martian Dunes, Martian Canyons and the
  Martian Polar Cap. No open water until you terraform it.
  - **Red Regolith** drifts one block downwind during dust storms (capped per chunk; placed blocks never move).
  - **Martian Sandstone** with four stonecutter **Strata** variants that follow the direction you place them.
  - **Hematite Ore** (drops fly to you with an iron or better tool); Raw Hematite smelts into iron or crafts into
    **Rust Pigment** (and red dye).
  - **Perchlorate Crust** poisons anyone on it without sealed boots and drops Perchlorate Oxidiser.
  - **Dry Ice** freezes adjacent water once and fumes away next to bright light; **Basalt Column** grows into a
    pillar up to four blocks high.
  - **Dust Devil Pits** release **Dust Devils** in dust storms: spinning vortices that toss loose items, never break
    blocks or hurt players, and vanish when the storm ends.
  - **Rover Wrecks** with salvage chests, each watched by a **Salvage Drone** that trades rover parts for Raw
    Hematite (one price list for everyone, limited stock that restocks once a day, no record of who traded), and
    **Derelict Habitats**. Mars has no villagers.
- **Nyxara** (Tier 4, needs Deep Space progress before launch): a tidally locked world with the sun fixed on the
  western horizon and a galaxy in the east. The land is split by longitude into a scorched **Dayside** (west), a
  habitable **Twilight Band** around the landing point and a frozen **Nightside** (east). The band is 2 048 blocks wide
  by default (`nyxara.twilightBandWidth`, 512–16 384).
  - **Nyx Stone**: block light near it fades two levels faster (config `blocks.nyxStoneLightAbsorb`).
  - **Dawnband Stone** glows during a flare and keeps the cold away within four blocks.
  - **Shadow Lichen** creeps over dark Nightside caves and withers in a flare; shear it for Night Fibre.
  - **Void Crystal** glows and bends beams (`#nerospace:beam_refractors`); **Voidglass Ore** → Voidglass →
    **Voidglass Lens**.
  - **Gravity Shard Block**: lifts anything in the column above it into a slow upward drift; breaks into nine Gravity
    Shards.
- **Cindara** gets its own terrain and three biomes: the Cindara Ash Wastes (the original biome), the **Ember Plains**
  (lava pools, about 50 % more Cindrite) and the **Sulfur Vents**.
  - **Ashstone** (mines instantly, drops Ash Dust), **Ember-Cracked Ashstone** (burns without heat-rated boots;
    water quenches it), **Basalt Spire**, **Magma Glass** (glowing glass lava cannot destroy), **Ash Layer**.
  - **Sulfur Crust**, the new ore: 2 to 4 Sulfur; two Sulfur and a coal or charcoal make two Gunpowder.
  - Cindrite keeps its old rate everywhere, and new Cindara land has the usual deep copper and iron ore veins.
- **Glacira** gets its own terrain and three biomes: the Glacira Ice Plains (the original biome), the **Methane
  Shore** and the **Glacira Spires**. Low gravity covers all three.
  - **Permafrost** (only a Terraformer's final stage thaws it), **Packed Cryo-Ice** (never melts), **Aurora Stone**
    (lights up only during Glacira's night), **Frozen Geyser** (launches you about seven blocks up, no fall damage).
  - **Frost Crystal Cluster** grows one stage each time diamond dust ends; drops Frost Shards.
  - **Methane Ice** explodes if a flame comes within two blocks (no block damage by default,
    `blocks.methaneIceBreaksBlocks`); its Methane Crystals burn in the Combustion Generator (2 400 ticks) and make
    torches.
- **Greenxertz** gets its own surface and three biomes: the **Greenxertz Canopy** (the original biome), the
  **Fungal Depths** and the **Spore Marsh**.
  - **Xertz Soil**, **Spore Moss** (spreads over Xertz Soil; makes you queasy without a sealed helmet), **Glowcap
    Fungus** (bone meal grows a **Giant Glowcap**), **Bio-Bark Log** (sleep near a stripped log to heal),
    **Xertz Vine** (Xertz Fibre → string), **Spore Pod** and throwable **Spore Bombs**, **Amber Sap Ore**.
- Luna, Mars, Nyxara and Cindara each have their own terrain shape: their hills no longer line up with the
  Overworld's (or each other's).
- **Planets creative tab:** every planet's blocks and drops, planet by planet (Luna, Mars, Cindara, Glacira,
  Greenxertz, then Nyxara); machines stay in the main tab.

#### Clocks, weather and skies

- **Planet clocks.** Every planet has its own day: Luna about 28 Overworld days, Mars a 24.6-hour sol, Cindara two
  days with an eclipse, Glacira three days with long twilights; Nyxara and the station are fixed. Greenxertz keeps its
  real day. Clocks drive the sky, the HUD and solar panels; they never change block or sky light. Operators:
  `/nerospace clock <planet> [get|set <phase>|freeze|thaw]`; config `clock.enabled`.
- **Planet weather.** Dust storms on Mars (radiation outdoors, dimmer sun, drifting regolith, dust devils), ash fall
  on Cindara (ash layers and ember strikes, at most one near each player every five seconds,
  `weather.emberStrikeIntervalTicks`), diamond dust on Glacira (freezes exposed water, grows frost crystals), spore
  blooms on Greenxertz (nausea outside a full suit or oxygen field) and flares on Nyxara (radiation, glowing Dawnband
  Stone, withering lichen). Luna and the station stay clear. Weather is decided by the server and keeps running on
  loaded planets with nobody there. Operators: `/nerospace weather <planet> [get|start [intensity]|clear]`. Config:
  `weather.enabled`, `weather.frequencyMultiplier`, `weather.durationMultiplier`, `weather.particleBudget` and
  `weather.<planet>.enabled`.
- **Planet skies.** Earth rising over Luna, the butterscotch Martian day with a blue sunset and Phobos racing across
  it, Cindara's red giant and its eclipse, Glacira's pale sun, two moons and auroras, Greenxertz's twin suns, Nyxara's
  pinned sunset on one side and a galaxy on the other, and the homeworld turning below your station. Full skies are
  drawn on NeoForge for Minecraft 26.3; every other version shows each world's sky colours.
- **Particles and ambience.** Dust, ash, spores, diamond dust, embers and nebula motes drift around you under open
  sky and thicken in storms (client settings `particles.*`). Each planet and the station has its own ambient sound,
  with weather loops and a flare chime.

#### Hazards and the Rad Lining

- **Radiation** joins heat and cold. Mars dust storms and all of Nyxara are irradiated. Unprotected, radiation drains
  your suit four times as fast and, from level 2 upwards, hurts you outside a breathable zone (harder on the Nightside
  or under a flare).
- **Rad Lining**: craft it from Regolith, Raw Hematite and a Titanium Ingot, then add it to a Tier 2, Thermal or
  Cryo suit piece at a smithing table with one more Titanium Ingot. The piece keeps its id, tier and heat or cold
  shield; a full set of four lined pieces keeps radiation out. Luna and the station never need it.
- Where several hazards meet you need every matching protection: Nyxara's Nightside is irradiated and freezing (a
  Rad-Lined Cryo Suit, or Rad Lining beside a flaring Dawnband Stone), the Dayside irradiated and hot. Mars is cold
  throughout, but any full Tier 2, Thermal or Cryo suit keeps that cold out; the Martian Polar Cap, Glacira and the
  Nightside are frigid and need the Cryo Suit, as Glacira always has. The HUD warns about the hazard that would hurt
  you next, and each planet card names the suit that world asks for (Luna and the station never ask for the Rad
  Lining).

#### Terraforming, life and villages

- **Every world terraforms its own way**, with its own Rooted, Hydrated and Living biomes, soil, ground cover, ore
  seeds and livestock. Luna grows a **Lunar Meadow** whose edge shimmers; Mars gets a **Red Steppe** and its first
  open water, one spring per chunk; Cindara keeps its savanna; Glacira keeps its tundra (Permafrost thaws at the last
  stage); Greenxertz becomes a **Xertz Garden**; Nyxara grows a **Twilight Grove** that only roots in the Twilight
  Band. The station has no ground, so the Terraformer refuses there.
- **The Terraformer and Terraform Monitor say why** a Terraformer refuses (no ground; outside the Twilight Band), and it
  spends no energy. The Monitor shows what the next stage will plant on this world.
- **New life on terraformed worlds:** the **Moon Hopper** on Luna's meadow, herds of **Dust Striders** on the Red
  Steppe and **Dusk Moths** in Nyxara's grove wherever no lamp lights the ground. Each has a spawn egg and a small
  drop (Regolith, Rust Pigment, Night Fibre), and none is harmed by its home planet.
- **Villager cultures:** the Xertz of Greenxertz (unchanged), the stocky **Ember-kin** of Cindara, the fur-wrapped
  **Frostbound** of Glacira and the tall **Duskborn** of Nyxara's Twilight Band, each with its own trade table,
  favourite gift, building style and glowing markings. A culture sells only the helmet of its world's protective suit
  until you reach its highest trust tier.
- **Settlements on every villager world:** Hamlets and Ruins on Cindara, Glacira and in the Nyxara Twilight Band, built
  in each culture's materials; Mega-Cities on Greenxertz and in the Twilight Band. New chunks only.

#### Stations

- **Station Console.** Right-clicking a Station Core opens a four-tab console: Overview (class, modules, power,
  oxygen, members, routes), Members, Docking and Build. Anyone can look; only the owner, members and operators see the
  member list.
- **Station classes:** Waystation (+1 route), Research (timed progress 25 % faster aboard, unlocks the Observatory),
  Industrial (Solar Wings +50 %, hydroponics at half speed) and Trade (+2 routes, a market node for economy mods). The
  owner may change class once per in-game day.
- **Station members:** the owner adds up to 16 members from the players online; members can use the station (docking,
  module power switches) but not rebuild or rename it, and can leave at any time. Docking can be limited to members; a
  members-only station is not listed to anyone else. Members can send a visitor's rocket home once it has stood
  riderless on the dock for ten minutes; the owner's and members' own rockets are never sent, nothing is sent while a
  visitor is still on the station, and everyone on the station is told when rockets leave.
- **Station modules**, built from the console with materials from your inventory: Habitat Ring, Docking Ring, Solar
  Wing, Hydroponics Bay, Gravity Deck and Observatory, up to eight. A second build only repairs missing blocks, for
  free, and a build never replaces a block you placed. Airtight modules come with their own oxygen.
- **Station blocks:** Hull Plating and Hull Window (airtight, from Luna's titanium), Truss (climbable; sneak-place to
  lay a run of up to eight), Solar Wing (twice a Tier 2 panel, station only), Gravity Plate (normal gravity above it),
  Hydroponics Tray (crops grow under a Hull Window inside oxygen, no water needed) and the Observatory's Telescope
  (previews locked worlds; Void Crystals above it extend the view).
- Server settings `stations.maxMembers` (16) and `stations.maxModules` (8) can lower the limits.
- Operators can manage any station; each operator action is logged with the station's slot and the action only.
- NeroLink: the stations section includes stations you are a member of, with class, modules, docking, pressurised
  modules, power and the member list (owner and members only).
- NeroLink: a public `wiki` section serves the bundled Nerospace wiki pages in the companion app. The pages are the
  same for every player and carry no personal data.

#### Rockets and landing

- **Rockets land centred, every time.** A rocket picks the pad you chose, else the pad it launched from, else a pad
  with the same name as your launch pad, else the nearest pad (within 2 048 blocks), else a **Landing Pod**, and stands
  in the middle of the pad facing its front. A pad with a rocket already on it is skipped. Landings only read the pads
  they could use and the area around the landing spot.
- **Descent animation:** arriving rockets appear 48 blocks up and come down with their engines firing, raising dust
  on touchdown; you stay seated until the rocket has landed. If the pad is broken during the descent, the rocket
  steers to the next spot.
- **Docking Ring:** a 3×3 landing plate that registers itself as a landing spot when placed. Stations prefer it for
  arrivals; any rocket tier can take off from it. New stations are built with one; existing stations keep their pad.
- **Landing Pods check their site:** a flat solid 5×5 with room above, no fluids, leaves, ice or magma, no
  structure, and a distance from other players' stations, private cargo pads, terraform regions other mods register
  and other pads. The
  search widens to 24 blocks (`landing.podSearchRadius`) before building a platform in the air; pods stay at least
  32 blocks apart and an empty one nearby is reused.
- **Launch cooldown:** a rider can launch once every five seconds; a launch inside that window is refused before any
  NeroEconomy launch fee is charged.
- **Rocket screen:** destinations are a scrollable list of planet cards (gravity, hazard and suit, weather, clock and
  the pad you will land on), with a larger card for the selected destination. Worlds still behind Deep Space
  clearance show why they are locked. Select with a click, the mouse wheel or Up/Down/Enter.

#### Progression, quests and integration

- **Fuel Refinery: two new inputs.** A Perchlorate Oxidiser slot adds 15 % to every batch it covers (one oxidiser
  covers four buckets), and a Raw Ilmenite line refines each Raw Ilmenite into a Titanium Ingot plus oxygen, pushed
  into an adjacent Gas Tank or gas pipe. Existing refineries keep their contents and settings.
- **Worlds chapters in the Star Guide**, one per world (Luna, Mars, Cindara, Glacira, Greenxertz, Nyxara): land,
  mine the local ore, build with a block only that world gives you, weather its storm (on Luna, its night) for two
  minutes under open sky, and root your first terraformed ground. Your progress in the earlier chapters is untouched.
- **Quest pack: Worlds Apart.** With NeroQuests installed, a new chapter gives every world its own four-quest line.
- **Server caps** for the new spreading, drifting, growing and exploding blocks: `blocks.sporeMossCap`,
  `blocks.shadowLichenCap`, `blocks.regolithDriftCap`, `blocks.ashLayerCap`, `blocks.frostCrystalCap`,
  `blocks.dawnbandCap`, `blocks.gravityOverrideCap` and `blocks.methaneChainCap` lower (never raise) the per-chunk
  limits.
- **New block tags for datapacks:** `nerospace:airtight`, `nerospace:lightning_safe`, `nerospace:hydration_immune`,
  `nerospace:xertz_rootable`, `nerospace:beam_refractors` and `nerospace:lava_immune`; mods that build beams can use
  the `api.optics.BeamRefraction` helper.
- **For mod authors:** three anonymous threshold channels on Neroland Core (`nerospace:planet_first_visit`,
  `nerospace:weather_survived`, `nerospace:station_module_built`; the scope is a planet or a module kind, never a
  player), a `nerospace:hook/worlds_briefing` quest hook, and player record gates
  `nerospace:worlds/<planet>/weather` and `nerospace:worlds/<planet>/terraform`.
- **Luna, Mars and Nyxara Compasses**: creative-only travel compasses for the new worlds, like the Station,
  Greenxertz, Cindara and Glacira ones (no recipe). The Nyxara Compass sets you down in the twilight band.

#### Changed

- Now requires Neroland Core 1.17.0 or later.
- **Rocket reach is cumulative.** A rocket reaches every destination of its own tier and the tiers below it, so Tier 2
  to 4 rockets fly to Luna and Tier 3 and 4 rockets to Mars. Nyxara still needs Deep Space progress. Each tier's
  pre-selected destination is unchanged.
- **Solar panels on planets follow the planet's day.** A panel peaks at that planet's noon, dips at night and in a
  Mars dust storm, and over a full day gives at least the old constant double rate on every planet (peaks are higher
  to make up for the nights). The station stays a constant double rate. Set `solar.clockDriven=false` to restore the
  old constant output.
- **Founding a station from a rocket now uses a Station Charter**, taken when you arrive (never at launch). Without
  one you dock at the origin platform and get a message; "New Station" is hidden in the dock list while you carry no
  charter.
- Rockets no longer land at the coordinates you launched from on another world; with no pad to use they look for a pod
  site near the planet's arrival area or near your old home pad.
- **Landing Pods now hold the arriving rocket instead of a kit.** The rocket you flew (with its leftover fuel) stands
  on the pod, ready to fly again, rather than being packed into the pod as an item and fuel canisters.
- **Screens and HUD refresh.** The Star Guide has a scrolling chapter list with the Worlds chapters by planet name,
  bonus steps marked with a star, a progress counter and scrollable guide text. The Station Console lines up its rows,
  shows one Build row per module with its cost (missing materials in red) and pad cards on the Docking tab. The Launch
  Controller uses icons for its pads, wall, gantry and hub gauges and `◀ name ▶` destination arrows. Long labels are
  shortened with "…" and shown in full in a tooltip. The HUD adds icons to its status lines, a weather line with an
  intensity bar, a Geiger icon and a planet clock dial that shows sunrise on the right and noon at the top, and names
  the phase for two seconds when it changes, including Cindara's *Eclipse*, Luna's *Earthrise* and Glacira's
  *Twilight* (client options `hud.clockDial`, `hud.clockDialScale`, `hud.weatherLine`).
- **Planet art pass:** new skins for the Ember-kin, Frostbound, Duskborn and the Dust Strider, models for the Moon
  Hopper, Dusk Moth and Salvage Drone, a violet Gravity Shard block, readable Luna footprints, a deeper Dust Devil pit,
  spawn eggs for the new creatures and a Rad Lining icon.
- Gravity, hazards, terraform results, villagers, quarry speed and meteor showers now come from each planet's
  definition; the three original planets and the station behave as before.
- The gallery backdrop dimension and the orbital station no longer count as planet surfaces for meteor material
  weighting.
- A pad you name-tag on a station keeps its name.

#### Fixed

- A rocket no longer lands on top of a rocket parked on a pad whose area had not finished loading: it steers to the
  next spot during the descent, or sets down on a Landing Pod nearby.
- "Send visitor rockets home" no longer strands a visitor: it does nothing while a player who is not the owner, a
  member or an operator is on the station, and tells everyone on the station when rockets are sent.
- Pressing Launch (rocket screen or Launch Controller) while someone else is seated in the rocket no longer launches
  them; only the seated player can launch an occupied rocket.
- On Forge, the jar now embeds Sentry 8.60.0, like the NeoForge and Fabric jars (it embedded 8.45.0).
- On Minecraft 26.3, clicking a destination in the Rocket screen or the Launch Controller works again (left clicks were
  ignored, so the destination never changed).
- The Rocket screen and the Launch Controller now show Nyxara as locked until you have deep-space clearance. Before,
  it looked selectable, the pick was refused with "the rocket kept its course", and the rocket flew to its previous
  destination. When a pick is refused, both screens now give the server's reason.
- In creative mode, Core progression gates no longer block choosing or launching to a destination. Survival,
  adventure and spectator players still need clearance.
- On Minecraft 26.3, Nerospace blocks are now recognised by terrain height checks: rain and snow stop on modded ground,
  mobs spawn at the right height, and ores placed by height generate again.
- On Minecraft 26.3, creatures and alien villagers spawn on Nerospace planets again (26.3 had been ignoring every
  Nerospace biome's spawn list).
- Crafting recipes now unlock in the recipe book the first time a player picks up one of their ingredients.
- The shared origin platform at the orbital station is no longer rebuilt on every arrival, so changes players make to
  it are kept.
- Mega-Cities are now always built whole. A city whose centre fell near a chunk edge lost part of its wall and plaza
  (and logged a "far chunk" error for each lost block); cities are now centred on their chunk. Only newly generated
  cities change.

#### Privacy

- **`/nerospace privacy export`**: any player can see everything Nerospace stores about them (planets reached,
  stations owned and station memberships, Village Cores, Cargo Pads, routes and cargo flights, oxygen, Star Guide
  markers and reputation with loaded Alien Villagers) as JSON shown only to them. Nothing about another player is
  included.
- **Village Cores no longer store the owner's name**; chat shows the owner's name only while they are online
  ("another player" otherwise).
- **Village Cores in unloaded areas are now reached by erasure, even after a restart.** Nerospace keeps a small index
  of where claimed Village Cores are (a new world file, `nerospace:village_cores`), holding each owner only as a salted
  one-way hash (pseudonymised, not anonymous: the server can recognise a known player's hash). When a player is erased,
  a core whose chunk is not loaded becomes a position-only "clear on next load" entry; the core is unclaimed the moment
  it loads and the entry is deleted. Only for an erased player who had visited a planet with villages, the hash is
  kept as an erasure key for 30 days (at most 256 keys), to clear a 1.x core that has not loaded since the update. If
  any of these bounded lists fills up, the server log shows a count-only warning.
- **Alien Villager reputation in unloaded areas is now erased after a restart too.** A villager that loads after its
  chunk sat unloaded through an erasure drops the erased player's reputation as it loads, using that same 30-day
  erasure key; no new data is stored for it.
- Erasure now also removes you from every station's member list and drops your launch cooldown. Station members are
  stored by account id only, never by name, and members of a station can see each other.
- Removing a station with `/nerospace station remove` no longer broadcasts the result to operators, so the server log
  no longer records the player's name next to the station's name.
- `PRIVACY.md` lists every store, including the ones that hold no personal data.

## [1.6.0] - 2026-10-23

### Changed

- **Rockets rebuilt as detailed, animated hulls.** Every tier is a new model: a stepped nose cone,
  plated hull with tier livery and numeral, an engine bell you can see into, raked tail fins, four
  landing legs, a boarding hatch with a ladder, navigation beacons and a strobe. Each tier adds to the
  last — Tier 2 gains twin strap-on boosters and a spinning radar dish, Tier 3 a main bell ringed by
  four verniers, fold-out solar wings, a scanning dish boom and a panoramic canopy, Tier 4 a wide
  five-engine core with four boosters, grid fins, heavy legs and the tallest nose.
- **The cockpit is real.** The window band is cut out of the hull and glazed from the inside with tinted
  glass; behind it is a lit cabin liner with a console, screen, ceiling lamp and wall strips. The rider's
  eyes sit exactly on the window band of their tier, so you look out through the glass in first person
  and onlookers see the lit cockpit (and, while the hatch is open, straight in through the doorway).
- **Launch animations.** On the pad the hatch stands open while nobody is aboard, dishes turn or scan,
  beacons blink and solar wings stand deployed; fuelled hulls vent a little vapour. On launch the hatch
  seals, additive exhaust plumes bloom and flicker under every bell, the hull shudders, a pad cloud rolls
  outward, wings and the dish boom fold in; during the climb the landing legs sweep up along the hull,
  the bells gimbal, grid fins deploy and the boosters cut off and fall away past two-thirds of the ascent.
- Rockets are now lit by the world (hull and glass) with only lamps, beacons and exhaust full-bright, so
  they no longer glow at night. Visual scales are now 1.5 / 1.8 / 2.1 / 2.4 (cargo 1.9); the hull is
  culled on its visual envelope, so a tall rocket no longer vanishes when its small hitbox leaves view.

### Internal

- Rocket geometry, Blockbench files and texture sheets are generated from one spec,
  `tools/rocket_models.py` (also run by `genAssets`); the hand-mirrored 128×128 painter and the
  `RocketT2Model`/`RocketT3Model`/`RocketT4Model` classes are gone, and `model_sync.py` no longer
  tracks the rockets. Client runtime checks: `docs/RUNTIME-VERIFICATION.md` → "Rocket visuals".

## [1.5.7] - 2026-10-20

### Changed

- **Internal: saved-data crash recovery now comes from Neroland Core.** Nerospace's own copy of the
  corrupt-save guard was removed and all of its saved data (meteor events, planet visits, launch pads,
  stations, oxygen fields and contributions, terraformers and terraform overlays) now uses the shared
  guard that ships in Neroland Core 1.15.0, as cargo routes already did. Existing last-known-good
  backup files keep the same names and are still used. The shared guard also catches save files that
  load silently empty or half-read, restoring the backup instead of overwriting it. Nothing changes
  in gameplay.

## [1.5.6] - 2026-10-20

### Fixed

- **Opening the Launch Controller no longer crashes the game** (MC-NEROSPACE-X). In 1.5.5 the build
  screen tried to show the tier of the pad in place before the client had room for it, so opening the
  console crashed with "Rendering screen". The screen now opens normally and shows the built tier.

## [1.5.5] - 2026-10-19

### Changed

- **Hand-built launch pads now say when they're ready.** Placing the block that completes a pad tier
  (a single pad, the last pad of a 3×3, the 16th Station Wall of the ring, or the Launch Gantry of a
  Heavy Launch Complex) shows "✔ Launch pad ready: Tier N — place a rocket" above the hotbar with a
  chime and particles, so it's clear the only step left is to place a rocket. Placements that don't
  complete a tier stay silent.
- **Clearer launch pad report.** Right-clicking a pad with an empty hand now gives three short,
  colour-coded lines: the tier (in bold gold) and block count, a green "✔ Ready — place a rocket" line
  while no rocket is docked, and a muted gray hint for the next tier.
- **The Launch Controller follows pads built by hand.** It now reads the formation in front of it and
  raises its selected tier to match, instead of staying on whatever tier was last picked in its menu.
  Its build screen also shows the tier of the pad in place.

## [1.5.4] - 2026-10-18

### Fixed

- **Breaking a Launch Controller no longer deletes the building materials inside it.** The Launch
  Pads, Station Wall and Launch Gantry blocks loaded into its slots now drop as items when any part of
  the console is broken, alongside the controller itself.
- **Ores drop their raw material again on Minecraft 26.3** (NeoForge, Fabric and Forge). Mining Nerosium
  (and Deepslate Nerosium), Nerosteel, Xertz Quartz, Cindrite or Glacite ore with a normal pickaxe dropped
  the ore block itself, as if the pickaxe had Silk Touch. Minecraft 26.3 changed the loot-table file
  format and silently ignored the old Silk Touch check, so the "drop the ore block" branch always won.
  The 26.3 builds now ship every Nerospace loot table in the new format: ores drop raw materials and gems
  (with Fortune), Silk Touch still gives the ore block, and creature drops and explosion rules work as
  intended. 26.1.2 and 26.2 were not affected.
- **Forge: no more client crash loop from the Meteor Tracker readout** (MC-NEROSPACE-W). When the Forge
  client's item registry no longer held Nerospace's items (for example after joining a server that does
  not run Nerospace), the Meteor Tracker check threw on every client tick. Registry entries can now be
  checked for presence on every loader, and the readout simply stays quiet while the item is absent.
- **An older JEI no longer crashes the recipe screen** (MC-NEROSPACE-V, follow-up to MC-NEROSPACE-T).
  The Nerosium Grinder, Fuel Refinery and Combustion Generator pages now show without their energy/time
  readout, instead of crashing, if the installed JEI lacks a widget method this build uses. Each jar also
  declares the oldest JEI it supports for its Minecraft line (26.1.2: 29.5.0.24, 26.2: 30.0.0.1,
  26.3: 31.0.0.3). JEI is still optional; only a JEI older than that is refused at launch.
- The Tier 2 and Tier 3 Solar Panels now need an iron pickaxe or better to drop, the same as the
  basic Solar Panel.

## [1.5.2] - 2026-10-18

### Fixed

- **Every silk-touched ore can be smelted.** Nerosteel, Xertz Quartz, Cindrite and Glacite ore blocks now
  smelt and blast into their ingot or gem, like Nerosium ore already did. Before, only the Nerosium
  Grinder could process them.
- **Nerosteel, Cindrite and Glacite blocks can be uncrafted** back into 9 ingots or gems, matching the
  Nerosium blocks.
- **The Star Guide Book and Star Guide pedestal accept a Nerosium Ore block** (stone or deepslate) as
  well as Raw Nerosium, so a player holding only ore blocks is never locked out of the guide.

### Documentation

- New wiki pages **Getting Started** (a plain step-by-step progression guide) and **Powering Machines**.
- Wiki corrected against the code: machines need a generator (the Nerosium Grinder never self-charged),
  generator, oxygen, pipe and solar numbers, launch fuel costs, launch-pad sizes per rocket tier, and
  configuration keys that no longer exist.

## [1.5.1] - 2026-10-17

### Fixed

- **Nerosium ore smelts again.** Silk-Touched `nerosium_ore` and `deepslate_nerosium_ore` (including
  blocks mined by a quarry fitted with a Silk Touch module) had no furnace or blast-furnace recipe, so
  they could not be turned into Nerosium ingots. The recipes were lost when the sources moved into the
  `common` module and are now restored.
- **Nerosium storage blocks can be uncrafted.** A Nerosium block crafts back into 9 Nerosium ingots and a
  block of raw Nerosium back into 9 raw Nerosium; both reverse recipes were lost in the same move.
- Nerosium ore is tagged `c:ores/nerosium` (item and block) like the other Nerospace ores, so mods that
  look ores up by common tag can find it.

## [1.5.0] - 2026-10-11

Quest-book content and the small progression signals behind it. Requires **Neroland Core 1.15.0**.
Nothing here depends on NeroQuests: the quest pack is plain data inside the jar and simply stays
dormant when NeroQuests is not installed.

### Added

- **Quest pack.** Ships a NeroQuests quest pack — chapter `nerospace:starfarers_log` "Starfarer's Log",
  19 quests — that appears in the quest book when NeroQuests is installed; inert otherwise, no
  dependency. The chapter walks the whole arc from the first Nerosium ingot through orbit, the planets,
  colonies, freight and terraforming, including one shared "world log" quest for a hundred deliveries.
- **Record gates.** Three player-scope Neroland Core gates — `nerospace:first_freight`,
  `nerospace:terraformed_ground` and `nerospace:living_world` — are opened beside the matching Star
  Guide advancements. `first_freight` uses Core's offline-capable overload, so an owner who logged off
  while their flight was in the air still gets the milestone. They have no `requires` and gate nothing.
- **World freight log.** The cargo-route store keeps one world-wide count of delivered flights (a plain
  number, tied to no pad or player) and publishes a `nerospace:cargo_delivered` threshold crossing on
  Core's bus at 1, 10, 100 and 1000 deliveries. Its scope is the destination dimension id only.
- **Hook gates.** Two player-scope record gates other mods may open to hand a player an effect:
  `nerospace:hook/reveal_star_guide_page` sends the player a hint naming their next Star Guide step
  (nothing is awarded), and `nerospace:hook/oxygen_top_up` refills the personal / suit tank to its normal
  maximum once. Each is applied on the server thread, or on the player's next login if they were
  offline, and the gate is closed again so it can be re-armed.

### Changed

- Neroland Core floor raised to 1.15.0 (the gate and threshold APIs above).

### Fixed

- The rocket screen's pad-destination button had two conflicting `gui.nerospace.rocket.pad` lang entries;
  it now reads `Land: <pad name>` as the screen intends.

Privacy: no new per-player data is stored by Nerospace — player-scope gates live in Neroland Core's gate
store, which Core's privacy notice covers — and no UUID is logged; see [PRIVACY.md](PRIVACY.md).

## [1.4.0] - 2026-10-11

Optional economy hooks for servers running **NeroEconomy** 0.3.0-beta.1 or later. Everything is off by
default: with NeroEconomy absent, or with the new amounts left at `0`, Nerospace behaves exactly as in
1.3.1.

### Added

- **First-visit reward** (`economyFirstVisitReward`). A player's first arrival at each planet can pay a
  one-off credit bonus from NeroEconomy's Reserve, within its daily payout limits. A refused reward
  (Reserve short, budget spent) tells the player why and is not retried.
- **Launch fee** (`economyLaunchFee`). Charged to the pilot of a crewed rocket or the requester of a cargo
  flight once every other launch check has passed; a player who cannot pay is refused the launch.
- **Docking fee** (`economyDockingFee`). Charged on top of the launch fee for flights bound for an orbital
  station. If the launch fee is refused afterwards, the docking fee is handed back.
- **`FlightRequestResult.Denial.CANNOT_PAY`**, appended to the public route API (existing ordinals are
  unchanged). The Cargo Pad shows it as *Cannot pay the flight fee*.

### Changed

- NeroEconomy is declared as an optional dependency (Fabric `suggests`, NeoForge `optional`, Forge
  `mandatory = false`) and compiled against `compileOnly`; it is never bundled or required.
- The planet-visit store keeps one world-wide counter of first visits (no player data), used to give
  each reward a unique, non-identifying request key. Older saves load unchanged.

Privacy: no new per-player data is stored, and no UUID is logged or put in a request key — see
[PRIVACY.md](PRIVACY.md).

## [1.3.1] - 2026-10-01

### Fixed

- **Each jar now refuses to load on the next Minecraft line.** The NeoForge, Forge and Fabric metadata
  declared only a minimum Minecraft version, so a jar built for one line (e.g. 26.2) would load on the
  next (26.3) and crash at runtime on a changed method signature. Every build now caps its range at the
  next minor line (26.1.2 → `<26.2`, 26.2 → `<26.3`, 26.3 → `<26.4`), so the loader shows a clear
  version mismatch instead.

### Changed

- **Recovered saved data no longer reports as a crash** (MC-NEROSPACE-S). When a corrupt `.dat` is
  recovered from backup or restarted fresh, the handled report is now sent before the error is logged,
  so the handled event wins the per-session de-duplication instead of an unhandled duplicate.
- **Registration failures on NeoForge and Forge are reported at their source** (MC-NEROSPACE-R / -8).
  A block or item whose constructor throws during registration is now captured with its real exception
  before being rethrown, instead of surfacing only as the follow-on "Trying to access unbound value"
  errors that named the victim rather than the cause. Loading fails exactly as before.

Crash reports stay anonymous and opt-out (`telemetryEnabled = false`); see [PRIVACY.md](PRIVACY.md).

## [1.3.0] - 2026-09-27

Cargo rockets and Cargo Pads, with a public route API for NeroLogistics.

### Added

- **Cargo rockets and Cargo Pads** (`docs/CARGO-ROCKETS.md`). A **Cargo Pad** is a launch-pad plate with
  a 27-slot hold (18 usable by default), a pipe-fed rocket-fuel buffer and a route selector; it is a cell
  of an ordinary 3×3 pad, so the existing tier validation, Fuel Tank pumping and Launch Controller layout
  all apply unchanged. An uncrewed **Cargo Rocket** (new Cargo Hull intermediate; recipes on the Greenxertz
  ladder behind the Tier 2 rocket) carries the hold to another Cargo Pad or a founded station in any
  Nerospace dimension. Flights are server-side timers derived from the planet registry (time from the
  gravity of both ends and whether a dimension boundary is crossed; fuel from payload mass and origin
  gravity — one class, unit-tested), consume rocket fuel, survive a server restart, never load a chunk to
  deliver, hold and retry when the destination is missing or occupied, and after a timeout drop the
  remaining manifest as a **Cargo Crate** item rather than losing it. Schedules: manual, launch-when-full,
  launch every N minutes; per-route **Return empty** flies the hull home. Universal Pipes and their filters
  work on every face of the pad through Core's side configuration.
- **Public route API** `za.co.neroland.nerospace.api.route` (semver-stable, additive; the existing
  `NerospaceRoutes` facade is untouched): `RouteApi` (pads visible to a player, quotes, flight requests,
  flight queries, cancel), `PadHandle`/`RouteHandle`/`FlightHandle` snapshots, `FlightState`,
  `ScheduleMode`, `RouteQuote`, `FlightRequest`/`FlightRequestResult`, and the `RouteEvents` bus
  (`PadRegistered`, `PadUnregistered`, `FlightDeparted`, `FlightArrived`, `FlightHeld`, `FlightDropped`).
  Shaped for NeroLogistics' `RouteProvider`/`ShipmentManager` seams — see
  `docs/NEROLOGISTICS-HANDOVER.md`.
- **Star Guide chapter "Freight"** (Cargo Pad → Cargo Rocket → first unattended delivery, code-granted).
- NeroLink module section **`route`**: the requester's own Cargo Pads and cargo flights, by owner UUID only.
- Config (Core-managed, server-authoritative): `cargoPadSlots`, `cargoFuelCapacity`,
  `cargoTravelTimeMultiplier`, `cargoHoldRetrySeconds`, `cargoHoldTimeoutMinutes`,
  `cargoFlightRetentionDays`, `cargoMaxFlightsPerOwner`, `cargoMaxPads`.
- `en_za` language file (the new strings; everything else falls back to `en_us`).
- `/nerospace gallery` gains a **Freight** exhibit north of the rocket row: two routed Cargo Pads on 3×3
  footprints, pad A with a fuelled Cargo Rocket, a loaded hold, a Fuel Tank and a rocket-fuel pipe into
  its buffer, pad B with an extraction pipe into a chest — open A and press Launch.

### Privacy

- One new store, `nerospace:cargo_routes`, guarded by Neroland Core's `SavedDataRecovery` (Nerospace is
  now 6/6) and registered with `PlayerDataErasure`: erasure removes the player's routes, anonymises their
  pads and flights, drops them from pad access lists and rewrites the backup at once; in-flight cargo is
  delivered or crated, never deleted. Completed flight records are pruned after `cargoFlightRetentionDays`
  in-game days. Verified with Core's `ErasureConformance` harness in the unit tests. Logs and telemetry
  breadcrumbs carry flight ids only. `PRIVACY.md` lists the store.

## [1.2.0] - 2026-09-24

EMI support for the Nerospace machine pages, and its JEI pages now show on Fabric.

### Added

- **EMI support.** The Nerosium Grinder, Fuel Refinery and Combustion Generator pages that JEI already
  had now come from a native EMI plugin too. Official EMI has no Minecraft 26.x release, so this targets
  the community EMI Unofficial Port (Unstable) on NeoForge and Fabric; that port has no Forge build. With
  JEI and EMI both installed, the pages show once. EMI stays optional.

### Fixed

- **JEI pages now appear on Fabric.** JEI on Fabric only loads plugins listed under the `jei_mod_plugin`
  entrypoint and ignores the `@JeiPlugin` annotation, so Nerospace's grinding, refining and combustion
  fuel pages never showed there. NeoForge was not affected.

## [1.1.1] - 2026-09-24

Gas interop with NeroTech through the Universal Pipe, plus three crash-report fixes. Pair it with
NeroTech 0.2.1-beta.1 so both mods use the same oxygen.

### Fixed

- **Universal Pipe gas layer now works with NeroTech and every Neroland Core gas** (Neroland/nerotech#9).
  The gas buffer is Neroland Core's id-keyed store instead of Nerospace's oxygen-only enum, so a line
  can carry hydrogen and any other Neroland gas. The pipe now finds a neighbour's gas storage through
  Core's shared `nerolandcore:gas` capability first, then through Nerospace's own `nerospace:gas`.
  NeroTech's Electrolyzer, Chemical Processor and Gas Turbine now connect and exchange gas, and a Core
  Gas Tank holding a gas other than oxygen no longer looks empty to the pipe. Per-face side-config gating
  on Nerospace machines is unchanged, because those machines are only registered on `nerospace:gas`.
- The pipe is now also exposed on `nerolandcore:gas` on NeoForge, Forge and Fabric, so NeroTech machines
  can push into it and pull from it directly. The existing `nerospace:gas` exposure is kept.
- One oxygen across the ecosystem: `nerotech:oxygen` (NeroTech's old id) is read as `nerospace:oxygen`
  by the pipe and by Nerospace's oxygen consumers, so oxygen that NeroTech stored in a Core Gas Tank
  feeds airlocks and the Launch Controller. A tank that still holds the old id keeps accepting oxygen.
- Gas stream colour follows the carried gas: cyan for oxygen, pale blue for hydrogen, grey for
  anything else. The empty-hand pipe readout names the gas.
- Existing pipes keep their gas: the old `Gas` save value (`"oxygen"`/`"empty"`) is migrated to the
  full id on load.
- **Gravity no longer loads chunks from the server tick.** The terraformed-ground check read the chunk
  with a blocking load. For an entity on a chunk edge that nested load ran other chunk work mid-tick and,
  with C2ME, crashed the entity-section manager (`ConcurrentModificationException`). Gravity now reads
  only already-loaded chunks and skips entities whose chunk is not ready yet.
- **JEI Fuel Refinery page no longer fails on NeoForge.** When JEI starts before the rocket fuel's
  registry data is ready, the output slot shows the Rocket Fuel Bucket instead of throwing
  "Components not bound yet" and dropping the whole category.
- **Crash reporting ignores errors Nerospace did not cause:** exceptions thrown inside another mod's own
  code (for example a recipe viewer building a Nerospace mob while its own config is not loaded), and
  vanilla's "Invalid block entity" message for saved data whose block was replaced. Nothing else changes
  about what is sent.

## [1.1.0] - 2026-09-20

Minecraft **26.3** support, plus one advancement fix.

### Added

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.

### Changed

- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- Requires **Neroland Core 1.13.0** (was `1.10.0`) — the first Core release with a 26.3
  build. The loader range still derives from the pin (`[${nerolandcore_version},2.0)`).
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement. The `compat/jei` plugin compiles unchanged against all three.
- Now ships the `nerolandcore:battery`, `nerolandcore:fluid_tank`, `nerolandcore:gas_tank` and `nerolandcore:item_store` recipes (moved here from Neroland Core, ids unchanged). They are keyed on `c:ingots/nerosium` and `c:ingots/nerosteel`, which Nerospace owns — keeping them in Core made a Core-without-Nerospace world fail to load on 26.3, where recipes are a datapack registry and a missing ingredient tag is fatal.

### 26.3 port notes

- Block classes build their codecs through Core's `BlockCodecs` (26.3 removed block-type codecs); `codec()` is kept without `@Override` so one source compiles on every version.
- 26.3 API differences are handled with Stonecutter blocks: `PoseStack#rotate` (was `mulPose`), the new `Prediction` argument on `drop` / `placeItemBackInInventory`, `setPermanentlyInvulnerable`, and similar renames.
- Worldgen: Hamlet, Ruin and Mega-City implement 26.3's data-driven `Feature` interface; their codecs are registered in `FEATURE_TYPE`, and the ore and structure features ship as `worldgen/feature/*.json` in the `resources-26.3` overlay. The `configured_feature` JSON stays in place for 26.1.2 / 26.2.
- Falling meteors and rockets use `LinearInterpolationHandler` on 26.3, where `Entity` owns interpolation.
- Fixed: the *New Life* guide advancement never loaded, because its entity predicate used the legacy `type` key. It now uses `minecraft:entity_type`, with a 26.3 variant in the overlay.
- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.

## [1.0.4] - 2026-08-31

### Added

- **`welcomeMessageEnabled` config key** (default `true`). Set it to `false` in
  `config/nerospace.properties` to suppress the two-line welcome — the Star Guide Book prompt and the
  feedback link — that Nerospace posts in chat when a player joins the world. Server-authoritative and
  re-read on every join, so `/neroland config reload` applies it without a restart.

## [1.0.3] - 2026-08-30

Additive patch: a new public API package, no breaking change to any existing type.

### Added

**Cross-mod fluid and item interoperability**

- Nerospace now speaks the **platform-standard fluid and item handlers** on all three loaders, both as
  a provider and as a consumer: `Capabilities.Fluid.BLOCK` / `Capabilities.Item.BLOCK` on NeoForge,
  `FluidStorage.SIDED` / `ItemStorage.SIDED` (Fabric Transfer API v1) on Fabric, and
  `ForgeCapabilities.FLUID_HANDLER` / `ForgeCapabilities.ITEM_HANDLER` on Forge. Until now the
  Universal Pipe and every Nerospace tank and machine spoke only Nerospace's own mod-private
  capabilities plus vanilla `Container`, so a pipe simply refused to grow an arm toward another mod's
  tank or machine — which made the Fluid Tank hard to use as anything but a Nerospace-only endpoint.
- In play: a **Universal Pipe** now connects to, pulls from and pushes into another mod's fluid tanks,
  item machines and pipes, and **another mod's pipes can pull fluid or items straight out of** a Fluid
  Tank, Fuel Tank, Fuel Refinery, Quarry Controller, Launch Controller or Universal Pipe. Every block
  that already offered fluid on Nerospace's own capability now offers it on the standard one as well;
  the mod-private path is still tried first, so Nerospace-to-Nerospace behaviour is unchanged.
- **Per-face I/O modes and filters apply to foreign neighbours** exactly as they do to Nerospace ones —
  a face disabled in the side config looks shut to another mod's pipe too. This adds reach without
  giving up any control over what moves where.
- **Gas is deliberately not bridged.** There is no cross-mod standard for it, and Nerospace's oxygen is
  not a fluid, so the gas layer stays on Nerospace's own capability and Neroland Core's. Nothing about
  it changes.
- Worth stating plainly: this makes Nerospace speak the standard APIs. Whether any *particular* other
  mod interoperates still depends on that mod using them too.
- **One deliberate exception: the Rocket Launch Pad.** The pad's fluid connection is a one-way sink
  into a docked rocket — it can be filled but never drained — and the standard handlers are
  transactional, meaning another mod may insert and then roll back as a routine way of asking "how
  much would you take?". A sink cannot honour that rollback, so the pad is reachable through
  Nerospace's own pipes only. Fuel a rocket from another mod by piping into a **Fuel Tank** beside the
  pad, which auto-fuels as before.
- New platform seam `za.co.neroland.nerospace.platform.ItemLookup` alongside the existing
  `EnergyLookup` / `FluidLookup` / `GasLookup`, with exactly one implementation and a
  `META-INF/services` entry per loader cell. `FluidLookup` now falls back to the platform-standard
  handler when a neighbour does not answer Nerospace's own.

**Evaporator Module (`nerospace:evaporator_module`)**

- A new upgrade-module card for the Quarry Controller's module slot. With one installed the quarry
  **destroys liquids on sight** instead of buffering them at all, so its fluid tank stays empty and
  there is nothing to pipe away. Use it for a dig you know will hit water or lava and whose fluid you
  do not want; leave the slot for another card when you would rather keep what it collects.
- Crafted with the same shape and cost as the other module cards — Nerosteel Ingots and Redstone around
  a signature centre item, here **Blaze Powder**.
- Its texture is placeholder art for now.

### Changed

**The quarry no longer stops for liquid**

- The Quarry Controller's fluid handling is now best-effort instead of all-or-nothing: it buffers what
  fits in its **16,000 mB** tank and **destroys the rest** as it digs, so a dig that reaches an ocean or
  a lava lake keeps going rather than waiting for somewhere to put the fluid.
- It clears at most **64 source blocks per mining tick**, so a large body of water drains steadily over
  a few seconds instead of disappearing in one spike — and a single tick's work stays bounded.
- Buffered fluid still auto-ejects into any adjacent fluid store, which — per the interop change above
  — now includes other mods' tanks and pipes.
- The `fluid_full` pause reason is retired: nothing raises it any more. The **output buffer** still
  pauses the dig when it cannot fit a drop, so mined *items* are never voided.

### Fixed

**A quarry over water used to stall for good**

- The Quarry Controller paused with reason `fluid_full` the moment its 16,000 mB buffer filled — which
  is **16 source blocks** — or the moment it met a second fluid type, and it resumed only if something
  drained the tank. A dig that reached a lake or an aquifer therefore stopped dead and stayed stopped
  with everything else about it healthy. It now never stalls on liquid (see above).

**Public integration API (`za.co.neroland.nerospace.api`)**

- `NerospaceEnvironment.at(level, pos)` returns an immutable `EnvironmentSnapshot` — atmosphere,
  hazard, gravity, oxygen (0-15), terraforming stage (0-3) and breathability — so another mod can ask
  what a position is like without touching Nerospace's internal managers. It fails closed to a vacuum
  snapshot on unloaded chunks and never forces a chunk to load or generate.
- `NerospaceVisits` + `PlanetVisitEvents` answer "has this player ever reached Cindara?" and fire once
  on a genuine first visit. This is the half no amount of live event listening can reconstruct for a
  mod installed after the fact.
- `NerospaceOxygen` accepts bounded, expiring external oxygen contributions (max radius 64, strength
  15, one hour), keyed by a caller-owned source id, so another mod's life support can feed Nerospace's
  breathability and environment answers.
- `NerospaceTerraforming` applies and rolls back reversible regional overlays that change what
  Nerospace *reports* without rewriting a single block. **Denies every mutation until the server
  installs a `TerraformClaimPolicy`** — an unauthenticated regional environment override would be a
  griefing tool. Each region records the physical chunk stage underneath it, so a rollback restores
  that baseline rather than erasing real progress.

### Changed

- Breathability now also honours an active terraforming overlay and external oxygen contributions,
  alongside the existing terraformed-chunk flag, oxygen field and launch-pad safe radius.

### Privacy

- Planet visits are the only new player-keyed store: a UUID and a set of planet ids, with no
  timestamps, coordinates or movement trail. It is registered with Neroland Core's shared
  `PlayerDataErasure` hook and, like station ownership, an erasure request is pushed into the
  saved-data backup file immediately rather than at the next periodic pass.
- Oxygen contributions and terraforming overlays record no player at all. Their ids are caller-owned
  and are documented as never permitted to encode player identity, because Nerospace persists them
  verbatim.

## [1.0.2] - 2026-08-10

### Added

**NeroLink companion module (`za.co.neroland.nerospace.link`)**

- Nerospace now registers a module with Neroland Core's link registry, so the NeroLink companion
  bridge serves it instead of reporting `nerospace: absent`. Schema v1.
- Five read-only sections: `rockets` (tier, fuel, oxygen, power, launch readiness + nearby launch-pad
  tiers), `stations` (the stations **you** own, plus registry capacity as plain counts), `planets`
  (gravity / airless / hazard, plus which worlds you have reached), `life_support` (your oxygen tank,
  suit tier and hazard shield) and `star_guide` (your chapter/step progress and next step).
- Two deliberately conservative actions: `rename_station` (owner-gated, online-required, runs the same
  server-side path as the in-game naming console) and `acknowledge_alert`. **Nothing** in the module
  can launch a rocket, move a player or edit the world from a phone.
- Three live events: `oxygen_low` and `rocket_landed` (owner-scoped), and a deliberately anaemic
  `station_state` broadcast carrying only a slot number, a dimension and a state word.
- New config key `linkModuleEnabled` (default `true`, server-authoritative) switches the whole module
  off; it is re-checked on every snapshot, action and publish, so `/neroland config reload` applies
  immediately.
- **Privacy:** every section is filtered by the requesting player's UUID. No other player's station,
  oxygen value or Star Guide step can appear in a response; ownership of other players is never
  emitted, not even as a name. Operator status is deliberately not honoured. Rockets and launch pads
  have no owner in Nerospace, so that section reports only what you could already see from where you
  stand (a 128-block radius around you, while online) rather than a server-wide roster.

**Per-loader Maven artifacts published to GitHub Packages**

- The release workflow now publishes all six `nerospace-<loader>-<mc>` artifacts after the build, so
  sibling Nero mods can compile against `za.co.neroland.nerospace.api` from a remote repository
  instead of reaching Nerospace by reflection.

**Marker-less quarry setup (frame outline)**

- The quarry area can now be defined **without landmarks**: outline a closed rectangle with
  hand-placed **Frame Casing** (it is now a placeable block item that places `quarry_frame`)
  and put the Quarry Controller beside it — the controller detects the complete perimeter
  (same plane, same size limits as the landmark path) and starts mining inside it immediately.
  The landmark flow is unchanged.
- The GUI now shows *why* the quarry is paused ("Paused — frame incomplete / out of power /
  output buffer full / fluid buffer full / out of Frame Casing / wrong planet").

**Public cargo-rocket route API (`za.co.neroland.nerospace.api`)**

- New semver-stable route surface for logistics consumers (NeroLogistics' `RouteProvider` seam):
  - `NerospaceRoutes` — static lookup facade: `endpoints()` (Home + Orbital Station + the three
    moons, stable order), `endpoint`/`isEndpoint`, `route(from, to)`, `routesFrom(from)`, and
    `isOpen(...)` liveness checks against loaded dimensions.
  - `RouteEndpoint` — immutable endpoint record (dimension key, display name, optional `PlanetId`).
  - `CargoRoute` — immutable directed route record carrying the minimum rocket tier (1–4), the
    per-launch fuel cost in millibuckets (the same cross-dimension launch cost a crewed rocket
    pays, `fuelCost`-config-scaled at query time), and a canonical transit duration in ticks
    (`TRANSIT_TICKS_PER_STEP` per step of separation in the destination order).
- Read-only and player-data-free; the internals (`rocket.Destinations`, `rocket.RocketTravel`,
  `rocket.RocketTier`) remain internal.

**Meteor Tracker is now craftable (survival)**

- New shaped recipe: a vanilla **Compass** with **Nerosteel Ingots** above and below and
  **Redstone Dust** at the sides. The tracker was previously creative-only. (The four
  destination compasses remain creative-only pending a rework.)

**Optional Energized Power interop declared**

- All three loader manifests now declare **Energized Power** as an optional/suggested
  dependency with `AFTER` load ordering, so its capabilities and `c:` tags are ready
  before Nerospace loads when it is installed. Nerospace stays fully independent of it.

### Fixed

**A refused GUI no longer reports as a successful one (MC-NEROSPACE-J)**

- The `MenuOpener` guard already stopped a Bukkit/Paper hybrid from taking the server thread down when
  a plugin choked on a modded item during `InventoryOpenEvent` — but 21 of its 22 call sites discarded
  the result and returned `InteractionResult.SUCCESS` regardless. They now return the new
  `MenuOpener.openOrConsume`, which reports `CONSUME` on a refusal. `FAIL` would be wrong: it does not
  consume the action, so the interaction would fall through to the held item and the player would place
  a block against the machine they were trying to open.
- An **Alien Villager whose trade screen was refused stayed locked forever.** `startTrading` set the
  trading player before opening, and nothing cleared it when the open failed — so the villager was
  flagged as busy with a player who had no window, and neither they nor anyone else could trade with it
  again. It is now released on refusal.
- Handled-failure telemetry was applying its `handled` / `source` tags through a scope that never
  reached the event, so every report arrived unlabelled, at error level, in its own new issue. The tags
  now land, the level is `WARNING` (a guard caught it; the server carried on), and reports are
  fingerprinted by guard **and** operation so hybrid noise collapses into one issue without swallowing
  a genuine bug in one of our own menus.
- **Privacy:** the guard's log line and telemetry payload identified the menu by its *rendered* title,
  which for a renamed Alien Villager is text a player typed on a name tag. It now sends the translation
  key or the provider class name, never player-authored text.

**Right-to-erasure now reaches Alien Villager reputation (POPIA/GDPR)**

- Each Alien Villager keeps a `UUID -> reputation` map in its own entity NBT — an undeclared **fourth**
  per-player store that Nerospace's Core erasure hook never touched, so an erased player's UUID could
  sit in villager NBT indefinitely. The eraser (now `data.NerospaceErasure`) sweeps every **loaded**
  level and drops the entry, and a villager whose chunk loads later in the same server session has the
  entry stripped as it reads its NBT. The residual gap — a villager in a chunk never visited again
  after a server restart — is documented in code rather than papered over; deliberately no persistent
  suppression list, because storing the very UUID we were asked to forget, forever, would be worse.
- The eraser's comment claiming an offline player's attachment data "resets to defaults on its own"
  was **wrong**: both `oxygen` and `star_guide_seen` are declared `serialize(...)` with `copyOnDeath()`
  and persist in the player's save file. The comment now states the real limitation, and an erasure
  that lands while a player is offline is completed the next time they log in during the same session.

**Machine blocks now drop themselves when mined**

- Several blocks used `requiresCorrectToolForDrops` but were missing from the
  `minecraft:mineable/pickaxe` tag, so **no tool ever counted as correct and they dropped
  nothing**: Fuel Tank, Fuel Refinery, Oxygen Generator, Terraformer, Hydration Module,
  Terraform Monitor, Star Guide, Solar Panel (all tiers), Quarry Controller, Rocket Launch
  Pad, Launch Gantry, Village Core (plus Meteor Core, Launch Controller and its filler for
  mining-speed parity). All are now pickaxe-mineable; loot-table-dropping ones also need an
  iron tool, matching the other machines. Solar Panel T2/T3 keep their one-item multiblock
  drop from code (their loot tables are now empty to prevent a duplicate drop).

### Changed

**Neroland Core dependency floor raised to 1.10.0**

- `nerolandcore_version` moves from `1.4.0` to `1.10.0` (six minors of drift). Core's V1 API is frozen
  and every minor in that range is additive-only, so no Nerospace code changed. The loader dependency
  ranges are derived from this single property — `[1.10.0,2.0)` for NeoForge/Forge and
  `>=1.10.0 <2.0.0` for Fabric — so an out-of-date Core is now refused at load time instead of
  crashing later at registration.

**Quarry frame lifecycle**

- **Breaking a frame block now drops a Frame Casing**, and the quarry notices the broken ring:
  it rebuilds the gap from its casing stock, or pauses with "frame incomplete" until the
  player patches the ring by hand or inserts casings into the frame slots.
- **A finished dig dismantles its frame**: the standing frame blocks are removed and their
  casings returned to the controller's frame slots (spilled at the controller if the slots
  overflow). Breaking the controller still drops everything it holds.
- **Breaking the controller mid-dig no longer silently deletes the frame.** The orphaned frame
  blocks now *decay*: each one crumbles (break effect, **dropping its Frame Casing** — the same
  drop as mining it yourself) at its own random moment, the ring slowly dissolving block by
  block over roughly the next 30 seconds to 4 minutes (tunable via the new
  `quarryFrameDecayTicks` config key). Placing a new controller beside a still-complete
  orphaned ring re-adopts it and stops the decay.

**Storage blocks moved to Neroland Core**

- The **Battery**, **Fluid Tank**, **Gas Tank**, **Item Store**, and **Trash Can** (the first
  four with their **Creative** variants) now ship in the shared **Neroland Core** library
  instead of Nerospace. Their ids changed from `nerospace:*` to `nerolandcore:battery`,
  `nerolandcore:fluid_tank`, `nerolandcore:gas_tank`, `nerolandcore:item_store`,
  `nerolandcore:trash_can` (and `nerolandcore:creative_*`), so every Neroland mod now shares one
  set of storage endpoints. They craft and behave exactly as before.
- Bumped the required **Neroland Core** dependency to **1.1.0** (the release that adds these
  blocks and the generic fluid/gas storage APIs; the Trash Can also brings Core's first menu
  type and client screen).
- The **Universal Pipe** still interoperates with all of these blocks — it bridges Core's
  `nerolandcore:fluid` / `nerolandcore:gas` (and energy / item) capabilities onto Nerospace's
  own `nerospace:fluid` / `nerospace:gas` lookups, so existing pipe networks keep working
  (including voiding into the Trash Can).

**Generators now push power directly (no cables needed)**

- The **Combustion Generator**, **Passive Generator**, and **Solar Panel** now default
  their energy **auto-eject to ON**: they push power into adjacent receivers every tick —
  machines, batteries, pipes, and (via Neroland Core 1.3.2's Forge-Energy fallback)
  third-party FE blocks. Auto-eject can still be toggled off per machine in the
  side-config UI.

**Quarry pacing rebalance**

- The quarry's mining interval increased from 8 to 10 ticks — a modest slow-down to
  rebalance throughput against its power draw.

**Neroland Core dependency bumped to 1.3.2**

- Brings the standard **Forge-Energy fallback** in Core's energy lookup: the **Universal
  Pipe** now connects to and transfers with third-party FE cables and machines (e.g.
  **Energized Power**) on NeoForge and Forge, and Core's **Battery pushes power into
  adjacent blocks** without cables. See Neroland Core's changelog for details.

### Build & CI

- New **auto-assign** workflow: the maintainer is assigned to newly opened issues and PRs
  (including forked PRs via a limited, API-only `pull_request_target`).
- `publish.yml` release-note extraction now tries the exact version section first, falls
  back to `[Unreleased]`, and only uses the placeholder when neither exists.

### Migration note (existing worlds)

- **Block-id aliases preserve existing placements.** Blocks already placed as
  `nerospace:battery`, `nerospace:fluid_tank`, `nerospace:gas_tank`,
  `nerospace:item_store`, or `nerospace:trash_can` (and `creative_*`) are remapped to
  the matching `nerolandcore:` block on load:
  - **Forge** — via Forge's `MissingMappingsEvent` (fully supported).
  - **NeoForge / Fabric** — modern NeoForge removed that event and Fabric never had
    one, so a small, strictly-scoped mixin remaps these ids at the registry
    lookup instead. It is `require = 0` (best-effort): if it can't apply on a given
    version it safely no-ops rather than crashing. **Verify on your save after
    updating**; if a placed block ever drops, break and re-place it with the
    Neroland Core variant — **items and stored contents are unaffected** regardless.

## [1.0.0-beta.1] - 2026-06-27

First public **beta** of Nerospace — and the first **multi-loader** build. Nerospace now runs on
**NeoForge, Forge, and Fabric**, across **Minecraft 26.1.2 and 26.2** (the alpha was NeoForge 26.1.2
only). Each download is published tagged with its exact loader + game version. This release carries the
full 1.0.0 alpha feature set (reproduced below) plus everything added since. Expect balance changes
before the stable release; please report issues on the
[tracker](https://github.com/Neroland/nerospace/issues).

### Added since 1.0.0-alpha.1

**Wider platform support**

- Now available on **NeoForge, Forge, and Fabric** for **Minecraft 26.1.2 and 26.2** — six builds in
  all, each one a self-contained jar tagged for its exact loader and game version on CurseForge and GitHub.

**Rocket return & the Launch Controller**

- The new **Launch Controller** block turns a launch pad into a full launch hub: a holographic preview
  of the docked rocket, powered operation, board-and-launch from the console, and clearer pad/fuel
  status feedback.
- **Rockets now come home** — return pads, arrival sites, and a return-travel system let you fly back
  from any world, with a named launch-pad registry so destinations and return points stay put.
- **Rockets carry oxygen life-support in flight**, and a launch pad can sink gas to keep the cabin
  breathable during ascent.
- **Founded stations gained ownership controls** — rename, claim ownership, and decommission your
  stations, with return-pad travel between them.

**Planetary gravity**

- Each world now has its own **gravity**. The Orbital Station is near-weightless, **Glacira** is a
  floaty low-gravity moon, **Greenxertz** is light, and **Cindara** sits closer to normal — so movement,
  jump height, and falling all feel different from planet to planet. Gravity affects everything: you,
  mobs, dropped items, arrows, falling blocks, and incoming meteors (which descend more slowly on
  low-gravity worlds). Individual biomes can carry a lighter or heavier pull than their planet's baseline,
  and **terraforming a planet restores normal, Earth-like gravity** to the ground you reclaim.
- New config option **`gravityMultiplier`** (`config/nerospace.properties`, range 0.1–10, default 1.0):
  a global scale on all gravity — set it below 1 for an even floatier game, or above 1 for a heavier one.
- Creative/op debug command **`/nerospace gravity`** reports the gravity in effect where you're standing
  (value, source, and your current gravity stat) for tuning and bug reports.

### Changed since 1.0.0-alpha.1

- Expanded anonymous crash-reporting context and tracing coverage (still opt-out, still scrubbed of
  personal data — see [PRIVACY.md](PRIVACY.md)).

### Full feature set (carried from 1.0.0-alpha.1)

**Materials & machines**

- Nerosium ore (stone + deepslate) with the full chain: raw → ingot, dust, storage blocks,
  and the nerosium pickaxe.
- The **Nerosium Grinder** — powered ore-doubling via nerosium dust.
- Planetary materials with their own chains: **Nerosteel** and **Xertz Quartz** (Greenxertz),
  **Cindrite** (Cindara), **Glacite** (Glacira).
- Generators: **Combustion Generator** (burns fuel) and **Passive Generator** (trickle charge).
- The **Fuel Refinery** — turns coal + blaze powder + energy into pipeable rocket fuel.
- Common `c:` tags on all ores/ingots/dusts/gems/storage blocks, and tag-based recipe inputs,
  for clean cross-mod and recipe-viewer compatibility.

**Logistics**

- The **Universal Pipe** — energy, fluids, gas, and items in one tube, with per-face ×
  per-layer modes, visible travelling items, and coloured flow streams.
- The **Configurator** tool + pipe config panel, item filters, and speed/capacity upgrades.
- Storage endpoints: Battery, Fluid Tank, Gas Tank, Item Store (+ creative endless variants).
- Loader-native capabilities exposed on every sensible block face (hopper/pipe automation works
  everywhere, including feeding rockets through the launch pad).

**Rockets & travel**

- Four rocket tiers with per-tier models: **Tier 1** (Orbital Station) → **Tier 2**
  (+ Greenxertz) → **Tier 3** (+ Cindara) → **Tier 4** (+ Glacira).
- The **3×3 Launch Pad** with formation gating, plus the **Heavy Launch Complex** — a 5×5 pad
  with a **Launch Gantry** (right-click to board) that Tier 4 requires and Tier 3 accepts as
  an alternative to its Station Wall ring.
- The **Fuel Tank** pad module — auto-fuels the deployed rocket (up to 480 mB/t on a Heavy
  complex), with canister auto-feed.
- A rocket UI with painted panel, segmented fuel gauge, trajectory arc, and destination cycling.

**Dimensions**

- **Greenxertz** — a green planet with its own ores and creatures.
- **Cindara** — a volcanic moon with a heat hazard.
- **Glacira** — a frozen moon with a cold hazard.
- The **Orbital Station** — and **player-founded stations**: rename a **Station Charter** in an
  anvil, fly there, and found your own named station anchored by a **Station Core** (up to 64
  stations per world, selectable as rocket destinations).

**Oxygen & survival**

- Airless dimensions with a per-block **oxygen field**: sealed rooms fill, leaks drain,
  doors/trapdoors/glass count as boundaries.
- The **Oxygen Generator** — grid-powered electrolysis producing pipeable O₂ — plus airlock
  refilling from Gas Tanks.
- The **Oxygen Suit** in two tiers, plus hazard variants: the **Thermal Suit** (Cindara heat)
  and **Cryo Suit** (Glacira cold) — unprotected exposure quadruples oxygen drain.
- An O₂ HUD gauge with suit/hazard badges.

**Terraforming**

- The **Terraformer** — expanding terrain conversion with staged maturation:
  **Rooted → Hydrated → Living**, including a water cycle fed by the **Hydration Module**
  (glacite), per-planet mature biomes with real weather, and the **Terraform Monitor** readout.
- Three breedable livestock species on terraformed ground: **Meadow Loper**, **Ember Strutter**,
  **Woolly Drift** (food + fleece drops).

**Creatures**

- Native mobs with bespoke models, animations, and glow layers: Xertz Stalker, Quartz Crawler,
  Greenling (Greenxertz), Cinder Stalker (Cindara), Frost Strider (Glacira).

**Progression & polish**

- The **Star Guide** — a pedestal block + guidebook opening an interactive 7-chapter
  progression tree (31 steps), backed by a full advancement tree.
- A creative `/nerospace gallery` showcase command with live demos of every system.
- Bespoke art across the board: shaped machine models, per-tier rocket geometry, per-creature
  textures, animated GUI gauges, fluid/gas visuals.
- Config: five clamped multiplier keys (`oxygenDrain`, `oxygenCapacity`, `energyRate`,
  `fuelCost`, `machineSpeed`) over internal base values — modpack-friendly tuning.
- Optional, anonymous crash reporting (Sentry, EU servers) with full disclosure in
  [PRIVACY.md](PRIVACY.md) and a `telemetryEnabled = false` opt-out.

## [1.0.0-alpha.1] - 2026-06-15

First public **alpha** of Nerospace, for Minecraft 26.1.2 on NeoForge. This pre-release carries
the full 1.0.0 feature set (see below) for early testing — expect rough edges and balance changes
before the stable release. Please report issues on the
[tracker](https://github.com/Neroland/nerospace/issues).

### Added

**Materials & machines**

- Nerosium ore (stone + deepslate) with the full chain: raw → ingot, dust, storage blocks,
  and the nerosium pickaxe.
- The **Nerosium Grinder** — powered ore-doubling via nerosium dust.
- Planetary materials with their own chains: **Nerosteel** and **Xertz Quartz** (Greenxertz),
  **Cindrite** (Cindara), **Glacite** (Glacira).
- Generators: **Combustion Generator** (burns fuel) and **Passive Generator** (trickle charge).
- The **Fuel Refinery** — turns coal + blaze powder + energy into pipeable rocket fuel.
- Common `c:` tags on all ores/ingots/dusts/gems/storage blocks, and tag-based recipe inputs,
  for clean cross-mod and recipe-viewer compatibility.

**Logistics**

- The **Universal Pipe** — energy, fluids, gas, and items in one tube, with per-face ×
  per-layer modes, visible travelling items, and coloured flow streams.
- The **Configurator** tool + pipe config panel, item filters, and speed/capacity upgrades.
- Storage endpoints: Battery, Fluid Tank, Gas Tank, Item Store (+ creative endless variants).
- NeoForge capabilities exposed on every sensible block face (hopper/pipe automation works
  everywhere, including feeding rockets through the launch pad).

**Rockets & travel**

- Four rocket tiers with per-tier models: **Tier 1** (Orbital Station) → **Tier 2**
  (+ Greenxertz) → **Tier 3** (+ Cindara) → **Tier 4** (+ Glacira).
- The **3×3 Launch Pad** with formation gating, plus the **Heavy Launch Complex** — a 5×5 pad
  with a **Launch Gantry** (right-click to board) that Tier 4 requires and Tier 3 accepts as
  an alternative to its Station Wall ring.
- The **Fuel Tank** pad module — auto-fuels the deployed rocket (up to 480 mB/t on a Heavy
  complex), with canister auto-feed.
- A rocket UI with painted panel, segmented fuel gauge, trajectory arc, and destination cycling.

**Dimensions**

- **Greenxertz** — a green planet with its own ores and creatures.
- **Cindara** — a volcanic moon with a heat hazard.
- **Glacira** — a frozen moon with a cold hazard.
- The **Orbital Station** — and **player-founded stations**: rename a **Station Charter** in an
  anvil, fly there, and found your own named station anchored by a **Station Core** (up to 64
  stations per world, selectable as rocket destinations).

**Oxygen & survival**

- Airless dimensions with a per-block **oxygen field**: sealed rooms fill, leaks drain,
  doors/trapdoors/glass count as boundaries.
- The **Oxygen Generator** — grid-powered electrolysis producing pipeable O₂ — plus airlock
  refilling from Gas Tanks.
- The **Oxygen Suit** in two tiers, plus hazard variants: the **Thermal Suit** (Cindara heat)
  and **Cryo Suit** (Glacira cold) — unprotected exposure quadruples oxygen drain.
- An O₂ HUD gauge with suit/hazard badges.

**Terraforming**

- The **Terraformer** — expanding terrain conversion with staged maturation:
  **Rooted → Hydrated → Living**, including a water cycle fed by the **Hydration Module**
  (glacite), per-planet mature biomes with real weather, and the **Terraform Monitor** readout.
- Three breedable livestock species on terraformed ground: **Meadow Loper**, **Ember Strutter**,
  **Woolly Drift** (food + fleece drops).

**Creatures**

- Native mobs with bespoke models, animations, and glow layers: Xertz Stalker, Quartz Crawler,
  Greenling (Greenxertz), Cinder Stalker (Cindara), Frost Strider (Glacira).

**Progression & polish**

- The **Star Guide** — a pedestal block + guidebook opening an interactive 7-chapter
  progression tree (31 steps), backed by a full advancement tree.
- A creative `/nerospace gallery` showcase command with live demos of every system.
- Bespoke art across the board: shaped machine models, per-tier rocket geometry, per-creature
  textures, animated GUI gauges, fluid/gas visuals.
- Config: five clamped multiplier keys (`oxygenDrain`, `oxygenCapacity`, `energyRate`,
  `fuelCost`, `machineSpeed`) over internal base values — modpack-friendly tuning.
- Optional, anonymous crash reporting (Sentry, EU servers) with full disclosure in
  [PRIVACY.md](PRIVACY.md) and a `telemetryEnabled = false` opt-out.

[1.0.0-beta.1]: https://github.com/Neroland/nerospace/releases/tag/v1.0.0-beta.1
[1.0.0-alpha.1]: https://github.com/Neroland/nerospace/releases/tag/v1.0.0-alpha.1
