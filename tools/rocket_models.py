#!/usr/bin/env python3
"""
Rocket entity models - the single source of truth for the four crewed rocket hulls (the cargo
rocket borrows the Tier 2 hull).

The old rockets were a handful of unrotated boxes whose UV layout was mirrored by hand between the
Java models and the texture painter. The detailed hulls have ~60-100 cubes each, rotated/pivoted
bones (raked fins, folding legs, gimballing engines, hatches, solar wings, radar dishes), cut-out
windows with real glass, a lit cockpit interior and additive exhaust plumes - so everything that
must agree is derived from ONE spec here:

  * Java geometry  -> common/.../client/RocketGeometry.java  (the `// rocket_models:begin/end` block;
                      one method per tier building a bone tree, cubes filtered by render PASS),
  * Blockbench     -> art/blockbench/entity/rocket_t{1..4}.bbmodel  (bones with pivots + rotations,
                      a viewing/hand-off surface; NOT synced back - edit this file instead),
  * textures       -> common/.../textures/entity/rocket_t{1..4}.png (256x256, every cube's faces
                      painted by MATERIAL, window holes cut, plume gradients, per-tier palette).

Render passes (see RocketModel/RocketRenderer): HULL (cutout, lit), GLASS (translucent, lit),
GLOW (emissive, full-bright: lamps, screens, beacons, engine throats), PLUME (additive exhaust).
Every pass bakes the SAME bone skeleton, so the Java animation addresses bones by name in all four.

Hull widths (2.1.0-beta.1): the crewed cabins are 14 px wide on Tiers 1-3 and 16 px on Tier 4 (one
pixel wider per side than 2.0.0) so four seated crew fit inside - the seat offsets in
RocketTier.seatOffsets() are tuned to these walls; change `half` and the seats together.

Coordinates in the spec are "ground space": x/z centred on the hull, h = height above the pad in
model pixels (ground = 0). Java is Y-down with the ground at y=24 (jy = 24 - h); Blockbench is Y-up
(bb_y = h). Rotations are given in degrees with the JAVA sign convention (ModelPart ZYX):
  * yRot +phi turns local +x toward -z;  zRot -theta tilts a down-hanging part toward local +x;
  * xRot +theta tilts a down-hanging part toward local +z.
Animated bones are always a yaw bone (points local +x at the part's outward direction) wrapping an
"arm" bone that swings on ONE axis - so the Java animation is a single lerp per part.

Usage:
  python tools/rocket_models.py --multiloader          # regenerate Java block + bbmodels + textures
  python tools/rocket_models.py --multiloader --check  # report drift, write nothing (exit 1 if stale)
"""
import json
import math
import os
import random
import sys

from nerospace_target import resolve, target_label

try:
    from PIL import Image
except ImportError:  # pragma: no cover - textures are committed art
    Image = None

JAVA = "src/main/java/za/co/neroland/nerospace/client/RocketGeometry.java"
TEX_DIR = "src/main/resources/assets/nerospace/textures/entity"
BB_DIR = "art/blockbench/entity"
BEGIN = "// rocket_models:begin"
END = "// rocket_models:end"
GROUND = 24
SHEET = 256
PASSES = ("hull", "glass", "glow", "plume")
FACES = ("north", "east", "south", "west", "up", "down")
CLEAR = (0, 0, 0, 0)

TIERS = {
    1: dict(name="rocket_t1", method="tier1", accent=(224, 58, 58), trim=(150, 34, 34),
            glass=(120, 190, 230), plume=((255, 252, 225), (255, 176, 64), (232, 74, 32)), digit="1", eye=43),
    2: dict(name="rocket_t2", method="tier2", accent=(179, 39, 158), trim=(106, 31, 140),
            glass=(178, 150, 236), plume=((255, 236, 255), (226, 96, 236), (128, 44, 170)), digit="2", eye=49),
    3: dict(name="rocket_t3", method="tier3", accent=(240, 200, 80), trim=(60, 170, 90),
            glass=(156, 232, 196), plume=((255, 255, 232), (255, 212, 88), (84, 196, 118)), digit="3", eye=53),
    4: dict(name="rocket_t4", method="tier4", accent=(120, 210, 240), trim=(60, 130, 200),
            glass=(176, 228, 255), plume=((244, 252, 255), (136, 214, 255), (56, 116, 228)), digit="4", eye=57),
}


# ---------------------------------------------------------------------------
# Spec DSL
# ---------------------------------------------------------------------------

class Cube:
    def __init__(self, material, box, pass_, cuts, omit, params):
        self.material = material
        self.box = box            # (x0, h0, z0, w, hgt, d) ground space, bone-unrotated frame
        self.pass_ = pass_
        self.cuts = tuple(cuts)   # (face, fx, fy, fw, fh) face-local, v downward from the top
        self.omit = frozenset(omit)
        self.params = dict(params)
        self.uv = None

    def key(self):
        _, _, _, w, h, d = self.box
        return (self.material, tuple(sorted(self.params.items())), w, h, d, self.cuts, self.omit)


class Bone:
    def __init__(self, name, parent, pivot, rot):
        self.name = name
        self.parent = parent      # Bone or None
        self.pivot = pivot        # absolute ground space (x, h, z)
        self.rot = rot            # degrees (rx, ry, rz), Java sign convention
        self.cubes = []
        self.children = []


class Rig:
    def __init__(self, tier):
        self.tier = tier
        self.spec = TIERS[tier]
        self.bones = []
        self.by_name = {}
        self.body = self.bone("body", None)

    def bone(self, name, parent="body", pivot=(0, 0, 0), rot=(0, 0, 0)):
        if name in self.by_name:
            raise SystemExit("duplicate bone %s" % name)
        if isinstance(parent, str):
            parent = self.by_name[parent]
        b = Bone(name, parent, tuple(float(v) for v in pivot), tuple(float(v) for v in rot))
        if parent is not None:
            parent.children.append(b)
        self.bones.append(b)
        self.by_name[name] = b
        return b

    def cube(self, bone, material, x0, h0, z0, w, hgt, d, pass_="hull", cuts=(), omit=(), **params):
        if isinstance(bone, str):
            bone = self.by_name[bone]
        if pass_ not in PASSES:
            raise SystemExit("bad pass %s" % pass_)
        c = Cube(material, (float(x0), float(h0), float(z0), float(w), float(hgt), float(d)),
                 pass_, cuts, omit, params)
        bone.cubes.append(c)
        return c

    def cubes(self):
        for b in self.bones:
            for c in b.cubes:
                yield b, c


# ---------------------------------------------------------------------------
# Geometry helpers (shared by the tiers)
# ---------------------------------------------------------------------------

CARDINALS = (("e", 1, 0, 0), ("w", -1, 0, 180), ("s", 0, 1, -90), ("n", 0, -1, 90))
CORNERS = (("ne", 1, -1, 45), ("se", 1, 1, -45), ("sw", -1, 1, -135), ("nw", -1, -1, 135))


def centred(rig, bone, material, h0, w, hgt, d=None, pass_="hull", **kw):
    d = w if d is None else d
    return rig.cube(bone, material, -w / 2, h0, -d / 2, w, hgt, d, pass_, **kw)


def nose(rig, bone, h0, steps, material="hull", **kw):
    """A stepped (pixel-cone) nose: steps = [(width, height), ...] from the base up."""
    h = h0
    for i, (w, hh) in enumerate(steps):
        p = dict(kw)
        if i == 0:
            p["band_top"] = True
        centred(rig, bone, material, h, w, hh, **p)
        h += hh
    return h


def engine(rig, parent, name, cx, cz, h_mount, steps, plume_len, glow=True):
    """A gimballing bell under (cx, cz) hanging from h_mount. steps = [(width, height), ...] from
    the throat (top) down to the lip (bottom, drawn open so you can see the lit throat). A child
    `plume_<name>` bone pivots at the lip exit and carries the three nested exhaust cubes."""
    bone = rig.bone("engine_" + name, parent, pivot=(cx, h_mount, cz))
    h = h_mount
    for i, (w, hh) in enumerate(steps):
        last = i == len(steps) - 1
        rig.cube(bone, "engine", cx - w / 2, h - hh, cz - w / 2, w, hh, w,
                 omit=("down",) if last else (), lip=last)
        h -= hh
    lip_w = steps[-1][0]
    if glow:
        tw = max(1.0, lip_w - 2)
        rig.cube(bone, "throat", cx - tw / 2, h + steps[-1][1] - 1, cz - tw / 2, tw, 1, tw, "glow")
    plume = rig.bone("plume_" + name, bone, pivot=(cx, h, cz))
    for mat, wf, lf in (("plume_outer", 1.2, 1.0), ("plume_mid", 0.8, 0.8), ("plume_core", 0.45, 0.55)):
        w = max(1.0, round(lip_w * wf))
        length = round(plume_len * lf)
        rig.cube(plume, mat, cx - w / 2, h - length, cz - w / 2, w, length, w, "plume")
    return bone


def legs(rig, parent, half, h_pivot, length, tilt=30, strut=2, foot=4):
    """Four corner landing legs: yaw bone -> arm bone (zRot = -tilt, folds to -180 in flight)."""
    off = half + 0.5 + strut / 2
    for tag, sx, sz, yaw in CORNERS:
        px, pz = sx * off, sz * off
        yaw_bone = rig.bone("leg_" + tag, parent, pivot=(px, h_pivot, pz), rot=(0, yaw, 0))
        arm = rig.bone("leg_%s_arm" % tag, yaw_bone, pivot=(px, h_pivot, pz), rot=(0, 0, -tilt))
        rig.cube(arm, "leg", px - strut / 2, h_pivot - length, pz - strut / 2, strut, length, strut)
        rig.cube(arm, "leg_piston", px - strut / 2 - 0.5, h_pivot - round(length * 0.55), pz - strut / 2 - 0.5,
                 strut + 1, round(length * 0.3), strut + 1)
        rig.cube(arm, "foot", px - foot / 2 + strut / 2, h_pivot - length - 1, pz - foot / 2, foot, 1, foot)


def fins(rig, parent, half, h_top, span, height, rake=18, dirs=CARDINALS, beacons=True):
    """Raked tail fins hanging from h_top at the hull wall (yaw bone -> arm bone with zRot=-rake)."""
    for tag, dx, dz, yaw in dirs:
        px, pz = dx * half, dz * half
        yaw_bone = rig.bone("fin_" + tag, parent, pivot=(px, h_top, pz), rot=(0, yaw, 0))
        arm = rig.bone("fin_%s_arm" % tag, yaw_bone, pivot=(px, h_top, pz), rot=(0, 0, -rake))
        rig.cube(arm, "fin", px - 2, h_top - height, pz - 0.5, span + 2, height, 1)
        rig.cube(arm, "fin_edge", px + span - 0.5, h_top - height, pz - 0.75, 1.5, round(height * 0.6), 1.5)
        if beacons and tag in ("e", "w"):
            nav = rig.bone("nav_%s" % ("a" if tag == "w" else "b"), arm, pivot=(px, h_top, pz))
            rig.cube(nav, "beacon_red" if tag == "w" else "beacon_green",
                     px + span - 0.25, h_top - height - 0.5, pz - 1, 1.5, 1.5, 2, "glow")


def cabin(rig, bone, half, h_hull0, h_hull1, liner_h0, windows, hatch, digit, bands, portholes=()):
    """The crewed hull section: outer hull cube with window + hatch holes, a lit interior liner,
    inset glass panes, console, ceiling lamp and wall light strips. `windows` = (w, hgt, h0) one
    centred pane per side; `hatch` = (w, hgt, h0) on the south face."""
    hull_h = h_hull1 - h_hull0
    liner_half = half - 1.5
    liner_h = h_hull1 - liner_h0
    ww, wh, wh0 = windows
    hw, hh, hh0 = hatch
    if wh0 + wh / 2 != rig.spec["eye"]:
        raise SystemExit("%s: window band %s..%s is not centred on the rider eye line %s"
                         % (rig.spec["name"], wh0, wh0 + wh, rig.spec["eye"]))

    def cut(face, cube_w, cube_top, w, hgt, h0):
        return (face, (cube_w - w) / 2, cube_top - (h0 + hgt), w, hgt)

    hull_cuts = [cut(f, 2 * half, h_hull1, ww, wh, wh0) for f in ("north", "east", "south", "west")]
    hull_cuts.append(cut("south", 2 * half, h_hull1, hw, hh, hh0))
    liner_cuts = [cut(f, 2 * liner_half, h_hull1, ww, wh, wh0) for f in ("north", "east", "south", "west")]
    liner_cuts.append(cut("south", 2 * liner_half, h_hull1, hw, hh, hh0))
    centred(rig, bone, "hull", h_hull0, 2 * half, hull_h, cuts=hull_cuts, digit=digit,
            bands=tuple((h_hull1 - b1, h_hull1 - b0) for b0, b1 in bands),
            portholes=tuple(h_hull1 - p for p in portholes))
    centred(rig, bone, "liner", liner_h0, 2 * liner_half, liner_h, cuts=liner_cuts)
    # Glass panes sit between the hull wall and the liner (no coplanar faces on either side).
    inset0, inset1 = half - 1.25, half - 0.75
    rig.cube(bone, "glass", -ww / 2, wh0, -inset1, ww, wh, 0.5, "glass")
    rig.cube(bone, "glass", -ww / 2, wh0, inset0, ww, wh, 0.5, "glass")
    rig.cube(bone, "glass", -inset1, wh0, -ww / 2, 0.5, wh, ww, "glass")
    rig.cube(bone, "glass", inset0, wh0, -ww / 2, 0.5, wh, ww, "glass")
    # Interior: console under the forward window, screen on its face, ceiling lamp, wall strips.
    cw = min(8.0, 2 * liner_half - 2)
    rig.cube(bone, "console", -cw / 2, wh0 - 4, -liner_half + 0.25, cw, 3, 2)
    rig.cube(bone, "screen", -cw / 2 + 1, wh0 - 3.5, -liner_half + 2.25, cw - 2, 2, 0.5, "glow")
    rig.cube(bone, "lamp", -2, h_hull1 - 1, -2, 4, 0.5, 4, "glow")
    for sx in (-1, 1):
        x = liner_half - 0.75 if sx > 0 else -liner_half + 0.25
        rig.cube(bone, "strip", x, wh0 - 6, -0.5, 0.5, 5, 1, "glow")
    # Hatch door (hinge on its +x edge) and the ladder down to the pad.
    door = rig.bone("hatch", bone, pivot=(hw / 2, hh0 + hh, half))
    rig.cube(door, "hatch", -hw / 2, hh0, half, hw, hh, 1)
    rig.cube(door, "hatch_window", -1, hh0 + hh - 3, half + 1, 2, 2, 0.25, "glass")
    ladder = rig.bone("ladder", bone, pivot=(0, hh0, half))
    for sx in (-1, 1):
        rig.cube(ladder, "ladder", sx * 2.5 - 0.5, 2, half, 1, hh0 - 2, 1)
    h = 4
    while h < hh0 - 1:
        rig.cube(ladder, "ladder", -2, h, half + 0.25, 4, 1, 0.5)
        h += 4


def booster(rig, parent, name, cx, cz, w, h0, h1, steps, bell_steps, plume_len, half, beacon=None):
    """A strap-on booster: body + nose + its own bell/plume, with two clamps left on the core."""
    bone = rig.bone("booster_" + name, parent, pivot=(cx, h0, cz))
    rig.cube(bone, "booster", cx - w / 2, h0, cz - w / 2, w, h1 - h0, w)
    nose(rig, bone, h1, steps, material="booster_nose")
    engine(rig, bone, "booster_" + name, cx, cz, h0, bell_steps, plume_len)
    if beacon:
        top = h1 + sum(hh for _, hh in steps)
        nav = rig.bone({"w": "nav_a", "e": "nav_b"}.get(name, "nav_" + name), bone, pivot=(cx, top, cz))
        rig.cube(nav, beacon, cx - 0.75, top, cz - 0.75, 1.5, 1.5, 1.5, "glow")
    # Clamps bridge the hull wall and the booster's inner face (they stay with the core).
    inner = abs(cx if cx else cz) - w / 2
    for h in (h0 + 4, h1 - 6):
        if cz == 0:
            x0 = half if cx > 0 else -inner
            rig.cube(parent, "clamp", x0, h, -1.5, inner - half, 1, 3)
        else:
            z0 = half if cz > 0 else -inner
            rig.cube(parent, "clamp", -1.5, h, z0, 3, 1, inner - half)


def antenna(rig, bone, h0, rod=4):
    centred(rig, bone, "mast", h0, 1, rod)
    strobe = rig.bone("strobe", bone, pivot=(0, h0 + rod, 0))
    centred(rig, strobe, "beacon_white", h0 + rod, 2, 2, pass_="glow")


def dish_on_mast(rig, bone, h0, mast=3, plate=6):
    centred(rig, bone, "mast", h0, 1, mast)
    dish = rig.bone("dish", bone, pivot=(0, h0 + mast, 0))
    centred(rig, dish, "dish_hub", h0 + mast, 2, 2)
    centred(rig, dish, "dish", h0 + mast + 2, plate, 1)
    rig.cube(dish, "mast", -0.5, h0 + mast + 3, -0.5, 1, 2, 1)
    strobe = rig.bone("strobe", dish, pivot=(0, h0 + mast + 5, 0))
    centred(rig, strobe, "beacon_white", h0 + mast + 5, 2, 1.5, pass_="glow")


def dish_on_boom(rig, bone, half, h, reach=5, plate=5):
    """A side-scanning dish on a boom off the north face; the boom folds up for launch."""
    mast = rig.bone("dish_mast", bone, pivot=(0, h + 0.5, -half))
    rig.cube(mast, "mast", -0.5, h, -half - reach, 1, 1, reach)
    dish = rig.bone("dish", mast, pivot=(0, h + 0.5, -half - reach))
    rig.cube(dish, "dish_hub", -1, h - 0.5, -half - reach - 2, 2, 2, 2)
    rig.cube(dish, "dish", -plate / 2, h + 0.5 - plate / 2, -half - reach - 3, plate, plate, 1)


def solar_wings(rig, bone, half, h_top, length, width):
    """Fold-out solar wings on the east/west walls: yaw bone -> arm (deployed zRot=-90, folds to 0)."""
    for tag, dx, dz, yaw in CARDINALS[:2]:
        px = dx * (half + 1)
        yaw_bone = rig.bone("wing_" + tag, bone, pivot=(px, h_top, 0), rot=(0, yaw, 0))
        arm = rig.bone("wing_%s_arm" % tag, yaw_bone, pivot=(px, h_top, 0), rot=(0, 0, -90))
        rig.cube(arm, "solar", px - 0.5, h_top - length, -width / 2, 1, length, width)
        rig.cube(arm, "mast", px - 0.75, h_top - 1, -0.75, 1.5, 1, 1.5)


def grid_fins(rig, bone, half, h_top, size):
    """Four grid fins folded flat on the hull; deploy (zRot -> -80) once the rocket is climbing."""
    for tag, dx, dz, yaw in CARDINALS:
        px, pz = dx * (half + 1), dz * (half + 1)
        yaw_bone = rig.bone("grid_" + tag, bone, pivot=(px, h_top, pz), rot=(0, yaw, 0))
        arm = rig.bone("grid_%s_arm" % tag, yaw_bone, pivot=(px, h_top, pz))
        rig.cube(arm, "grid", px - 0.5, h_top - size, pz - size / 2, 1, size, size)


# ---------------------------------------------------------------------------
# The four tiers
# ---------------------------------------------------------------------------

BELL_L = ((6, 3), (8, 3), (10, 3))     # r=5 main bell
BELL_M = ((4, 3), (6, 3), (8, 3))      # r=4
BELL_S = ((3, 2), (5, 3))              # r=2.5 booster / outer engine
BELL_XS = ((2, 2), (3, 2))             # r=1.5 vernier


def build_t1():
    """Tier 1 'Sparrow': the classic single-stage hopper. Red livery, one bell, four raked fins,
    four landing legs, porthole cockpit."""
    rig = Rig(1)
    body = rig.body
    half = 7
    engine(rig, body, "main", 0, 0, 11, BELL_L, 40)
    centred(rig, body, "trim", 11, 16, 3)
    cabin(rig, body, half, 14, 49, 26, windows=(8, 6, 40), hatch=(6, 8, 28), digit="1",
          bands=((20, 23),))
    top = nose(rig, body, 49, ((12, 3), (10, 3), (8, 3), (6, 3), (4, 3), (2, 3)))
    antenna(rig, body, top, rod=4)
    fins(rig, body, half, 22, 5, 16)
    legs(rig, body, half, 16, 18)
    return rig


def build_t2():
    """Tier 2 'Kestrel': nerosium-purple hull, twin strap-on boosters that separate in flight, a
    spinning radar dish, two raked fins."""
    rig = Rig(2)
    body = rig.body
    half = 7
    engine(rig, body, "main", 0, 0, 11, BELL_L, 44)
    centred(rig, body, "trim", 11, 16, 3)
    cabin(rig, body, half, 14, 55, 35, windows=(8, 6, 46), hatch=(6, 8, 36), digit="2",
          bands=((20, 23), (53, 54)))
    top = nose(rig, body, 55, ((12, 3), (10, 3), (8, 3), (6, 3), (4, 3)))
    dish_on_mast(rig, body, top, mast=3, plate=6)
    for tag, cx, beacon in (("w", -10.5, "beacon_red"), ("e", 10.5, "beacon_green")):
        booster(rig, body, tag, cx, 0, 5, 8, 34, ((3, 3), (1, 2)), BELL_S, 26, half, beacon)
    fins(rig, body, half, 20, 5, 14, dirs=CARDINALS[2:], beacons=False)
    legs(rig, body, half, 16, 18)
    return rig


def build_t3():
    """Tier 3 'Falcon': the long-range cruiser. Gold/green livery, a main bell ringed by four
    verniers, fold-out solar wings, a scanning dish boom, panoramic canopy, long stepped nose."""
    rig = Rig(3)
    body = rig.body
    half = 7
    engine(rig, body, "main", 0, 0, 11, BELL_M, 46)
    for tag, sx, sz, _ in CORNERS:
        engine(rig, body, "vernier_" + tag, sx * 4.75, sz * 4.75, 10, BELL_XS, 18, glow=False)
    centred(rig, body, "trim", 11, 16, 3)
    cabin(rig, body, half, 14, 60, 41, windows=(10, 6, 50), hatch=(6, 8, 42), digit="3",
          bands=((22, 25), (26, 27)))
    top = nose(rig, body, 60, ((12, 3), (10, 3), (8, 4), (6, 4), (4, 4), (2, 4)))
    antenna(rig, body, top, rod=3)
    solar_wings(rig, body, half, 38, 14, 8)
    dish_on_boom(rig, body, half, 46)
    fins(rig, body, half, 20, 4, 16)
    legs(rig, body, half, 16, 18)
    return rig


def build_t4():
    """Tier 4 'Titan': the heavy. Wide ice-blue hull, five-engine cluster, four separating
    boosters, grid fins that deploy in flight, heavy legs, side dish boom, panoramic canopy."""
    rig = Rig(4)
    body = rig.body
    half = 8
    engine(rig, body, "main", 0, 0, 11, BELL_L, 52)
    for tag, sx, sz, _ in CORNERS:
        engine(rig, body, "outer_" + tag, sx * 5, sz * 5, 9, BELL_S, 30)
    centred(rig, body, "trim", 11, 18, 3)
    cabin(rig, body, half, 14, 64, 47, windows=(10, 6, 54), hatch=(6, 8, 48), digit="4",
          bands=((22, 26), (27, 28)), portholes=(41,))
    top = nose(rig, body, 64, ((14, 3), (12, 3), (10, 3), (8, 4), (6, 4), (4, 4), (2, 3)))
    antenna(rig, body, top, rod=3)
    for tag, cx, cz, beacon in (("w", -12, 0, "beacon_red"), ("e", 12, 0, "beacon_green"),
                                ("n", 0, -12, "beacon_accent"), ("s", 0, 12, "beacon_accent")):
        booster(rig, body, tag, cx, cz, 6, 8, 42, ((4, 3), (2, 3)), BELL_S, 30, half, beacon)
    grid_fins(rig, body, half, 50, 6)
    dish_on_boom(rig, body, half, 52, reach=5, plate=5)
    legs(rig, body, half, 16, 18, strut=3, foot=6)
    return rig


BUILDERS = {1: build_t1, 2: build_t2, 3: build_t3, 4: build_t4}


# ---------------------------------------------------------------------------
# UV packing (box UV: a (2w+2d) x (d+h) footprint per cube; identical cubes share one region)
# ---------------------------------------------------------------------------

def footprint(cube):
    _, _, _, w, h, d = cube.box
    return int(math.ceil(2 * w + 2 * d)), int(math.ceil(d + h))


def pack(rig):
    uniques = {}
    for _, c in rig.cubes():
        uniques.setdefault(c.key(), []).append(c)
    items = sorted(uniques.items(), key=lambda kv: (-footprint(kv[1][0])[1], -footprint(kv[1][0])[0]))
    x = y = shelf = 0
    for key, cubes in items:
        fw, fh = footprint(cubes[0])
        if x + fw > SHEET:
            x = 0
            y += shelf
            shelf = 0
        if y + fh > SHEET:
            raise SystemExit("%s: UV sheet overflow" % rig.spec["name"])
        for c in cubes:
            c.uv = (x, y)
        x += fw
        shelf = max(shelf, fh)
    return [cubes[0] for _, cubes in items]


# ---------------------------------------------------------------------------
# Java emission
# ---------------------------------------------------------------------------

def _f(v):
    v = float(v)
    return ("%d" % v if v == int(v) else ("%.4f" % v).rstrip("0")) + "F"


def _rad(deg):
    return _f(round(math.radians(deg), 4))


def jy(h):
    return GROUND - h


def emit_java_tier(rig):
    out = ["    private static void %s(PartDefinition root, Pass pass) {" % rig.spec["method"],
           "        CubeListBuilder c;"]
    for b in rig.bones:
        px, ph, pz = b.pivot
        if b.parent is None:
            rel = (px, jy(ph), pz)
            parent_var = "root"
        else:
            qx, qh, qz = b.parent.pivot
            rel = (px - qx, jy(ph) - jy(qh), pz - qz)
            parent_var = "b_" + b.parent.name
        by_pass = {}
        for c in b.cubes:
            by_pass.setdefault(c.pass_, []).append(c)
        if by_pass:
            out.append("        c = CubeListBuilder.create();")
            for p in PASSES:
                if p not in by_pass:
                    continue
                out.append("        if (pass == Pass.%s) {" % p.upper())
                for c in by_pass[p]:
                    x0, h0, z0, w, hgt, d = c.box
                    lx, ly, lz = x0 - px, jy(h0 + hgt) - jy(ph), z0 - pz
                    u, v = c.uv
                    faces = ""
                    if c.omit:
                        keep = [f for f in FACES if f not in c.omit]
                        faces = ", Set.of(%s)" % ", ".join("Direction." + f.upper() for f in keep)
                    out.append("            c.texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s%s);"
                               % (u, v, _f(lx), _f(ly), _f(lz), _f(w), _f(hgt), _f(d), faces))
                out.append("        }")
            cube_expr = "c"
        else:
            cube_expr = "CubeListBuilder.create()"
        rx, ry, rz = b.rot
        if any(b.rot):
            pose = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % (
                _f(rel[0]), _f(rel[1]), _f(rel[2]), _rad(rx), _rad(ry), _rad(rz))
        else:
            pose = "PartPose.offset(%s, %s, %s)" % (_f(rel[0]), _f(rel[1]), _f(rel[2]))
        decl = "PartDefinition b_%s = " % b.name if b.children else ""
        out.append('        %s%s.addOrReplaceChild("%s", %s, %s);' % (decl, parent_var, b.name, cube_expr, pose))
    out.append("    }")
    return "\n".join(out)


def emit_paths(rig):
    """Slash-separated bone paths (root first) so the Java model can index parts by name."""
    def path(b):
        return b.name if b.parent is None else path(b.parent) + "/" + b.name
    lines = ["    private static final String[] PATHS_%s = {" % rig.spec["method"].upper()]
    for b in rig.bones:
        lines.append('            "%s",' % path(b))
    lines.append("    };")
    return "\n".join(lines)


def emit_java(rigs):
    return "\n\n".join([emit_paths(r) for r in rigs] + [emit_java_tier(r) for r in rigs])


def write_java(rigs, check):
    path = resolve(JAVA)
    with open(path) as fh:
        src = fh.read()
    i, j = src.find(BEGIN), src.find(END)
    if i < 0 or j < 0:
        raise SystemExit("markers missing in " + JAVA)
    block = BEGIN + "\n" + emit_java(rigs) + "\n    " + END
    new = src[:i] + block + src[j + len(END):]
    if new == src:
        return False
    if not check:
        with open(path, "w", newline="\n") as fh:
            fh.write(new)
    return True


# ---------------------------------------------------------------------------
# Blockbench (.bbmodel, modded_entity) - a viewing / hand-off surface
# ---------------------------------------------------------------------------

def _uuid(seed):
    import hashlib
    h = hashlib.md5(seed.encode()).hexdigest()
    return "%s-%s-%s-%s-%s" % (h[0:8], h[8:12], h[12:16], h[16:20], h[20:32])


def _tex_entry(name):
    import base64
    png = resolve(TEX_DIR + "/" + name + ".png")
    src = ""
    if os.path.exists(png):
        with open(png, "rb") as fh:
            src = "data:image/png;base64," + base64.b64encode(fh.read()).decode("ascii")
    return {
        "path": os.path.normpath(png), "name": name + ".png", "folder": "entity",
        "namespace": "nerospace", "id": "0", "particle": True, "render_mode": "default",
        "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop", "frame_order": "",
        "frame_interpolate": False, "visible": True, "mode": "bitmap", "saved": True,
        "uuid": _uuid("tex:" + name), "relative_path": "../../../common/" + TEX_DIR + "/" + name + ".png",
        "source": src,
    }


def bbmodel(rig):
    name = rig.spec["name"]
    elements = []
    groups = {}
    for b in rig.bones:
        px, ph, pz = b.pivot
        rx, ry, rz = b.rot
        gid = _uuid(name + ":bone:" + b.name)
        groups[b.name] = {
            "name": b.name, "origin": [px, ph, pz], "rotation": [-rx, ry, -rz], "color": 0,
            "uuid": gid, "export": True, "isOpen": False, "visibility": True, "autouv": 0, "children": [],
        }
        for i, c in enumerate(b.cubes):
            x0, h0, z0, w, hgt, d = c.box
            cid = _uuid("%s:cube:%s:%d" % (name, b.name, i))
            faces = {}
            for f in FACES:
                faces[f] = {"uv": [0, 0, 0, 0], "texture": None if f in c.omit else 0}
            elements.append({
                "name": "%s.%s" % (c.pass_, c.material), "box_uv": True, "uv_offset": list(c.uv),
                "rescale": False, "locked": False, "render_order": "default", "allow_mirror_modeling": True,
                "from": [x0, h0, z0], "to": [x0 + w, h0 + hgt, z0 + d], "autouv": 0, "color": 0,
                "origin": [px, ph, pz], "faces": faces, "type": "cube", "uuid": cid,
            })
            groups[b.name]["children"].append(cid)
    outliner = []
    for b in rig.bones:
        g = groups[b.name]
        if b.parent is None:
            outliner.append(g)
        else:
            groups[b.parent.name]["children"].append(g)
    return {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": name, "model_identifier": "", "visible_box": [3, 5, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
        "unhandled_root_fields": {}, "resolution": {"width": SHEET, "height": SHEET},
        "elements": elements, "outliner": outliner, "textures": [_tex_entry(name)],
    }


def write_bbmodel(rig, check):
    path = resolve(BB_DIR + "/" + rig.spec["name"] + ".bbmodel")
    out = json.dumps(bbmodel(rig), indent=2)
    old = ""
    if os.path.exists(path):
        with open(path) as fh:
            old = fh.read()
    if out == old:
        return False
    if not check:
        with open(path, "w", newline="\n") as fh:
            fh.write(out)
    return True


# ---------------------------------------------------------------------------
# Texture painter - one 256x256 sheet per tier, every face painted by material
# ---------------------------------------------------------------------------

WHITES = ((234, 234, 244), (218, 218, 230), (202, 202, 216))
STEELS = ((150, 150, 168), (134, 134, 152), (118, 118, 136))
DARKS = ((70, 70, 86), (58, 58, 72), (46, 46, 58))
INK = (26, 26, 34)
SEAM = (176, 178, 196)
RIVET = (96, 98, 114)

DIGITS = {
    "1": ("010", "110", "010", "010", "111"),
    "2": ("111", "001", "111", "100", "111"),
    "3": ("111", "001", "111", "001", "111"),
    "4": ("101", "101", "111", "001", "001"),
}


def _mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _rgba(rgb, a=255):
    return (rgb[0], rgb[1], rgb[2], a)


class Painter:
    def __init__(self, spec):
        self.spec = spec
        self.img = Image.new("RGBA", (SHEET, SHEET), CLEAR)
        self.px = self.img.load()
        self.rng = random.Random(spec["name"])

    # -- primitives ---------------------------------------------------------
    def fill(self, x0, y0, w, h, ramp):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = self.rng.choice(ramp) if isinstance(ramp, tuple) and isinstance(ramp[0], tuple) else ramp
                self.px[x, y] = _rgba(c) if len(c) == 3 else c

    def rect(self, x0, y0, w, h, c):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.px[x, y] = _rgba(c) if len(c) == 3 else c

    def frame(self, x0, y0, w, h, c):
        self.rect(x0, y0, w, 1, c)
        self.rect(x0, y0 + h - 1, w, 1, c)
        self.rect(x0, y0, 1, h, c)
        self.rect(x0 + w - 1, y0, 1, h, c)

    # -- materials ----------------------------------------------------------
    def plating(self, x0, y0, w, h, ramp, seam=SEAM, rivets=True):
        self.fill(x0, y0, w, h, ramp)
        for x in range(x0 + 5, x0 + w - 1, 6):
            for y in range(y0 + 1, y0 + h - 1):
                if self.rng.random() < 0.85:
                    self.px[x, y] = _rgba(seam)
        if rivets and h >= 6:
            for y in range(y0 + 3, y0 + h - 2, 8):
                for x in range(x0 + 1, x0 + w - 1, 3):
                    self.px[x, y] = _rgba(RIVET)

    def hull(self, x0, y0, w, h, face, params, accent_ramp=False):
        accent, trim = self.spec["accent"], self.spec["trim"]
        if face in ("up", "down"):
            self.plating(x0, y0, w, h, STEELS, rivets=False)
            return
        ramp = (accent, _mix(accent, (0, 0, 0), 0.12), _mix(accent, (255, 255, 255), 0.1)) if accent_ramp else WHITES
        self.plating(x0, y0, w, h, ramp)
        if params.get("band_top"):
            self.rect(x0, y0, w, 1, trim)
        for b0, b1 in params.get("bands", ()):
            b0, b1 = int(b0), int(b1)
            if b1 - b0 >= 2:
                self.rect(x0, y0 + b0, w, b1 - b0, accent)
                self.rect(x0, y0 + b1 - 1, w, 1, trim)
            else:
                self.rect(x0, y0 + b0, w, max(1, b1 - b0), trim)
        for p in params.get("portholes", ()):
            p = int(p)
            cx = x0 + w // 2
            self.rect(cx - 2, y0 + p - 2, 4, 4, INK)
            self.rect(cx - 1, y0 + p - 1, 2, 2, (40, 60, 90))
        digit = params.get("digit")
        if digit and face in ("north", "south") and w >= 5:
            rows = DIGITS[digit]
            dx = x0 + (w - 3) // 2
            dy = y0 + int(params.get("digit_y", 13))
            for r, row in enumerate(rows):
                for cidx, ch in enumerate(row):
                    if ch == "1":
                        self.px[dx + cidx, dy + r] = _rgba(accent)
        self.rect(x0, y0 + h - 1, w, 1, DARKS[1])

    def paint(self, mat, params, face, x0, y0, w, h):
        spec = self.spec
        accent, trim, glass = spec["accent"], spec["trim"], spec["glass"]
        if mat == "hull":
            self.hull(x0, y0, w, h, face, params)
        elif mat == "booster":
            self.hull(x0, y0, w, h, face, dict(params, bands=((2, 5), (h - 4, h - 2))))
        elif mat == "booster_nose" or mat == "fin":
            self.hull(x0, y0, w, h, face, params, accent_ramp=True)
            if mat == "fin" and face in ("north", "south"):
                self.rect(x0, y0, 1, h, trim)
        elif mat == "fin_edge":
            self.rect(x0, y0, w, h, trim)
        elif mat == "trim" or mat == "clamp" or mat == "mast" or mat == "ladder":
            self.fill(x0, y0, w, h, DARKS)
            if h > 2 and w > 2:
                self.rect(x0, y0, w, 1, STEELS[0])
        elif mat == "steel" or mat == "dish_hub":
            self.plating(x0, y0, w, h, STEELS, seam=DARKS[0], rivets=False)
        elif mat == "engine":
            for y in range(y0, y0 + h):
                t = (y - y0) / max(1, h - 1)
                c = _mix(DARKS[0], (24, 22, 30), t) if face not in ("up", "down") else DARKS[1]
                for x in range(x0, x0 + w):
                    self.px[x, y] = _rgba(c)
            if face not in ("up", "down"):
                if params.get("lip"):
                    self.rect(x0, y0 + h - 1, w, 1, INK)
                    self.rect(x0, y0 + h - 2, w, 1, _mix(accent, INK, 0.5))
                else:
                    self.rect(x0, y0, w, 1, accent)
        elif mat == "throat":
            self.rect(x0, y0, w, h, _mix(spec["plume"][0], (255, 255, 255), 0.3))
            if w > 2 and h > 2:
                self.rect(x0 + 1, y0 + 1, w - 2, h - 2, spec["plume"][1])
        elif mat == "glass" or mat == "hatch_window":
            tint = _rgba(glass, 118)
            self.rect(x0, y0, w, h, tint)
            self.frame(x0, y0, w, h, _rgba(_mix(glass, (255, 255, 255), 0.35), 150))
            for i in range(min(w, h)):
                if 0 < i < w - 1 and 0 < i < h - 1:
                    self.px[x0 + w - 1 - i, y0 + i] = _rgba(_mix(glass, (255, 255, 255), 0.55), 160)
        elif mat == "liner":
            base, rib, strip = (44, 50, 66), (62, 70, 90), (120, 140, 170)
            if face == "down":
                self.rect(x0, y0, w, h, (40, 42, 52))
                for y in range(y0, y0 + h, 2):
                    for x in range(x0 + (1 if (y - y0) % 4 else 0), x0 + w, 2):
                        self.px[x, y] = _rgba((70, 74, 90))
            elif face == "up":
                self.rect(x0, y0, w, h, (36, 38, 50))
                self.rect(x0 + w // 2 - 2, y0 + h // 2 - 2, 4, 4, (90, 96, 112))
            else:
                self.rect(x0, y0, w, h, base)
                for x in range(x0 + 1, x0 + w, 3):
                    self.rect(x, y0, 1, h, rib)
                self.rect(x0, y0 + 1, w, 1, strip)
                self.rect(x0, y0 + h - 2, w, 1, rib)
        elif mat == "console":
            self.rect(x0, y0, w, h, (20, 24, 34))
            if face == "up":
                cols = ((88, 224, 128), (255, 196, 64), (236, 80, 80), (96, 196, 255))
                for x in range(x0 + 1, x0 + w - 1):
                    self.px[x, y0 + h // 2] = _rgba(self.rng.choice(cols))
            elif face == "south":
                self.rect(x0, y0 + h - 1, w, 1, (60, 66, 84))
        elif mat == "screen":
            self.rect(x0, y0, w, h, (26, 72, 60))
            if face == "south" and w > 2:
                for i in range(1, w - 1):
                    c = (120, 255, 170) if (i % 3) else (255, 210, 90)
                    self.px[x0 + i, y0 + (i % max(1, h - 1))] = _rgba(c)
        elif mat == "lamp":
            self.rect(x0, y0, w, h, (255, 244, 214))
        elif mat == "strip":
            self.rect(x0, y0, w, h, _mix(glass, (255, 255, 255), 0.5))
        elif mat == "beacon_red":
            self.rect(x0, y0, w, h, (255, 70, 60))
        elif mat == "beacon_green":
            self.rect(x0, y0, w, h, (80, 255, 120))
        elif mat == "beacon_white":
            self.rect(x0, y0, w, h, (255, 255, 255))
        elif mat == "beacon_accent":
            self.rect(x0, y0, w, h, _mix(accent, (255, 255, 255), 0.4))
        elif mat == "leg":
            self.fill(x0, y0, w, h, STEELS)
            if face not in ("up", "down"):
                for y in range(y0 + 2, y0 + h - 1, 5):
                    self.rect(x0, y, w, 1, DARKS[2])
        elif mat == "leg_piston":
            self.fill(x0, y0, w, h, DARKS)
            if face not in ("up", "down"):
                self.rect(x0, y0, w, 1, accent)
        elif mat == "foot":
            self.rect(x0, y0, w, h, DARKS[2])
            self.frame(x0, y0, w, h, INK)
        elif mat == "hatch":
            self.hull(x0, y0, w, h, face, {})
            if face == "south":
                self.frame(x0, y0, w, h, INK)
                self.rect(x0 + 1, y0 + h - 3, 1, 1, DARKS[2])
        elif mat == "dish":
            self.plating(x0, y0, w, h, STEELS, seam=DARKS[0], rivets=False)
            if w >= 4 and h >= 4 and face in ("up", "north"):
                self.frame(x0 + 1, y0 + 1, w - 2, h - 2, DARKS[0])
                self.rect(x0 + w // 2 - 1, y0 + h // 2 - 1, 2, 2, accent)
        elif mat == "solar":
            if face in ("east", "west") or (w >= 4 and h >= 4):
                self.rect(x0, y0, w, h, (30, 50, 120))
                for x in range(x0, x0 + w, 3):
                    self.rect(x, y0, 1, h, (92, 112, 176))
                for y in range(y0, y0 + h, 3):
                    self.rect(x0, y, w, 1, (92, 112, 176))
                self.frame(x0, y0, w, h, STEELS[0])
            else:
                self.fill(x0, y0, w, h, STEELS)
        elif mat == "grid":
            self.rect(x0, y0, w, h, DARKS[1])
            if w >= 4 and h >= 4:
                for x in range(x0 + 1, x0 + w, 2):
                    self.rect(x, y0 + 1, 1, h - 2, STEELS[1])
                for y in range(y0 + 1, y0 + h, 2):
                    self.rect(x0 + 1, y, w - 2, 1, STEELS[1])
        elif mat.startswith("plume_"):
            core, mid, tip = spec["plume"]
            if mat == "plume_core":
                c0, c1, a0 = _mix(core, (255, 255, 255), 0.4), mid, 235
            elif mat == "plume_mid":
                c0, c1, a0 = mid, tip, 150
            else:
                c0, c1, a0 = tip, _mix(tip, (20, 10, 30), 0.6), 80
            if face == "down":
                self.rect(x0, y0, w, h, CLEAR)
                return
            if face == "up":
                self.rect(x0, y0, w, h, _rgba(c0, a0))
                return
            for y in range(y0, y0 + h):
                t = (y - y0) / max(1, h - 1)
                col = _mix(c0, c1, t)
                a = int(a0 * (1 - t) ** 1.6)
                if mat == "plume_core" and (y - y0) % 5 == 2:
                    col = _mix(col, (255, 255, 255), 0.5)
                    a = min(255, a + 20)
                for x in range(x0, x0 + w):
                    ex = 1.0
                    if w > 2 and (x == x0 or x == x0 + w - 1):
                        ex = 0.6
                    self.px[x, y] = _rgba(col, int(a * ex))
        else:
            raise SystemExit("no painter for material " + mat)

    # -- cube driver --------------------------------------------------------
    def cube(self, c):
        _, _, _, w, h, d = c.box
        u, v = c.uv
        w_, h_, d_ = int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d))
        regions = {
            "down": (u + d_, v, w_, d_), "up": (u + d_ + w_, v, w_, d_),
            "west": (u, v + d_, d_, h_), "north": (u + d_, v + d_, w_, h_),
            "east": (u + d_ + w_, v + d_, d_, h_), "south": (u + 2 * d_ + w_, v + d_, w_, h_),
        }
        for face, (x0, y0, rw, rh) in regions.items():
            if rw <= 0 or rh <= 0 or face in c.omit:
                continue
            self.paint(c.material, c.params, face, x0, y0, rw, rh)
        for face, fx, fy, fw, fh in c.cuts:
            x0, y0, _, _ = regions[face]
            fx, fy, fw, fh = int(fx), int(fy), int(fw), int(fh)
            if c.material in ("hull", "booster"):
                self.frame(x0 + fx - 1, y0 + fy - 1, fw + 2, fh + 2, INK)
            self.rect(x0 + fx, y0 + fy, fw, fh, CLEAR)


def paint(rig, uniques, check):
    if Image is None:
        print("rocket_models: Pillow not installed; skipping textures (pip install pillow).")
        return False
    p = Painter(rig.spec)
    for c in uniques:
        p.cube(c)
    path = resolve(TEX_DIR + "/" + rig.spec["name"] + ".png")
    if os.path.exists(path):
        old = Image.open(path).convert("RGBA")
        if old.size == p.img.size and old.tobytes() == p.img.tobytes():
            return False
    if not check:
        p.img.save(path)
    return True


# ---------------------------------------------------------------------------

ENTITY = "src/main/java/za/co/neroland/nerospace/rocket/RocketEntity.java"


def verify_rider_eye():
    """RocketEntity (common, server-safe) carries the per-tier eye line as a plain constant; make
    sure it still matches the window bands authored here."""
    import re
    with open(resolve(ENTITY)) as fh:
        m = re.search(r"RIDER_EYE_PX\s*=\s*\{([^}]*)\}", fh.read())
    want = [float(TIERS[t]["eye"]) for t in sorted(TIERS)]
    got = [float(v.strip().rstrip("fF")) for v in m.group(1).split(",")] if m else None
    if got != want:
        raise SystemExit("RocketEntity.RIDER_EYE_PX %s must equal the spec eye lines %s" % (got, want))


def main():
    check = "--check" in sys.argv
    print("rocket_models: target = %s" % target_label())
    verify_rider_eye()
    rigs = []
    changed = []
    for tier in sorted(BUILDERS):
        rig = BUILDERS[tier]()
        uniques = pack(rig)
        rigs.append(rig)
        n = sum(1 for _ in rig.cubes())
        print("  %s: %d bones, %d cubes (%d unique UV regions)" % (rig.spec["name"], len(rig.bones), n, len(uniques)))
        if paint(rig, uniques, check):
            changed.append(rig.spec["name"] + ".png")
        if write_bbmodel(rig, check):
            changed.append(rig.spec["name"] + ".bbmodel")
    if write_java(rigs, check):
        changed.append("RocketGeometry.java")
    for c in changed:
        print("  %s %s" % ("stale:" if check else "wrote:", c))
    if not changed:
        print("  up to date")
    if check and changed:
        sys.exit(1)


if __name__ == "__main__":
    main()
