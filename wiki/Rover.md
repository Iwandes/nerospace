# Mars Rover

A six-wheeled electric buggy for getting around a planet without walking. It seats **two** (a driver and one
passenger), carries **nine slots** of cargo and runs on a rechargeable battery. It was designed on Mars, but it
drives on any solid ground in any world, the Overworld and every Nerospace planet included.

## Getting one

You need three things from Mars: **Raw Hematite**, **Perchlorate Oxidiser** and **Rover Parts**.

- **Rover Parts** (makes 2): four Raw Hematite in the corners, four Iron Ingots on the sides, Redstone in the
  middle. A wheel, its strut and a little hub motor.
- **Mars Rover**: Hull Plating, a Chest, Hull Plating on the top row; Rover Parts, Perchlorate Oxidiser, Rover Parts
  in the middle; Rover Parts, an Iron Ingot, Rover Parts on the bottom.
- **The shortcut**: about half of the [Rover Wrecks](Mars#rover-wrecks-the-salvage-drone-and-derelict-habitats)
  on Mars have a **Rover Chassis** in their salvage chest. A Chest above, Rover Parts either side of the Chassis, and
  Perchlorate Oxidiser below gives you a rover for much less.

Everything is made at a normal crafting table.

## Placing it and packing it up

- **Place**: right-click the ground with the Mars Rover item. It faces the way you are looking.
- **Pack up**: **sneak and punch** it. Everyone has to be out first. Whatever is in the cargo bay spills on the
  ground, and the item keeps the battery charge (shown as a bar and in its tooltip).
- Only the player who placed a rover (or a server operator) can pack it up. Anyone can ride in it.
- You can have up to **4** rovers out at once (servers can change this, see below).

## Driving

- **Get in**: **right-click** it, the same as a horse. The first person in drives; one more can ride along. Sneak
  to get out.
- **Drive**: your normal forward and back keys accelerate and reverse, left and right steer.
- **Jump** makes a small hop while you are moving. Pressed while you are stopped, it honks the horn instead.
- **Cargo**: **sneak and right-click** the rover to open its nine-slot bay. The title shows the charge.

What it can and cannot do:

- It climbs a step of **one block**, never two. Its hop is never high enough to get onto a two-block wall either.
- It cannot fly, and it cannot swim: in water or lava it just sinks slowly and will not drive.
- On low-gravity worlds it is faster and hops higher; on heavy worlds it is a little slower. On Mars it tops out
  around 7.5 blocks a second.
- It has **no life support**. Wear your [Oxygen Suit](Oxygen-Suit): the planet's air, heat, cold and radiation
  reach you in the seat exactly as they would on foot.
- It does not keep chunks loaded. Leave it somewhere and it waits there until you come back.

## Charging

The battery holds **40,000 FE**. Holding the throttle uses 5 FE a tick, so a full battery is about 400 seconds of
driving (well over two thousand blocks). Hops cost a little extra. Coasting and standing still are free.

To charge it, **park it right next to (or on top of) any Neroland energy block**: a [Battery](Battery), a
generator, a [Universal Pipe](Universal-Pipe) carrying power, or a station's **Solar Wing**. A parked rover pulls up
to 200 FE a tick from up to four blocks around it. Machines from other Neroland mods work too.

The headlights and the cyan charge bar on the back go dark when the battery is flat.

## Your data

A rover remembers only **who placed it**, so it knows who may pack it up and can show up in your own NeroLink app.
It keeps no record of where it has been or who drove it. If your data is erased, the rover stays in the world
without an owner, and anyone can pack it up.

## Configuration

See [Configuration](Configuration#mars-rover): `rover.capacity`, `rover.maxPerPlayer`, `rover.drainPerTick`,
`rover.chargeRate`.
