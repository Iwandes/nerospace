# Orbital Stations

Player stations in orbit: founded with a [Station Charter](Station-Charter), run from the **Station Console**,
and grown one module at a time.

## Founding

Use a **Station Charter** (name it in the charter screen) or pick *New Station* in a rocket while carrying a
charter. A new station is a 7×7 room with an airlock and a **Docking Ring** where rockets arrive. Stations
founded before 2.0 keep their original room and their 3×3 landing pad exactly as they were.

### Placing your own station

Use a charter **while you are in the station dimension** and the station is founded **where you stand**, not in the
next free spot. The block under your feet becomes the station floor. The naming screen then shows **Build starter
structure** (on by default):

- **On:** the room, airlock, Station Core and Docking Ring are built around you.
- **Off:** you get your claim (48 blocks each way from the centre) and a Docking Ring, nothing else. You build the
  rest yourself. The game never stamps the starter room over your build later.

A few rules keep stations apart:

- The centre must be at least **512 blocks** from every other station and from the shared platform at 0,0
  (measured on each axis). If it is not, you see *Too close to …* with the station's name.
- It must be within **30,000 blocks** of 0,0 on each axis, or you see *Too far from the origin*.
- The floor height is kept between Y 64 and Y 256.
- A world holds up to 64 stations. When it is full you see *The station registry is full*.

If the station cannot be placed, **your charter is not used up**. Move and try again. Use a charter anywhere else
and it works as before: the station goes in the next free spot and is built for you.

### Free docks

You do not need a charter to build a place rockets can reach. Place a **Docking Ring** anywhere in the station
dimension **outside every station's claim** (and away from the shared platform) and it becomes a **free dock**.

- It shows up in a rocket's **Dock at** list, marked *free dock*, when it is within **2,048 blocks** of where that
  rocket last stood in the station dimension. A rocket that has never been to the station measures from the
  shared platform at 0,0. There is no list of every free dock on the server.
- Rename the Docking Ring in an anvil before you place it to give the dock a name. An unnamed ring is listed as
  *Free dock 1 (420 m)*: its position is never sent to other players' lists, only a rounded distance.
- Anyone whose rocket can see a free dock can land on it and so find it. If you want to keep a base to yourself,
  claim it with a charter and set its docking to members only.
- Rockets land on the ring itself. If the ring is broken or swallowed by a new station's claim while you are in
  flight, the rocket docks at the shared platform and the chat line says why.
- A free dock has **no owner**, no members and no docking policy: anyone whose rocket can see it may land. If you
  want those, found a station with a charter.

### The shared platform

The platform at 0,64,0 is listed as **Shared platform (origin)**. It belongs to nobody and is never one of the
founded stations. A rocket docks there only when you pick it, when your chosen dock is gone, or when you have no
station of your own (or as a member) yet.

## The Station Console

Right-click the [Station Core](Station-Core) to open the console. It has four tabs:

| Tab | What it shows |
|---|---|
| Overview | name, class, modules, Solar Wing power, how many sealed modules have air, members, routes |
| Members | the owner and up to 16 members (only the owner, members and operators see the list) |
| Docking | who may dock, where rockets arrive, cards for the pads on other worlds named after the station, routes and inbound cargo |
| Build | one row per module: its cost (missing materials in red), Build or Repair, and an On/Off switch for Solar Wings and the Gravity Deck |

Anyone may open the console; a player who is not the owner or a member sees a read-only *Visitor* view with
the member count only.

## Who can do what

- **Owner** (or anyone, on an unowned station): rename, change class, set docking, add and remove members,
  build modules, abandon the station.
- **Members**: use the station — dock when docking is limited to members, switch module power, send a
  visitor's rocket home, leave. A station whose docking is limited to members is not listed in other players'
  Rocket screens.
- **Visitor rockets**: a rocket that arrived with a rider who was neither the owner nor a member is a visitor.
  Once it has stood riderless on the dock for ten minutes, the owner or a member can send it back to its home
  pad from the Docking tab. Rockets the owner or a member flew in are never sent away. Nothing is sent while a
  player who is not the owner, a member or an operator is anywhere on the station (within 48 blocks of its
  core), so a visitor is never left without a ride; everyone on the station is told when rockets are sent.
- **Unowned stations** (stations from before owners were recorded) can be managed, and removed with
  `/nerospace station remove`, by anyone. Ask an operator to claim one to protect it.
- **Operators**: may manage any station. Each operator action is written to the server log with the station's
  slot number and the action only.
- Members are added only from the players currently online. Nobody's name is stored by the mod.

## Classes

Every station has one class, shown in the console header. Change it from the Overview tab, once per in-game day.

| Class | Perk |
|---|---|
| Waystation | +1 route capacity (every station from before 2.0 starts here) |
| Research | timed progress 25 % faster for members aboard; unlocks the Observatory |
| Industrial | Solar Wings produce 50 % more; hydroponics grow at half speed |
| Trade | +2 route capacity; economy mods may treat it as a market |

Changing class never removes a module: an Observatory on a station that leaves Research simply goes dormant.

## Modules

Built from the Build tab with materials taken from your inventory. Up to eight modules (the core room does not
count). Building a module that already exists only **repairs** its missing blocks, for free. A build never
replaces a block you placed: if anything is in the way, nothing is built and the console tells you where.

| Module | Needs | Cost |
|---|---|---|
| Habitat Ring | — | 64 Hull Plating, 24 Hull Window, 32 Station Floor, 8 Titanium Ingot |
| Docking Ring | — | 24 Hull Plating, 12 Titanium Ingot, 4 Nerosteel Ingot |
| Solar Wing (×2) | — | 8 Truss, 12 Solar Wing |
| Hydroponics Bay | Habitat Ring | 32 Hull Plating, 16 Hull Window, 12 Hydroponics Tray, 8 Station Floor |
| Gravity Deck | Habitat Ring | 24 Hull Plating, 8 Hull Window, 9 Gravity Plate, 4 Gravity Shard |
| Observatory | Research class | 16 Hull Window, 8 Hull Plating, 4 Truss, 8 Titanium Ingot, 1 Voidglass Lens, 2 Void Crystal |

Modules grow west, north, south and up from the core room; the airlock side stays free for docking. Building the
Docking Ring on an older station replaces its 3×3 launch pad.

## Life support, power and gravity

- **Air:** each sealed module (core, Habitat Ring, Hydroponics Bay, Gravity Deck, Observatory) gets its own
  oxygen supply when built. Hull Plating and Hull Window are airtight; break the hull and the air leaks into the
  void. An existing station's room gets breathable air the first time it loads in 2.0, unless something
  already supplies it.
- **Power:** each Solar Wing block makes 200 FE/t (300 on an Industrial station) while it faces open space,
  and pushes it into adjacent machines, pipes and batteries. Wings only work on the station.
- **Gravity:** a Gravity Plate gives normal gravity in the six blocks above it; the Gravity Deck has nine.
  Switching the deck off returns it to station gravity.
- **Hydroponics:** a crop on a Hydroponics Tray grows when a Hull Window is within three blocks above the tray and
  the crop is in breathable air. No water is needed. Trays grow Earth crops; Xertz plants only root in
  Xertz Soil. Crops on a tray grow at the speed of unwatered farmland.

## The Observatory

A glazed cupola on the core room's roof with a **Telescope** at its top, reached by a truss ladder inside the
room. On a Research station, players who may use the station see the next locked world in the rocket's
destination list as a full planet card while they are aboard. Each Void Crystal placed in the column above the
telescope shows one more world, up to three.

## Station blocks

| Block | What it does |
|---|---|
| Hull Plating | Airtight hull, made with titanium from [Luna](Luna) |
| Hull Window | Airtight glazing |
| Truss | Climbable frame; sneak-place to lay a run of up to eight |
| Solar Wing | Twice a Tier 2 solar panel; works only on the station |
| Gravity Plate | Normal gravity in the six blocks above it |
| Hydroponics Tray | Grows crops under a Hull Window inside breathable air, no water needed |
| Telescope | The Observatory's eyepiece (see above) |
| Docking Ring | 3×3 landing plate; see [Landing Pads](Landing-Pads) |

## Sky, clock and weather

The station's clock is fixed in permanent daylight and it has no weather. The homeworld fills the lower sky to the
east, turning slowly, with a blue rim, a day side and a night side sprinkled with city lights; the sun stands high
in a black, star-filled sky with the galaxy and two nebulae behind it, and an operator can show the sunrise through
the homeworld's rim with `/nerospace gallery sky station sunrise` (the full sky is drawn on NeoForge for Minecraft
26.3; elsewhere the station shows its colours with the vanilla sky shapes). Station gravity is about
a tenth of the Overworld's, except above Gravity Plates. See [Planet Clock and Weather](Planet-Clock-and-Weather).

**Beds** never let you sleep on the station ("The station's lights never dim"), but a bed there still sets your
respawn point and never explodes.

**Open water freezes:** the station is cold, so water under the open sky turns to ice unless the block light at the
water is 10 or more, and sugar cane planted by it breaks. Keep a light source within a few blocks of the water, or
farm under the station roof.

## Server settings

| Key | Default | Range | Effect |
|---|---|---|---|
| `stations.maxMembers` | `16` | 0–16 | members an owner may add |
| `stations.maxModules` | `8` | 0–8 | modules a station may have |
| `stations.minSpacing` | `512` | 128–4,096 | blocks (on each axis) a placed station keeps from every other station and the shared platform |
| `stations.maxRadius` | `30000` | 1,024–1,000,000 | how far from 0,0 (on each axis) a station may be placed |
| `stations.freeDockRadius` | `2048` | 0–8,192 | how close a free dock must be to where a rocket last stood in the station dimension to be listed; `0` turns free docks off |

## Privacy

A station stores its owner's id and its members' ids, only to decide who may manage and use it. A placed station
stores the same things plus its chosen centre, which is tied to you only while you own the station. A Docking Ring stores no player at all,
so a free dock never records who built it. Erasing your
data removes you from every member list and clears your ownership; the station itself stays. See the mod's
privacy notice for details.
