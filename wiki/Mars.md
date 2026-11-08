# Mars

Mars is a **Tier 2** destination: rust-red dunes, banded canyons and a frozen polar cap under a butterscotch sky.

| | |
|---|---|
| Dimension | `nerospace:mars` |
| Rocket | **Tier 2** or any higher tier |
| Gravity | 0.38 × Overworld |
| Day | one sol = 24 600 ticks (a little longer than an Overworld day) |
| Weather | Dust storms, on average every 2.5 sols, lasting 5 to 15 minutes |
| Hazard | Cold everywhere (any Tier 2 suit; Cryo Suit on the Polar Cap); radiation outdoors during dust storms ([Rad Lining](Oxygen-Suit#rad-lining-radiation)) |
| Villagers | None (Rover Wrecks, Salvage Drones and Derelict Habitats instead) |
| Native life | Dust Strider, on terraformed ground only |

## Biomes

- **Martian Dunes**: Red Regolith over Martian Sandstone. Perchlorate Crust patches, the odd Basalt Column, and Dust Devil Pits.
- **Martian Canyons**: flat red floors between walls of banded Sandstone Strata. Basalt Columns stand on the rims.
- **Martian Polar Cap**: a sheet of Dry Ice over sandstone. It is colder than the rest of Mars: only a Cryo Suit
  keeps this cold out.

There is no open water on Mars until you terraform it. Hematite, Nerosium and Nerosteel ore generate in all three
biomes. Mars has its own terrain shape: its hills and valleys do not line up with the Overworld's.

## Blocks and items

| Block / item | What makes it special |
|---|---|
| Red Regolith | During a dust storm, natural regolith drifts one block downwind (at most 16 moves per chunk per storm). Regolith you place yourself never moves. |
| Martian Sandstone | Slowly shimmering bands. Use a stonecutter to cut it into four **Strata** variants (Thin, Banded, Wide, Cross-Bedded). Each one lines up with the direction you place it. |
| Hematite Ore | Needs an iron pickaxe. If you break it with an iron or better tool, the drops are pulled toward you. Drops 1 to 2 Raw Hematite (Fortune applies). |
| Raw Hematite | Smelt or blast it into an iron ingot, or craft one into two Rust Pigment. |
| Rust Pigment | A Mars dye material that crafts into red dye. |
| Perchlorate Crust | Standing on it without sealed oxygen-suit boots gives Poison. It drops Perchlorate Oxidiser. |
| Perchlorate Oxidiser | A rocket-fuel booster for the [Fuel Refinery](Fuel-Refinery). |
| Dry Ice | Freezes adjacent still water into ice, once, when you place it next to water (it never spreads). Next to a bright light source (block light above 11) it fumes and may vanish. Silk Touch is needed to collect it. |
| Basalt Column | Place one and it grows upward into a pillar up to 4 blocks tall, as long as the space above is free. |
| Dust Devil Pit | Found only in the dunes. During a dust storm it can release a Dust Devil. Silk Touch is needed to collect it. |

## Hazards and protection

- **Cold, everywhere.** Mars is a cold world, but not a frigid one: a full **Tier 2** suit keeps its cold out, and so
  does a full Thermal or Cryo Suit (both are Tier 2 suits). In a Tier 1 suit your oxygen drains four times as fast.
- **Frigid on the Polar Cap.** The Martian Polar Cap is colder still: there only a full four-piece **Cryo Suit** keeps
  the cold out, as on Glacira.
- **Radiation in dust storms.** While a dust storm blows, the open surface outside a sealed oxygen field is irradiated
  (radiation level 2). Without a full set of Rad-Lined suit pieces you lose oxygen four times as fast and take one
  point of damage every two seconds. Go indoors, under cover or into an oxygen field, or line your suit.
- A Rad-Lined Tier 2 suit covers both on the dunes and in the canyons; on the Polar Cap you need a Rad-Lined Cryo
  Suit. The HUD warns about whichever hazard would hurt you next.
- Perchlorate Crust poisons bare boots (see above).

## Clock and weather

A Martian sol is 24 600 ticks. The day is butterscotch, the sunset blue. Every 2.5 sols on average a **dust storm**
rolls in: the sky turns brown, solar panels lose up to 80 % of their output, the open air becomes radioactive outside
a sealed oxygen field, Red Regolith drifts, and Dust Devil Pits may release **Dust Devils**: whirls of red dust that
drift downwind and toss loose items into the air. They never break blocks, never pick anything up and never hurt
players. Each lasts at most 30 seconds, and they all disappear when the storm ends. See
[Planet Clock and Weather](Planet-Clock-and-Weather).

**Beds** work in the Martian night: when enough players on Mars are asleep, the clock jumps to sunrise and the sol
count goes up by one. A dust storm keeps blowing through it. See [Sleeping](Planet-Clock-and-Weather#sleeping).

**Open water freezes.** Mars is cold, so water under the open sky turns to ice like in a snowy Overworld biome,
unless the block light at the water is 10 or more. Sugar cane planted by that water breaks when it freezes. Keep a
light source within a few blocks of the water, or farm under a roof.

## Sky

A butterscotch sky by day with a small, cool-white sun. At sunset the sky turns grey-blue and the sun wears a
clearly **blue glow** around it, the real Martian effect of fine dust scattering the light. Phobos races across
several times a sol and slow little Deimos follows, both showing phases. A dust storm turns the sky rust and brown,
hides the stars and dims the moons; for about a minute after it clears, a faint green aurora hangs over the north.
Shooting stars cross the night sky.

The full sky is drawn on NeoForge for Minecraft 26.3. On every other version Mars shows its colours with the vanilla sky shapes, and the world fog still follows the time of day and the weather.

## Rover Wrecks, the Salvage Drone and Derelict Habitats

Mars has no villagers. Someone was here before you, though:

- **Rover Wrecks** are half-buried six-wheeled rovers with a snapped antenna, a solar wing and red regolith
  drifted over the tail. Each has a salvage chest (Raw Hematite, Titanium Ingots, Hull Plating, Truss, Rust
  Pigment, sometimes a Solar Wing or Alien Tech Scrap). About half of them still hold a **Rover Chassis**, the
  shortcut to building your own [Mars Rover](Rover).
- A **Salvage Drone** hovers over each wreck on its thruster pods. Right-click it to trade **Raw Hematite** for rover
  parts: Hull Plating, Truss, Titanium Ingots, Solar Wings, Frame Casing and Alien Tech Scrap. It has a single price
  list for everyone, and its stock is limited: each offer can be used only a few times (Titanium Ingots twice, Alien
  Tech Scrap once) and the drone restocks once a day. There is no reputation, it has no Village Core, and it
  keeps no record of who traded with it.
- **Derelict Habitats** are the ruins of an early surface base: a round floor pad, a cracked hull with a
  window band, a half-collapsed dome, regolith blown in through the gaps, a dead console and a supply locker.

Wrecks appear in the dunes and canyons, and habitats in all three Mars biomes. Both appear only in newly
generated land.

## Terraforming and fauna

On Mars the [Terraformer](Terraformer) turns rust sand into red loam under hardy grass (**Rooted**). **Hydrated**
ground brings the **first open water on Mars**: one spring opens in each chunk, at the lowest natural ground the
Terraformer has watered. **Living** ground settles into a treeless **Red Steppe**. Dust storms still blow over
terraformed land, so keep your Rad Lining. Terraformed ground seeds Hematite and Nerosteel.

The Red Steppe is grazed by herds of **Dust Striders**, long-legged herd animals that drop Rust Pigment and are never
harmed by Mars. The Terraformer stocks a starter herd, and more spawn on Living ground. See [Creatures](Creatures).

## Star Guide

**Worlds: Mars**: land, mine Hematite, build with Martian Sandstone, last two minutes under the open sky in a dust
storm, and root your first terraformed ground.

## Tips

- Bring sealed boots before you walk across perchlorate patches, and a Rad-Lined Tier 2 suit for long trips (a Cryo
  Suit if you head for the Polar Cap).
- Build with placed Red Regolith if you want your dunes to stay put.
- Keep Dry Ice away from torches and lamps, or store it in a chest.
- Solar power sags in storms: give Martian bases a battery or a generator.
