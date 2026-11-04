# Planet Clock and Weather

From 2.0 every Nerospace world keeps its own time and has its own weather. Both are decided by the server, saved with
the world and sent to every player on that planet, so everyone sees the same sky. They keep running on loaded planets
with nobody there, so unattended machines see the same day and storms as visitors.

## Planet clocks

| World                               | Day length    | In real time                                            | Notes                                                                          |
| ----------------------------------- | ------------- | ------------------------------------------------------- | ------------------------------------------------------------------------------ |
| [Luna](Luna)                        | 672 000 ticks | about 9 h 20 min (28 Overworld days) | Earth rises late in the long night, adding a faint earthshine                  |
| [Mars](Mars)                        | 24 600 ticks  | 20.5 min (one sol)   | a blue sunset                                                                  |
| [Cindara](Cindara)                  | 48 000 ticks  | 40 min (two Overworld days)          | a 4-minute _Eclipse_ in the late afternoon, running into dusk                  |
| [Glacira](Glacira)                  | 72 000 ticks  | 1 h (three Overworld days)           | long twilights at dawn and dusk                                                |
| [Greenxertz](Greenxertz)            | 24 000 ticks  | 20 min                                                  | the Overworld's own day: beds, raids and `/time` work as usual |
| [Nyxara](Nyxara)                    | fixed         | —                                                       | the sun never moves; day and night are places, not times                       |
| [Orbital Station](Orbital-Stations) | fixed         | —                                                       | permanent daylight                                                             |

**What a clock drives:** the sky (sun, moons, stars, colours), the HUD clock dial, [solar panel](Solar-Panel) output,
when weather may start (Glacira's diamond dust only forms at night or in twilight; Cindara's ash fall never starts near
the eclipse), Aurora Stone (it glows only during Glacira's night), the Star Guide's lunar-night step and when you can
[sleep](#sleeping).

**What it never changes:** block light and sky light. It is as bright underfoot at a planet's "night" as it was in
1.x, and mobs spawn by light exactly as before. Greenxertz is the exception only because it runs on the real
Overworld day, as it always has.

### The HUD clock dial

On every world with a day cycle a small dial sits above the oxygen gauge: sunrise is on the right, noon at the top,
and the face is lit by how bright that planet's day is at the moment. Special stretches of the day are marked on the
rim: Cindara's eclipse as a dotted red window, Luna's Earthrise and Glacira's two twilights as solid windows. When
the phase changes the dial shows its name for two seconds: _Sunrise_, _Day_, _Sunset_ or _Night_, or the name of
the window you are in: _Eclipse_ (Cindara), _Earthrise_ (Luna) or _Twilight_ (Glacira, in place of Sunrise and
Sunset). Nyxara and the orbital station have fixed clocks, so they show no dial.
Client options: `hud.clockDial` (on/off), `hud.clockDialScale` (1 = 16 px, 2 = 32 px).

### Solar panels

A panel on a planet peaks at that planet's noon, dips at night and in a Mars dust storm (down to a fifth of its
output at the height of the storm), and over a full day gives at least the old constant double rate on every planet:
the peaks are higher to make up for the nights. The orbital station stays at a constant double rate. Set
`solar.clockDriven=false` to restore the 1.x constant output everywhere.

### Sleeping

Vanilla time stands still on the worlds with their own clock, so a bed there follows the planet's clock instead:

| World                        | When you can sleep                                                                         | What a full night's sleep does                                                                                                                                                                    |
| ---------------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Luna, Mars, Cindara, Glacira | in the planet's night: from just after sunset until shortly before sunrise | once enough players on that planet are asleep, the clock jumps to sunrise and the day count (Mars: the sol) goes up by one, as if you had waited the night out |
| Greenxertz                   | at night, as in the Overworld                                                              | the Overworld's own night skip: Greenxertz shares the Overworld's day                                                                                                             |
| Nyxara                       | in a bed on the **Nightside**, at any time                                                 | you rest and get up on your own a few seconds later; nothing is skipped, the sun never moves. In the Twilight Band and on the Dayside: "The sun never sets here"  |
| Orbital Station              | never: "The station's lights never dim"                                    | —                                                                                                                                                                                                 |

- Every bed on every world sets your respawn point, even where you cannot sleep, and no bed explodes.
- "Enough players" is the `players_sleeping_percentage` game rule, counted per world: players asleep on Luna skip
  Luna's night only, and a planet's night never moves the Overworld's time.
- Cindara's eclipse is not night: the window opens after sunset. Glacira's night ends part-way through the dawn
  twilight.
- Sleeping never ends planet weather: a dust storm, an ash fall or diamond dust keeps running.
- While an operator has frozen a clock (`/nerospace clock <planet> freeze`, or a `/nerospace gallery sky` state) you
  can still lie down at night and get up rested, but the night does not pass. With `clock.enabled=false` every clock
  reads noon, so beds on Luna, Mars, Cindara and Glacira only set your spawn point.
- The vanilla `advance_time` rule (`doDaylightCycle` before 1.21.11) does not stop a planet's clock or its night
  skip; only `/nerospace clock <planet> freeze` does.
- Open water freezes under the sky on the cold worlds (Luna, Mars, Glacira, the Nyxara Nightside and the orbital
  station), as in a snowy Overworld biome, unless the block light at the water is 10 or more. Sugar cane planted by
  frozen water breaks, so light the water or farm under a roof.

## Weather

Each world has one weather event (none on Luna or the station). An event builds up over up to about 50 seconds,
holds at its peak for the time below, then fades. Each event rolls its own strength, from 40 % to full.

| World                    | Event            | How often (average)                                             | Peak lasts                                  | What it does                                                                                                                                                                                    |
| ------------------------ | ---------------- | ---------------------------------------------------------------------------------- | ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Luna](Luna)             | —                | —                                                                                  | —                                           | Clear skies always                                                                                                                                                                              |
| [Mars](Mars)             | **Dust storm**   | every 2.5 sols (about 51 min)                   | 5–15 min                                    | Brown sky; solar output falls; the open surface outside a sealed oxygen field is irradiated (radiation 2); Red Regolith drifts downwind; Dust Devil Pits release Dust Devils |
| [Cindara](Cindara)       | **Ash fall**     | about every 30 min (1.5 Overworld days)         | 2.5–7.5 min | Ash Layers pile up (up to four); in the strongest part, ember strikes hit open ground near players (never Basalt Spires, never setting blocks alight)     |
| [Glacira](Glacira)       | **Diamond dust** | about every 40 min (two Overworld days), night or twilight only | about 3–10 min                              | Open water under the sky freezes over; auroras brighten; Frost Crystal Clusters that see the sky grow one stage when it ends                                                                    |
| [Greenxertz](Greenxertz) | **Spore bloom**  | about every hour (three Overworld days)                         | 2–5 min                                     | Spores fill the air; Spore Moss spreads three times as fast; Nausea for anyone outside a sealed suit or an oxygen field                                                                         |
| [Nyxara](Nyxara)         | **Solar flare**  | about every 80 min (four Overworld days)                        | 1–2 min                                     | Gold sky; Dawnband Stone lights up and keeps the cold away within four blocks; Shadow Lichen withers; radiation +1 outdoors on the Dayside and in the Twilight Band                             |
| Orbital Station          | —                | —                                                                                  | —                                           | No weather                                                                                                                                                                                      |

"Outdoors" means at or above the surface of the ground below you and not inside a breathable zone (an oxygen field,
a launch pad's safe zone or terraformed ground). A roof over your head or an oxygen field keeps weather radiation off. Spore nausea
reaches anyone, indoors or out, who is neither wearing a full suit nor standing in a breathable zone. The Star Guide's Worlds chapters ask you to last two minutes under the open sky through each world's
weather (on Luna, its night).

Particles (dust, ash, spores, diamond dust, embers near lava, Nyxara's nebula motes) drift only around you, only under
open sky, and thicken with the weather. Each world has its own ambient sound, with weather loops for every event and a
chime as a flare begins. With `hud.weatherLine` on, the HUD shows the current weather with a four-step intensity bar.

## Commands (operators)

| Command                                         | What it does                                                                                                                                                                 |
| ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/nerospace clock <planet>` or `... get`        | Shows the phase (0 = sunrise, 0.25 = noon, 0.5 = sunset, 0.75 = midnight), the daylight and the day count |
| `/nerospace clock <planet> set <phase>`         | Jumps the clock to a phase from 0 to 1                                                                                                                                       |
| `/nerospace clock <planet> freeze` / `thaw`     | Stops or restarts the clock                                                                                                                                                  |
| `/nerospace weather <planet>` or `... get`      | Shows the current event, its phase and intensity                                                                                                                             |
| `/nerospace weather <planet> start [intensity]` | Starts the planet's event now (intensity 0.4 to 1.0, default full)                                                        |
| `/nerospace weather <planet> clear`             | Ends the current event                                                                                                                                                       |

Greenxertz follows the normal day, so use `/time` there. Nyxara and the station have fixed clocks that cannot be set
or frozen. The commands need operator permission (level 2).

### Sky gallery

`/nerospace gallery sky` holds a world's sky in one of its showcase states, for screenshots and for checking how a
world looks. It needs operator permission (level 2), like the clock and weather commands, and is run by a player.

| Command                                        | What it does                                                                                                                                                                         |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `/nerospace gallery sky`                       | Lists every world and its states                                                                                                                                                     |
| `/nerospace gallery sky <planet> <state>`      | Sets that world's clock to the state's time and holds it there, and starts its weather at full strength or clears it. Everyone on that world sees the change at once |
| `/nerospace gallery sky <planet> <state> here` | The same, and moves you to a viewing spot on that world (your own spot if you are already there; Nyxara picks the band or a side), facing the view                |
| `/nerospace gallery sky reset [<planet>]`      | Lets the clock run again and clears the weather, on one world or on all of them                                                                                                      |

Tab completion only offers the states the chosen world has:

| World           | States                                                                                                                                 |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Greenxertz      | `dawn`, `noon`, `dusk`, `night`, `twin_noon`, `spore_bloom`                                                                            |
| Cindara         | `dawn`, `noon`, `dusk`, `night`, `eclipse`, `ash_fall`                                                                                 |
| Glacira         | `dawn`, `noon`, `dusk`, `night`, `twilight`, `aurora`, `diamond_dust`                                                                  |
| Orbital Station | `dawn`, `noon`, `dusk`, `night`, `sunrise`, `planet_night`                                                                             |
| Luna            | `dawn`, `noon`, `dusk`, `night`, `earthrise`                                                                                           |
| Mars            | `dawn`, `noon`, `dusk`, `night`, `blue_sunset`, `dust_storm`                                                                           |
| Nyxara          | `dayside`, `band`, `nightside`, `galaxy_side`, `flare`; `noon`, `dawn`/`dusk` and `night` mean the dayside, the band and the nightside |

- Weather states (`spore_bloom`, `ash_fall`, `diamond_dust`, `dust_storm`, `flare`) start the event at its peak; it
  then lasts its normal time and fades. Run the state again to restart it. If a world's weather is switched off in the
  server settings, the clock still changes but no weather starts.
- Nyxara's sun never moves, so its states are places: without `here` the command tells you roughly which X to stand at.
- Greenxertz shares the Overworld's day, so its states set the Overworld's time too and the day keeps running.
- The full set pieces are drawn on NeoForge for Minecraft 26.3; on other versions a state shows that world's colours
  at the matching time, and the world fog still follows it.
- A held sky keeps its aurora, shooting stars and star twinkle moving; the sun, moons and colours stay put.

## Configuration

Server settings (in `config/nerospace.properties`, see [Configuration](Configuration)):

| Key                                | Default | Range                  | Effect                                                                                      |
| ---------------------------------- | ------- | ---------------------- | ------------------------------------------------------------------------------------------- |
| `clock.enabled`                    | `true`  |                        | `false` holds every planet clock at noon (Greenxertz keeps its real day) |
| `solar.clockDriven`                | `true`  |                        | `false` = the 1.x flat double solar rate everywhere                         |
| `weather.enabled`                  | `true`  |                        | `false` = clear skies everywhere                                                            |
| `weather.<planet>.enabled`         | `true`  |                        | per world: `mars`, `cindara`, `glacira`, `greenxertz`, `nyxara`             |
| `weather.frequencyMultiplier`      | `1.0`   | 0.1–10 | higher = weather starts more often                                                          |
| `weather.durationMultiplier`       | `1.0`   | 0.1–10 | higher = weather lasts longer                                                               |
| `weather.emberStrikeIntervalTicks` | `100`   | 40–12 000              | ticks between Cindara ember strikes near each player                                        |
| `weather.particleBudget`           | `1000`  | 0–4 000                | weather particles a client may spawn per second                                             |

Client settings (each player's own, never forced by the server):

| Key                                 | Default                                                                                                                                                                                                              | Effect                                                                 |
| ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `particles.enabled`                 | `true`                                                                                                                                                                                                               | `false` = no ambient planet particles at all                           |
| `particles.<type>.perPlayerPerTick` | dust 0.6, ash 0.5, spore 0.4, diamond_dust 0.8, ember 0.2, nebula_mote 0.3 | particles per tick around you at full weather (0–8) |
| `hud.clockDial`                     | `true`                                                                                                                                                                                                               | show the clock dial                                                    |
| `hud.clockDialScale`                | `1`                                                                                                                                                                                                                  | 1 = 16 px, 2 = 32 px                                                   |
| `hud.weatherLine`                   | `true`                                                                                                                                                                                                               | show the weather line                                                  |

## Skies

The star fields mix many faint points with a few brighter stars. Galaxies have branching dust lanes and fine star
clusters; their light fades with the stars during storms. Mars adds orange and violet dust filaments, Greenxertz has
emerald and blue wisps, and Glacira has cyan and lilac clouds and two individually cratered ice moons. These faint
structures brighten at night. Nyxara's galaxy combines magenta, violet and turquoise light. Earth has spherical coastlines, cloud swirls,
polar ice and night lights, with its phase still following the sun.

The sky stays steady while walking, even with view bobbing enabled. Your normal world view keeps its bobbing setting.
The horizon gradients blend smoothly into the lower sky, without a speckled strip below the horizon.

Each world has its own sky: Earth hanging over Luna, the butterscotch Martian day with Phobos racing across it,
Cindara's red giant and its eclipse, Glacira's pale sun, two moons and auroras, Greenxertz's twin suns, Nyxara's pinned
sunset on one side and a galaxy on the other, and the homeworld turning below your station. The full skies are drawn on
NeoForge for Minecraft 26.3. On every other loader and version each world still shows its own sky, fog and sunrise
colours with the vanilla sky shapes.
