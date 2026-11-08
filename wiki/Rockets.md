# Rockets

The four crewed rockets that carry you off-world, what each one looks like, and what it does on the
pad and in flight. For fuel numbers, pad sizes and destinations see [Rocket Launch Pad](Rocket-Launch-Pad);
for the uncrewed freight hull see [Cargo Rocket](Cargo-Rocket).

## Overview

Deploy a rocket item onto a [launch pad](Rocket-Launch-Pad), fuel it, right-click it to open the flight
console and press **Launch**. You board as the engines light and arrive on the destination pad a few
seconds later. Each tier reaches every destination of the tiers below it plus its own, so a Tier 3 or Tier 4
rocket still flies to [Luna](Luna) and [Mars](Mars). Every rocket can fly home.

| Tier | Reaches | Pad | Look |
| --- | --- | --- | --- |
| **Tier 1** | Orbital Station, Luna | one pad block | Red livery, single bell, four raked fins, four landing legs, porthole cockpit |
| **Tier 2** | Orbital Station, Luna, Greenxertz, Mars | 3×3 | Nerosium-purple livery, twin strap-on boosters, spinning radar dish, two fins |
| **Tier 3** | Orbital Station, Luna, Greenxertz, Mars, Cindara | ringed 3×3 | Gold / green livery, main bell ringed by four verniers, fold-out solar wings, scanning dish boom, panoramic canopy, long nose |
| **Tier 4** | Orbital Station, Luna, Greenxertz, Mars, Cindara, Glacira, Nyxara (after Deep Space progress) | Heavy Launch Complex | Ice-blue livery, wide five-engine core, four boosters, grid fins, heavy legs, the tallest nose |

Each tier is bigger than the last, both in geometry and in scale, so the progression reads from across
the base.

## The cockpit

Every hull has a window band at the rider's eye height, cut out of the hull and glazed from the inside
with tinted glass. Behind it is a lit cabin: a console with a screen under the forward window, a ceiling
lamp, and light strips on the side walls. The rider is seated so their eyes sit exactly on the window
band, so in first person you look out through the glass at the pad falling away; from outside, the
cabin glows through the windows at night.

The **hatch** on the south side stands open while nobody is aboard, with a ladder down to the pad, and
seals the moment you launch. While it is open you can see straight into the cockpit through the doorway.

## Flying with friends (crew)

Every rocket seats **up to four players** (a server can lower this with `rockets.maxCrew`). All four tiers
have four seats; bigger tiers go further, not fuller.

- **Boarding.** Right-click the rocket to open its console and press **Take seat**. Nobody can put you in a
  rocket: you always board yourself. The button reads **Seated** once you are aboard and **Full** when every
  seat is taken; hover it to see how many are aboard. To climb out again before launch, close the console
  and sneak.
- **The pilot** is the first player aboard. Only the pilot can press **Launch**, pick the destination, or
  choose where to dock or land. Anyone else who tries is told so. Boarding later never takes the pilot's
  seat. On an empty rocket, pressing **Launch** boards you as the pilot, as before.
- **Everyone goes.** The whole crew travels together, rides the descent down, and steps out beside the
  rocket when it lands. Each of you gets the arrival message, your own Star Guide progress and your own
  first-visit record.
- **Oxygen is shared.** With two or more aboard, the rocket needs enough onboard oxygen for every seat
  for the flight. The consoles show **CREW n/4 · O₂ FOR n**: if the oxygen covers fewer seats than are
  filled, the status reads **NEEDS OXYGEN**. Load more from an Oxygen Generator or fly with fewer crew.
  On arrival the rocket's leftover oxygen is shared out between the crew as your reserve. A solo flight
  needs no onboard oxygen, as before.
- **Cooldown.** Each player has their own short launch cooldown, so riding along doesn't skip yours. If
  someone aboard is still cooling down, the pilot is told "a crew member" is, and that player sees their
  own wait.
- **Fees.** If the server runs NeroEconomy, the launch fee is charged **once per flight, to the pilot**.
- **Stations.** A members-only station is checked against the **pilot**. Crew who aren't members still
  arrive and are visitors there.
- **Disconnects.** If the pilot drops out mid-flight, the next player aboard takes over for the landing
  (their Station Charter is never spent on a "New Station" they didn't pick). A crew member who drops out
  mid-flight rejoins beside the rocket, not in the sky, as long as the server hasn't restarted.
- The rocket keeps no record of who flew in it.

## On the pad

- Landing legs are planted; on Tier 3 the solar wings stand deployed.
- Navigation beacons blink red (west) and green (east); a white strobe flashes at the nose.
- Tier 2's radar dish spins; Tier 3 and Tier 4 carry a dish on a side boom that scans back and forth.
- A fuelled hull vents a little cold vapour at its wall.

## Launch sequence

1. **Ignition** — the hatch seals, exhaust plumes bloom and flicker under every bell (white-hot core,
   tier-coloured fringe), the hull shudders, and a cloud rolls outward across the pad. Solar wings and
   the dish boom fold in.
2. **Climb** — the landing legs sweep out and up along the hull, the engine bells gimbal, and on Tier 4
   the grid fins deploy.
3. **Staging** — past two-thirds of the climb, Tier 2 and Tier 4 boosters go dark and fall away.
4. **Arrival** — the rocket re-appears on the destination pad, legs down and hatch opening.

The hull and its glass are lit by the world; only the lamps, beacons and exhaust are full-bright, so a
rocket parked at night is a dark silhouette with glowing windows.

## Cargo Rocket

The [Cargo Rocket](Cargo-Rocket) borrows the Tier 2 hull and livery at a slightly larger scale. Nobody
boards it, so its hatch stays sealed and it carries no ladder.

## For contributors

The rocket geometry, the Blockbench files under `art/blockbench/entity/` and the texture sheets are all
generated from one spec, `tools/rocket_models.py`; the animation lives in `RocketModel`. Edit the
Python, run it with `--multiloader` (or `./gradlew genAssets`) and rebuild.
