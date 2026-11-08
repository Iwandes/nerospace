# Nerospace Wiki

**Nerospace** is a space-exploration / tech-progression mod for Minecraft (Java Edition), for
**NeoForge, Fabric and Forge** on **Minecraft 26.1.2, 26.2 and 26.3**. Mine alien materials, build machines, craft rockets, travel to other
worlds, survive their airless atmospheres with oxygen gear, found your own orbital stations, and
eventually **terraform** a dead planet into livable, rained-on ground.

> 🆕 **Nerospace 2.0 is an alpha** and adds Luna, Mars and Nyxara, gives every world its own blocks, ores, sky, clock and weather,
> and turns orbital stations into habitats with classes, members and modules. Coming from 1.x? Read
> **[Upgrading to 2.0](Upgrading-to-2.0)** first, and back up your world before testing.

> 💬 Questions, builds, feedback? Join the [Discord](https://discord.gg/ArPXvYUzJG) or open a
> [GitHub issue](https://github.com/Neroland/nerospace/issues).

## Getting started

> 🚀 **New player? Start with the step-by-step [Getting Started](Getting-Started) guide.** Machine stuck
> at 0% power? See **[Powering Machines](Powering-Machines)**.

1. Mine **Nerosium Ore** in the Overworld — and craft a **[Star Guide Book](Star-Guide)**

   (Book + Raw Nerosium) right away: it's the in-game version of this wiki, a live 16-chapter
   progression tree that always shows your next step.

2. Mine **Nerosteel Ore** deep in the Overworld (below y 16) and build a **generator** — every machine
   needs power. Then build a **Nerosium Grinder** to double your ore yield via **Nerosium Dust**.
3. Craft a Tier 1 **Rocket** and a **[Launch Pad](Rocket-Launch-Pad)** (Tier 1 needs just one pad
   block; Tier 2 needs a full 3×3) and fly to the Orbital

   Station; progress to **Nerosteel** and **Xertz Quartz** on Greenxertz.

4. Pack an **[Oxygen Suit](Oxygen-Suit)** and set up **Oxygen Generators** — every world out there

   is airless. Bring the **Thermal**/**Cryo** variants for Cindara and Glacira.

   - Automate the digging with a **[Quarry Controller](Quarry-Controller)**: mark an area with

     landmarks (or outline it with hand-placed frame blocks) and it strip-mines the whole
     rectangle to bedrock on its own. New to it? Follow the
     **[Quarry Setup Guide](Quarry-Setup-Guide)**.

5. Late game: build the **[Heavy Launch Complex](Launch-Gantry)**, found stations with a

   **[Station Charter](Station-Charter)**, and run a **[Terraformer](Terraformer)** until a dead
   planet grows, rains, and fills with livestock.

## The worlds

In the order you usually reach them:

- **[Luna](Luna)**: grey, airless moon; titanium (Ilmenite), Helium-3, Crater Glass. Tier 1.
- **[Mars](Mars)**: rust-red dunes, canyons and a Dry Ice cap; Hematite, dust storms, rover wrecks and the [Mars Rover](Rover). Tier 2.
- **[Cindara](Cindara)**: volcanic ash world under a red giant; Cindrite, Sulfur, ash fall, the Ember-kin. Tier 3.
- **[Glacira](Glacira)**: ice plains, methane shores and spires; Glacite, diamond dust, auroras, the Frostbound. Tier 4.
- **[Greenxertz](Greenxertz)**: bioluminescent jungle under twin suns; Nerosteel, Xertz Quartz, spore blooms, the Xertz. Tier 2.
- **[Nyxara](Nyxara)**: tidally locked; Dayside, Twilight Band and Nightside; Voidglass, Void Crystal, flares, the Duskborn. Tier 4 after Deep Space progress.
- **[Orbital Stations](Orbital-Stations)**: your own habitats in orbit, with classes, members and modules. Found one
  [where you stand](Orbital-Stations#placing-your-own-station), or land on a hand-placed
  [free dock](Orbital-Stations#free-docks).

Every world keeps its own time and weather: see **[Planet Clock and Weather](Planet-Clock-and-Weather)**. Rockets
land centred on a pad, or on an automatic Landing Pod: see **[Landing Pads](Landing-Pads)**.

## Contents

- **Blocks** — see the sidebar for a page on each block (ores, storage blocks, machines, station

  building blocks, launch infrastructure).

- **[Items](Items)** — materials, tools, the Oxygen Suit family, rockets & fuel, charters, and

  the Star Guide.

- **[Creatures](Creatures)** — the native mobs of Greenxertz, Cindara and Glacira, the terraform

  livestock, and the Moon Hopper, Dust Strider and Dusk Moth of terraformed Luna, Mars and Nyxara.

- **[Alien Villagers](Alien-Villagers)** — wary alien NPCs you befriend to trade and grow villages,

  with the **[Village Core](Village-Core)**, alien **[structures](Alien-Structures)**,
  **[decoration blocks](Alien-Decoration)** and exclusive **[gear](Alien-Gear)**.

- **[Meteor Events](Meteor-Events)** — rare meteor crashes that seed the world with alien materials

  and off-world ores; track them with the Meteor Tracker.

- **[Rockets](Rockets)** — the four crewed hulls, their lit cockpits and windows, what they do on
  the pad and during launch, and [flying with up to four players](Rockets#flying-with-friends-crew).
- **[Mars Rover](Rover)** — a two-seat electric buggy with a cargo bay for driving around any world.
- **[Cargo Pad](Cargo-Pad)** and **[Cargo Rocket](Cargo-Rocket)** — automated freight between pads and
  stations: load a hold, pick a destination, and cargo flies with nobody at either end.
- **[Star Guide](Star-Guide)** — the in-game progression guide.
- **[Public Integration API](Public-API)** — for mod developers: the supported surface for querying
  planets, environment and visits, contributing oxygen, and applying reversible terraforming overlays.
- **[Mod Compatibility](Mod-Compatibility)** — what Nerospace shares with the other mods in your pack:
  energy, fluids and items cross the mod boundary; gas stays Nerospace-side.
- **[Planet Gravity](Planet-Gravity)** — each world's own gravity, biome variation, and how
  terraforming restores it.
- **[Neroland Core](Neroland-Core)** — the required foundation library Nerospace is built on: one
  cross-mod power network, shared progression milestones, a unified privacy/erasure command,
  Core-managed config, and the shared storage blocks ([Battery](Battery), [Fluid Tank](Fluid-Tank),
  [Gas Tank](Gas-Tank), [Item Store](Item-Store), [Trash Can](Trash-Can)) that moved into Core.
- **[Configuration](Configuration)** — server and modpack tuning; the planet clock, weather, particle and HUD keys
  are listed in **[Planet Clock and Weather](Planet-Clock-and-Weather)**.
- **[Saved-Data Backups](Saved-Data-Backups)** — automatic last-known-good backups and crash-proof
  recovery of Nerospace's world data.
- **[Roadmap](Roadmap)** — what shipped in 1.0 and what's next.
- **[Future Features](Future-Features)** — the longer-term wish list.

## Dimensions at a glance

| Dimension | Reached by | Sky | [Gravity](Planet-Gravity) | Notes |
| --- | --- | --- | --- | --- |
| Overworld | — | normal | `1.0×` | Source of Nerosium. |
| **[Orbital Station](Orbital-Stations)** | Tier 1+ rocket | black, the homeworld below | `~0.1×` | A platform in orbit, plus any [station you found](Station-Charter) yourself. Airless/void. |
| **[Luna](Luna)** | Tier 1 rocket | black and starry, Earth over the horizon | `~0.17×` | Grey airless moon; Ilmenite (titanium), Helium-3, Crater Glass. 28-day lunar day, no weather. |
| **[Mars](Mars)** | Tier 2 rocket | butterscotch, blue sunsets, dust storms | `~0.38×` | Rust-red dunes and canyons; Hematite, Perchlorate, Dry Ice. **Cold** (any Tier 2 [suit](Oxygen-Suit); Cryo Suit on the Polar Cap) + **radiation** in dust storms (Rad Lining). |
| **[Cindara](Cindara)** | Tier 3+ rocket | red giant, eclipse, ash fall | `~0.8×` | Volcanic world; Cindrite, Sulfur. **Heat** (×4 O₂ drain without a [Thermal Suit](Oxygen-Suit)). |
| **[Glacira](Glacira)** | Tier 4 rocket | pale sun, two moons, auroras | `~0.4×` | Frozen world; Glacite, Methane Ice. **Cold** (×4 O₂ drain without a [Cryo Suit](Oxygen-Suit)). |
| **[Greenxertz](Greenxertz)** | Tier 2+ rocket | twin suns, real day/night | `~0.6×` | Green jungle world; Nerosteel + Xertz Quartz, the Xertz villagers. Spore blooms. |
| **[Nyxara](Nyxara)** | Tier 4 rocket (Deep Space progress) | fixed sun in the west, galaxy in the east, flares | `~0.9×` | Tidally locked; Dayside, Twilight Band and Nightside by longitude; Voidglass, Void Crystal, Gravity Shards. **Radiation** everywhere ([Rad Lining](Oxygen-Suit#rad-lining-radiation)). |

Full skies are drawn on NeoForge for Minecraft 26.3; every other version shows each world's sky colours.

All Nerospace dimensions are **airless** — without a breathable zone (an Oxygen Generator, a Launch
Pad, terraformed ground, or a full Oxygen Suit) you will run out of oxygen and suffocate. They also
each have their **own [gravity](Planet-Gravity)** — lighter than home out on the moons, near-weightless
in orbit — which [terraforming](Terraformer) restores to normal as you reclaim the ground.
