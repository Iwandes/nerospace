# Landing Pads

Where does a rocket come down? From 2.0 a rocket **always lands centred on a pad, facing the pad's front**,
or — when there is no pad to use — on an automatically placed **Landing Pod**. Arrivals are animated: the
rocket appears high above its landing spot and descends with its engines firing, with you seated inside
until touchdown.

## How the landing spot is chosen

The first rule that finds a spot wins:

1. **The pad you picked.** Use the pad button on the rocket's flight console to choose a named pad in the
   destination. If that pad has been broken, you are told and the next rules apply.
2. **Home pad.** A rocket flying back to the world it came from returns to the pad it launched from, if that
   pad still stands — named or not.
3. **Paired pad.** If you launched from a named pad (for example "Home Base") and the destination has a pad
   with exactly the same name, you land there. Automatic labels such as "Pad 3" never pair. If several pads
   share the name, the oldest one wins; a far-away, ambiguous name is ignored.
4. **Nearest pad.** Otherwise the nearest registered pad within 2048 blocks of the arrival area.
5. **Landing Pod.** If none of the above applies, a Landing Pod is placed for you.

A pad with another rocket already standing on it is skipped — a landing never stacks rockets.

## Registering a pad

Use a **Name Tag** on a [Rocket Launch Pad](Rocket-Launch-Pad) to register it as a landing spot (the tag's
name labels it; an unnamed tag gives "Pad N"). Sneak and use an empty hand on it to unregister it. Pads
registered before 2.0 keep working; the first time a rocket lands on one, its centre and front are worked out
and remembered.

A pad cannot be renamed while someone else is sitting in a rocket parked on it.

## Docking Ring

The **Docking Ring** is a 3×3 landing plate with hazard-stripe lights. Place it with room for the whole
3×3; it registers itself as a landing spot straight away (no Name Tag needed) and faces the way you were
looking. Breaking any part removes the whole ring and drops it as one item. Rockets of any tier can take off
from a Docking Ring.

At an orbital station, arriving rockets prefer the station's Docking Ring, then its original landing pad.
New stations are built with a Docking Ring; older stations keep their pad. A ring placed in the station
dimension outside every station's claim is a [free dock](Orbital-Stations#free-docks). Rename the ring item in an
anvil first to give it a name.

## Landing Pods

A Landing Pod needs a flat, solid 5×5 patch (no water, lava, leaves, ice or magma), three blocks of open space
above it, no structure in the way, and some distance from other players' stations you cannot use, from cargo
pads you have no access to, from terraform regions that other mods register with Nerospace, and from other pads.
Nothing else counts as off-limits: a Terraformer's own ground and other players' bases are not avoided. The search
spirals out from the arrival area — up to 24 blocks — and, if nothing fits, builds a small platform in the air
above, still keeping the same distances. An empty pod nearby is reused, and pods are
kept at least 32 blocks apart so they don't litter the landscape. Breaking a Landing Pod removes it from the
list of landing spots.

## Choosing where to dock or land

Both the rocket's flight console and the [Launch Controller](Launch-Controller) have a **Dock at** row (when the
destination is the Orbital Station) or a **Land at** row (any other world). Click it to open the list of places
you can arrive at; pick one with the mouse, or with **Up/Down** and **Enter**. The mouse wheel scrolls a long list,
and **Esc** closes it. The small **»** button beside the row steps to the next entry without opening the list.

- **Dock at** lists the shared origin platform, every station you may dock at, the
  [free docks](Orbital-Stations#free-docks) near where the rocket last stood in the station dimension (with their
  distance), and **New Station**. Each entry says how it relates to you: *yours*, *member*, *public*, *origin* or
  *free dock*. Stations that only accept their members' rockets are not listed unless you are a member. The **»**
  button steps through stations only.
- **Land at** lists *Nearest pad* and every pad on the destination world, with the distance from your rocket for
  pads on the world you are on.
- **Your own station comes first.** A new rocket docks at the first station you own, else the first one you are a
  member of, else the shared origin platform at 0, 64. The same rule fills in the dock when you switch the
  destination to the Orbital Station and when you open either console, but only until you pick a dock yourself:
  choosing the origin platform on purpose sticks, and upgrading the rocket's tier never changes your choice.
- If the server turns down a choice (for example the station was abandoned a moment ago), the console shows the
  reason in an amber notice and keeps the previous choice.
- **On arrival** the chat line names where you docked. If your choice could not be honoured, the reason comes first:
  the station is gone, it now only accepts its members, you had no Station Charter for a new station, or the station
  registry is full. You then dock at the shared origin platform.
- The station list refreshes by itself when you found, rename or abandon a station, and when you arrive in the
  station dimension.

## Founding a station from a rocket

Choosing **New Station** in the dock list needs a **Station Charter** in your inventory, which is used up when
you arrive (never at launch, so an aborted trip costs nothing). Without a charter the console says so and keeps
your previous choice, and the **»** button skips New Station. If the charter is gone by the time you arrive, you
dock at the shared origin platform and the arrival message tells you why.

## Good to know

- If a pad is broken while your rocket is coming down, it steers to the next spot instead.
- If you disconnect during the descent, the rocket still finishes landing.
- If a rocket parked on your pad only shows up while you are coming down (its area had not loaded yet), your
  rocket steers to the next spot, or sets down on a Landing Pod nearby; it never lands on top of another rocket.
- A rocket saved halfway through a descent finishes landing the moment its area loads again.
- A rider can launch once every five seconds. A launch inside that window is refused with a message, before any
  launch fee is charged.
- Landings only look at pads they could actually use and at the area around the landing spot, so a world with many
  pads does not load chunks all over the planet.
- Server owners can shorten the Landing Pod search with `landing.podSearchRadius` (default 24, range 6–24 blocks).
- Rocket tiers and their destinations: Tier 1 reaches the Orbital Station and [Luna](Luna); Tier 2 adds
  [Greenxertz](Greenxertz) and [Mars](Mars); Tier 3 adds [Cindara](Cindara); Tier 4 adds [Glacira](Glacira) and
  [Nyxara](Nyxara). Each tier keeps every destination of the tiers below it, and every tier can fly home.
