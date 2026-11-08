# Creatures

The native life of the Nerospace planets. Each has bespoke geometry, a walk animation, a distinct
hand-tuned **texture palette**, and an **emissive glow** on its eyes/crystals/embers that shines in
the dark.

## Greenxertz

### Xertz Stalker — "Crystal Hunter" (hostile)

A tall, upright crystalline biped — the planet's apex predator. Uses a light-independent spawn rule, so
it's dangerous **day and night**. Strides toward prey on two legs with down-swept blade-arms.

**Look:** deep emerald/teal crystal hide shot through with angular cyan facets; its blade-arms, crest
and back-fins are brighter crystal with glowing edges. The facets and eyes are emissive, so it reads as
a cold cyan silhouette in the dark.

**Sounds:** chitters while idle, shrieks when hurt, shatters on death. *(Placeholder audio aliases
vanilla spider sounds until bespoke audio is recorded — see "Sound notes" below.)*

### Quartz Crawler — "Geode Skitterer" (neutral)

A low, domed six-legged crawler with a back crystal cluster. Skitters along the ground and leaves you
alone unless provoked.

**Look:** a pale, milky quartz carapace divided by darker seams, with faint rose-quartz veins. Only the
green crystal cluster on its back (and its eyes) glow — the plated shell itself stays solid, non-emissive
quartz.

**Sounds:** skitters while idle, cracks when hurt, crumbles on death. *(Placeholder aliases vanilla
silverfish sounds.)*

### Greenling — "Sprout" (passive)

A small, chubby grounded critter with an oversized cheeky head and a leaf crest. Harmless ambient life;
toddles around on two stubby legs.

**Look:** soft, friendly mottled greens with a lighter belly and gentle darker spots, topped by a leafy
crest. Deliberately the *only* creature that barely glows — just a tiny glint in its big dark eyes.

**Sounds:** chirps while idle, squeaks when hurt, wilts on death. *(Placeholder aliases vanilla panda
sounds for a soft, gentle voice.)*

### Alien Villager — "The Wary" (social)

The social aliens of the planets — see the dedicated **[Alien Villagers](Alien-Villagers)** page for the
full system (appearance, gifting, trust tiers, trading). In brief: a wary-neutral humanoid with a domed
head and crystalline shoulder growths that strides on two legs. Each individual is a slightly different
green/steel shade, and the skin shifts per planet (Cindara ember, Glacira frost). Win its trust to trade
and to grow a **[Village Core](Village-Core)** village.

## Cindara

### Cinder Stalker — "Magma Hulk" (hostile)

A heavy, horned quadruped of the volcanic moon, ridged with obsidian back-plates and glowing embers.
Fire-immune and aggressive — the main threat on Cindara.

**Look:** a charcoal/obsidian hide veined with branching, glowing orange-red lava cracks; obsidian horns
and back-plates whose ridge seams burn hot. The cracks and ember eyes are emissive, so it smoulders in
the dark.

**Sounds:** smoulders while idle, roars when hurt, cools on death. *(Placeholder aliases vanilla blaze
sounds.)*

## Glacira

### Frost Strider — "Ice Stilt-Walker" (hostile)

A tall, gangly predator stalking the frozen moon on four stilt legs, with a long low-slung neck and a
row of ice-shard back spines. Freeze-immune; slightly faster but more fragile than the Cinder Stalker.

**Look:** pale glacial plates over a deep-blue hide; the shard spines and eyes glow a cold frost-white.

**Sounds:** creaks while idle, splinters when hurt, shatters on death. *(Placeholder aliases vanilla
stray sounds.)*

## Bosses

### Ruin Warden

A towering crystalline construct that guards the **[Mega-City](Alien-Structures)** keep. Heavily
armoured, resists knockback, hits hard, and hunts players on sight — clearing it is the price of the
keep's grand vault.

**Look:** a hulking dark-crystal body veined with glowing violet, with jagged crystal shoulder spires
and heavy limbs. **Stats:** 120 HP, high armour + knockback resistance, strong melee.

## Terraformed worlds — livestock

Mature ("Living") terraformed ground wakes a breedable livestock species per planet — the seeded
ecosystem of deeper terraforming. All three are peaceful, follow their breed food, panic from damage,
and spawn naturally on Living ground (the [Terraformer](Terraformer) also seeds starter herds).

### Meadow Loper (Greenxertz — cow-analogue)

A placid bulk grazer: a deep barrel body on sturdy legs, broad low head, small horn nubs, lazy
swishing tail. **Breeds with wheat; drops Loper Haunch** (hearty food).

### Ember Strutter (Cindara — chicken-analogue)

A skittish little ground bird with ember-orange feathers, a comb and quick two-legged strut.
Fire-proof, like everything that survives Cindara. **Breeds with seeds; drops Strutter Drumstick**
(food). Its ember flecks glow faintly in the dark.

### Woolly Drift (Glacira — sheep-analogue)

A shaggy snowdrift of a grazer: one big rounded fleece ridged with wind-packed tufts over stubby
legs. Cold-proof (no freeze build-up). **Breeds with wheat; drops Drift Fleece** (crafts into 4
string).

### Moon Hopper (Luna)

A small, quick grazer that bounds along on big hind legs. It lives on the Lunar Meadow, Luna's terraformed Living
ground; the Terraformer stocks a starter group. **Breeds with carrots; drops up to one Regolith.** 6 HP.

### Dust Strider (Mars)

A long-legged herd grazer of the Red Steppe, Mars' terraformed Living ground. Sturdier and a little slower than the
others. **Breeds with wheat; drops up to two Rust Pigment.** 14 HP.

### Dusk Moth (Nyxara)

A fragile ambient flier with glowing eye-spot wings, found in the Twilight Grove (Nyxara's terraformed Living ground)
wherever no lamp lights the ground (block light 7 or lower). It flutters away when hurt and does not breed. **Drops up
to one Night Fibre.** 4 HP.

The Moon Hopper, Dust Strider and Dusk Moth are native to their worlds: nothing on their home planet (vacuum, cold,
radiation, weather) harms them. Each has a spawn egg in the creative tab.

---

## Sound notes

Each creature has three registered sound events under the `nerospace` namespace —
`entity.<creature>.ambient`, `.hurt`, and `.death` — defined in `assets/nerospace/sounds.json`. No
`.ogg` files ship yet: each entry currently **aliases a fitting vanilla sound event** (`"type":
"event"`) as a placeholder, chosen to suit the creature (spider for the Stalker, silverfish for the
Crawler, panda for the Greenling, blaze for the Cinder Stalker).

Because the events are registered under our own ids and every sound carries a Nerospace **subtitle**
key (e.g. `subtitles.nerospace.greenling.ambient`), dropping in real audio later is purely a resource
change: add `.ogg` files under `assets/nerospace/sounds/entity/<creature>/` and switch the sounds.json
entry from an `event` alias to a `sound` file. No code changes are needed.

---

**Spawn eggs:** all eight creatures — the five natives (Xertz Stalker, Quartz Crawler, Greenling,
Cinder Stalker, Frost Strider) and the three livestock (Meadow Loper, Ember Strutter, Woolly Drift) —
have creative spawn eggs in the Nerospace tab, each icon coloured to match its creature. For a quick
look at every creature, use the creative gallery command **`/nerospace gallery`**, which spawns each
one frozen in place (no AI) under its name, sets the Mars natives beside the rover, and shows every
block and item alongside.
