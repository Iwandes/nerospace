#!/usr/bin/env python3
"""
Sky and particle textures for the Nerospace 2.0.0 planet skies.

Generates every texture the seven ``assets/nerospace/sky/<planet>.json`` profiles reference, plus the sprite
sheets for the six custom particle types, into the resource tree:

    textures/sky/      galaxy.png, galaxy_nyx.png, aurora_0.png, aurora_1.png, stars.png, horizon_ramp.png,
                       sun_white.png, sun_small.png, sun_pale.png, sun_amber.png, red_giant.png, dark_moon.png,
                       earth.png, earth_disk.png, phobos.png, deimos.png, moon_small.png
                       -- sky upgrade (2.0.x) --
                       stars_natural.png (temperature-coloured star sheet), aurora_glacira_0..2, aurora_nyxara_0..1,
                       aurora_mars_0, aurora_greenxertz_0 (gradient curtains: RGB = the colour gradient bottom->top,
                       alpha = the ray mask), aurora_fringe_rose.png, meteor_streak.png, limb_glow.png,
                       terminator.png, terminator_00..15.png (night-shade phase sheet), earth_globe.png, earth_disk_globe.png, earth_lights.png, earth_disk_lights.png, corona.png, diamond_ring.png,
                       halo_22.png, rainbow_arc.png, flare_rays.png, caustic_band.png, nebula_a.png, nebula_b.png,
                       clouds_ash.png, sun_aureole.png, horizon_ramp_soft.png
    textures/particle/ dust_0..3, ash_0..3, spore_0..3, diamond_dust_0..3, ember_0..3, nebula_mote_0..3 (8x8)

Everything is deterministic: each output seeds its own ``numpy`` generator from the md5 of its name (same rule as
``gen_planet_textures.seed_of``), so a re-render is byte-identical. Pillow + numpy only.

Conventions
-----------
* Body textures are **luminance-first**: discs are white/pale so ``Body.color`` in the sky profile does the tinting
  (the red giant and Earth are the exceptions - they carry their own colour because their surface detail is the
  identity).
* The galaxy bands and aurora strips tile horizontally (periodic value noise), so the renderer can wrap them
  around the sky dome with ``yaw``.
* Gradient curtains (``aurora_<planet>_<n>``) carry the planet's vertical colour gradient in RGB because the sky
  renderer draws them through an additive POSITION_TEX pipeline with one tint per draw: the texture supplies the
  colour run, the tint supplies intensity. Decal textures anchored to a body (``halo_22``, ``rainbow_arc``,
  ``corona``, ``limb_glow``, ``flare_rays``) are drawn on a flat tangent quad, so an angular radius ``a`` inside a
  quad of half-angle ``h`` sits at ``tan(a) / tan(h)`` of the half-size - the constants below say which ``h``
  they assume.
* Detailed celestial surfaces use spherical volume noise; dust bands use cylindrical volume noise for seamless
  wrapping. Art is baked offline; the runtime uses the existing lightweight sky meshes.
* ``stars.png`` is a 4x4 sheet of 16x16 sprites (point, soft, cross, 6-ray at two brightnesses); the renderer
  picks a sprite per star by hash and tints with ``stars.tint``.
* Particle sprites are 8x8 like vanilla's and come in four frames per type (``<type>_0..3``), listed in that order
  by ``particles/<type>.json`` so vanilla's animated sprite set cycles them over the particle lifetime.

Usage (from anywhere; the default root is the parent of this ``tools`` folder, so inside the repo it writes to
``common/src/main/resources/assets/nerospace/textures``; ``--root`` points it at another resource tree):

    python tools/gen_sky_textures.py                 # write all textures (additive: existing files are overwritten
                                                     #   only with --force, like gen_textures.save)
    python tools/gen_sky_textures.py --force
    python tools/gen_sky_textures.py --root <dir>    # another resource root
    python tools/gen_sky_textures.py --preview <dir> # also write a contact sheet of every texture
    python tools/gen_sky_textures.py --check         # re-render to a temp dir; exit 1 if a committed texture drifted
"""
import argparse
import hashlib
import math
import os
import shutil
import sys
import tempfile

try:
    from PIL import Image, ImageFilter
    import numpy as np
except ModuleNotFoundError as e:  # mirrors gen_textures: committed art, deps optional
    print("gen_sky_textures: %s not installed; skipping (pip install pillow numpy)." % e.name)
    sys.exit(0)

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_ROOT = os.path.dirname(HERE)
ASSET_DIR = os.path.join("common", "src", "main", "resources", "assets", "nerospace", "textures")


# ======================================================================================= helpers

def seed_of(name):
    return int(hashlib.md5(name.encode("utf-8")).hexdigest()[:8], 16)


def rng_of(name):
    return np.random.default_rng(seed_of(name))


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255.0


def smoothstep(t):
    return t * t * (3.0 - 2.0 * t)


def value_noise(rng, w, h, fx, fy, wrap_x=False):
    """Bilinear value noise with ``fx`` x ``fy`` lattice cells over the image; periodic in x when wrap_x."""
    lat = rng.random((fy + 1, fx + 1)).astype(np.float32)
    if wrap_x:
        lat[:, -1] = lat[:, 0]
    xs = np.linspace(0.0, fx, w, endpoint=False, dtype=np.float32)
    ys = np.linspace(0.0, fy, h, endpoint=False, dtype=np.float32)
    x0 = np.floor(xs).astype(int)
    y0 = np.floor(ys).astype(int)
    tx = smoothstep(xs - x0)[None, :]
    ty = smoothstep(ys - y0)[:, None]
    x1 = np.minimum(x0 + 1, fx)
    y1 = np.minimum(y0 + 1, fy)
    a = lat[y0][:, x0]
    b = lat[y0][:, x1]
    c = lat[y1][:, x0]
    d = lat[y1][:, x1]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def fbm(rng, w, h, base=4, octaves=5, persistence=0.5, wrap_x=False, aspect=1.0):
    out = np.zeros((h, w), dtype=np.float32)
    amp = 1.0
    total = 0.0
    f = base
    for _ in range(octaves):
        fy = max(1, int(round(f * aspect)))
        out += amp * value_noise(rng, w, h, max(1, int(f)), fy, wrap_x)
        total += amp
        amp *= persistence
        f *= 2
    return out / total


def radial(size):
    """(dx, dy, r) in [-1, 1] from the centre of a size x size image."""
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    c = (size - 1) / 2.0
    dx = (xs - c) / c
    dy = (ys - c) / c
    return dx, dy, np.sqrt(dx * dx + dy * dy)


def noise_space(name):
    """A seeded periodic volume: evaluate on a sphere/cylinder rather than painting flat noise onto a disc."""
    lattice = rng_of(name).random((64, 64, 64), dtype=np.float32)

    def sample(x, y, z):
        ix, iy, iz = np.floor(x).astype(int), np.floor(y).astype(int), np.floor(z).astype(int)
        tx, ty, tz = smoothstep(x - ix), smoothstep(y - iy), smoothstep(z - iz)
        value = np.zeros(np.broadcast_shapes(np.shape(x), np.shape(y), np.shape(z)), dtype=np.float32)
        for dz in (0, 1):
            for dy in (0, 1):
                for dx in (0, 1):
                    weight = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                    value += lattice[(iz + dz) % 64, (iy + dy) % 64, (ix + dx) % 64] * weight
        return value

    return sample


def volume_fbm(sample, x, y, z, octaves=5):
    if not 1 <= octaves <= 8:
        raise ValueError("volume noise requires 1..8 octaves")
    value = np.zeros(np.broadcast_shapes(np.shape(x), np.shape(y), np.shape(z)), dtype=np.float32)
    amplitude, total = 0.5, 0.0
    for _ in range(octaves):
        value += amplitude * sample(x, y, z)
        total += amplitude
        x, y, z = x * 2.07 + 7.3, y * 2.07 + 13.1, z * 2.07 + 19.7
        amplitude *= 0.51
    return value / total


def sphere(size, radius):
    x, y, r = radial(size)
    x, y = x / radius, y / radius
    z = np.sqrt(np.maximum(0, 1 - x * x - y * y))
    return x, y, z, r


def to_image(rgb, alpha):
    rgb = np.clip(rgb, 0.0, 1.0)
    alpha = np.clip(alpha, 0.0, 1.0)
    arr = np.concatenate([rgb, alpha[..., None]], axis=-1)
    return Image.fromarray((arr * 255.0 + 0.5).astype(np.uint8), "RGBA")


def save(img, path, force):
    if os.path.exists(path) and not force:
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    return True


# ======================================================================================= suns

def sun_disc(name, size, radius, glow, rays=0, rim=None):
    rng = rng_of(name)
    dx, dy, r = radial(size)
    disc = np.clip((radius - r) / 0.04, 0, 1)
    halo = np.exp(-(np.maximum(r - radius, 0.0) / glow) ** 2)
    a = np.maximum(disc, halo * 0.9)
    lum = np.ones((size, size), dtype=np.float32)
    # faint limb darkening so the disc is not a flat cut-out
    lum = np.where(r < radius, 1.0 - 0.12 * (r / radius) ** 2, lum)
    if rays:
        ang = np.arctan2(dy, dx)
        ray = np.abs(np.cos(ang * rays / 2.0)) ** 60.0
        ray *= np.exp(-((r - radius) / (glow * 2.4))) * (r > radius)
        a = np.clip(a + ray * 0.8, 0, 1)
    sx, sy, sz, _ = sphere(size, radius)
    granules = volume_fbm(noise_space(name), sx * 18, sy * 18, sz * 18, 4)
    grain = (0.03 + 0.12 * granules) * (r < radius)
    rgb = np.stack([lum - grain * (r < radius)] * 3, axis=-1)
    if rim is not None:
        rimc = hexc(rim)
        w = np.clip(halo * 1.5, 0, 1)[..., None]
        rgb = rgb * (1 - w) + rimc * w
    return to_image(rgb, a)


def red_giant(name="red_giant", size=256):
    rng = rng_of(name)
    dx, dy, r = radial(size)
    radius = 0.72
    sx, sy, sz, _ = sphere(size, radius)
    gran = volume_fbm(noise_space(name), sx * 24, sy * 24, sz * 24, 6)
    cells = fbm(rng, size, size, base=5, octaves=2)
    spots = (fbm(rng, size, size, base=4, octaves=3) > 0.66).astype(np.float32)
    spots = np.array(Image.fromarray((spots * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2))) / 255.0
    limb = 1.0 - 0.55 * np.clip(r / radius, 0, 1) ** 1.6
    lum = (0.78 + 0.22 * gran) * (0.85 + 0.15 * cells) * limb
    lum *= (1.0 - 0.6 * spots)
    # surface colour: white-yellow core to orange limb; Body.color (#FF6B35) multiplies this
    core = hexc("#FFF1C2")
    edge = hexc("#FFB070")
    t = np.clip(r / radius, 0, 1)[..., None]
    rgb = (core * (1 - t) + edge * t) * lum[..., None]
    disc = np.clip((radius - r) / 0.012, 0, 1)
    corona = np.exp(-(np.abs(r - radius) / 0.16) ** 1.4) * (r > radius)
    wisps = fbm(rng, size, size, base=16, octaves=3)
    corona *= 0.55 + 0.45 * wisps
    rgb = np.where((r > radius)[..., None], hexc("#FF8A4A")[None, None, :] * (0.7 + 0.3 * wisps[..., None]), rgb)
    a = disc + corona * 0.85
    return to_image(rgb, a)


def dark_moon(name="dark_moon", size=256):
    rng = rng_of(name)
    dx, dy, r = radial(size)
    radius = 0.82
    n = fbm(rng, size, size, base=8, octaves=4)
    lum = 0.10 + 0.08 * n
    # a thin lit rim so the silhouette reads against the red giant during totality
    rim = np.exp(-((radius - r) / 0.025) ** 2) * (r < radius)
    rgb = np.stack([lum] * 3, axis=-1)
    rgb += rim[..., None] * hexc("#6E3A4A")[None, None, :] * 0.9
    a = np.clip((radius - r) / 0.01, 0, 1)
    return to_image(rgb, a)


# ======================================================================================= moons / planets

def rocky_moon(name, size, radius, base_grey, crater_count, potato=0.0):
    rng = rng_of(name)
    x, y, z, r = sphere(size, radius)
    sample = noise_space(name)
    height = volume_fbm(sample, x * 5, y * 5, z * 5, 5)
    for _ in range(crater_count * 5):
        centre = rng.normal(size=3)
        centre /= np.linalg.norm(centre)
        reach = rng.uniform(0.04, 0.28)
        distance = np.sqrt((x - centre[0]) ** 2 + (y - centre[1]) ** 2 + (z - centre[2]) ** 2) / reach
        height += 0.24 * np.exp(-((distance - 1) / 0.15) ** 2) - 0.22 * np.clip(1 - distance ** 2, 0, 1)
    gy, gx = np.gradient(height)
    relief = np.clip(1 + (gy - gx) * size * 0.08, 0.45, 1.35)
    lum = base_grey * (0.4 + height * 0.8) * relief * (0.65 + 0.35 * z)
    if name.startswith('rime') or name.startswith('hoar'):
        cracks = np.exp(-((height - 0.5) / 0.015) ** 2)
        rgb = lum[..., None] * hexc('#B9D6DD') + cracks[..., None] * hexc('#468B9B') * 0.3
    else:
        rgb = np.repeat(lum[..., None], 3, axis=-1)
    outline = radius * (1 + potato * (np.sin(np.arctan2(y, x) * 3 + 0.7) * 0.18))
    alpha = np.clip((outline - r) * size / 2, 0, 1)
    return to_image(rgb, alpha)


def earth(name, size):
    rgb, a, _ = earth_parts(name, size)
    return to_image(rgb, a)


def earth_globe(name, size, source):
    """``earth`` without the baked upper-left daylight: a flat-lit globe for bodies that carry a ``nightShade`` (the
    renderer's terminator supplies the light direction, so a baked one would fight it). Same land as ``source``."""
    rgb, a, _ = earth_parts(source, size, baked_light=False)
    return to_image(rgb, a)


def earth_parts(name, size, baked_light=True):
    x, y, z, r = sphere(size, 0.86)
    sample = noise_space(name)
    warp = volume_fbm(sample, x * 2 + 11, y * 2, z * 2, 3) - 0.5
    land = volume_fbm(sample, x * 3.8 + warp, y * 3.8, z * 3.8, 6)
    landmask = smoothstep(np.clip((land - 0.50) / 0.045, 0, 1))
    terrain = volume_fbm(sample, x * 19, y * 19, z * 19, 4)
    coast = np.exp(-((land - 0.51) / 0.022) ** 2)
    desert = np.clip(1 - np.abs(y - 0.25) * 3, 0, 1) * terrain
    landc = hexc('#345B42') * (1 - desert[..., None]) + hexc('#B49B72') * desert[..., None]
    ocean = hexc('#102C53') + coast[..., None] * hexc('#286A79') * 0.7
    rgb = ocean * (1 - landmask[..., None]) + landc * landmask[..., None]
    gy, gx = np.gradient(terrain)
    rgb *= np.clip(1 + (gy - gx) * size * 0.025 * landmask, 0.7, 1.2)[..., None]
    ice = smoothstep(np.clip((np.abs(y) - 0.76 + (terrain - 0.5) * 0.18) / 0.14, 0, 1))
    rgb = rgb * (1 - ice[..., None]) + hexc('#D7E4DD') * ice[..., None]
    swirl = np.sin(y * 12 + warp * 6) * 0.25
    cloud = volume_fbm(sample, x * 7 + swirl + 23, y * 10 + warp, z * 7, 6)
    cloud = smoothstep(np.clip((cloud - 0.50) / 0.22, 0, 1)) * 0.9
    rgb *= 1 - cloud[..., None] * 0.3
    rgb = rgb * (1 - cloud[..., None]) + hexc('#EDF0E7') * cloud[..., None]
    light = np.clip(0.75 - 0.3 * (x + y), 0.2, 1) if baked_light else np.ones_like(z)
    rgb *= (light * (0.55 + 0.45 * z ** 0.4))[..., None]
    rim = (1 - z) ** 4 * (r < 0.86)
    rgb = rgb * (1 - rim[..., None] * 0.55) + hexc('#6FA4C1') * rim[..., None] * 0.55
    glow = np.exp(-((r - 0.86) / 0.025) ** 2) * (r > 0.86)
    rgb = np.where((r > 0.86)[..., None], hexc('#629ABF'), rgb)
    alpha = np.clip((0.86 - r) * size / 2, 0, 1) + glow * 0.5
    return rgb, alpha, landmask * (r < 0.86) * (1 - cloud)


def galaxy_band(name, w, h, core, outer, lane, star_tint, density=1.0, nebula=None):
    sample = noise_space(name)
    angle = np.linspace(0, 2 * math.pi, w, dtype=np.float32)[None, :]
    vertical = np.linspace(-1, 1, h, dtype=np.float32)[:, None]
    x, z = np.cos(angle) * 3.5, np.sin(angle) * 3.5
    warp = volume_fbm(sample, x, vertical * 2, z, 4) - 0.5
    cloud = volume_fbm(sample, x * 2 + warp * 2, vertical * 5, z * 2, 6)
    dust = volume_fbm(sample, x * 4, vertical * 9 + warp * 3, z * 4, 5)
    centre = vertical + 0.15 * np.sin(angle * 2) + warp * 0.3
    envelope = np.exp(-(centre / 0.40) ** 2) * smoothstep(np.clip((1 - np.abs(vertical)) / 0.2, 0, 1))
    split = np.exp(-((centre + (dust - 0.5) * 0.45) / 0.075) ** 2)
    tendrils = smoothstep(np.clip((dust - 0.46) / 0.22, 0, 1))
    emission = envelope * (0.18 + 1.6 * cloud ** 2) * (1 - split * 0.92) * (1 - tendrils * 0.65)
    tint = np.clip(np.abs(centre) * 2 + warp * 0.5, 0, 1)[..., None]
    rgb = hexc(core) * (1 - tint) + hexc(outer) * tint
    rgb = rgb * (1 - split[..., None] * 0.5) + hexc(lane) * split[..., None] * 0.5
    if nebula:
        knots = np.clip((cloud - 0.54) * 4, 0, 1)[..., None]
        hue = smoothstep(np.clip((dust - 0.36) / 0.26, 0, 1))[..., None]
        emission_color = hexc(nebula[0]) * (1 - hue) + hexc(nebula[1]) * hue
        rgb = rgb * (1 - knots) + emission_color * knots
    stars = rng_of(name + '_clusters').random((h, w), dtype=np.float32)
    points = np.where(stars > 0.995, (stars - 0.995) / 0.005, 0) * envelope
    rgb = rgb * (1 - points[..., None]) + hexc(star_tint) * points[..., None]
    alpha = np.clip(emission * density + points * 0.85, 0, 1)
    rgb[:, -1], alpha[:, -1] = rgb[:, 0], alpha[:, 0]
    return to_image(rgb, alpha)


def aurora_strip(name, w, h, phase):
    rng = rng_of(name)
    xs = np.linspace(0, 1, w, endpoint=False, dtype=np.float32)
    ys = np.linspace(0, 1, h, dtype=np.float32)[:, None]
    rays = fbm(rng, w, 1, base=24, octaves=4, persistence=0.6, wrap_x=True)[0]
    rays2 = fbm(rng, w, 1, base=9, octaves=3, wrap_x=True)[0]
    ray = np.clip((rays - 0.35) / 0.4, 0, 1) * (0.4 + 0.6 * rays2)
    base_y = 0.75 + 0.15 * (fbm(rng, w, 1, base=3, octaves=2, wrap_x=True)[0] - 0.5)
    vert = np.exp(-((ys - base_y[None, :]) / 0.08) ** 2) * 1.0 + np.clip(1 - (base_y[None, :] - ys) / 0.6, 0, 1) * (ys < base_y[None, :]) * 0.6
    wisps = fbm(rng, w, h, base=12, octaves=3, wrap_x=True, aspect=0.6)
    inten = ray[None, :] * vert * (0.6 + 0.4 * wisps)
    inten = np.clip(inten, 0, 1)
    # the strip is a luminance/alpha mask; the renderer tints it with aurora.colors over time
    rgb = np.ones((h, w, 3), dtype=np.float32)
    return to_image(rgb, inten)


def star_sheet(name="stars", cell=16, n=4):
    rng = rng_of(name)
    size = cell * n
    rgb = np.ones((size, size, 3), dtype=np.float32)
    a = np.zeros((size, size), dtype=np.float32)
    kinds = ["point", "point2", "soft", "soft2", "cross", "cross2", "ray6", "ray6b",
             "point", "soft", "pointw", "softw", "cross", "point2", "soft", "point"]
    for i, kind in enumerate(kinds):
        cx = (i % n) * cell
        cy = (i // n) * cell
        dx, dy, r = radial(cell)
        if kind == "point":
            spr = np.exp(-(r / 0.13) ** 2) * 0.8
        elif kind == "point2":
            spr = np.clip(1 - r / 0.16, 0, 1)
        elif kind == "pointw":
            spr = np.exp(-(r / 0.13) ** 2) * 0.5
        elif kind == "soft":
            spr = np.exp(-(r / 0.22) ** 2)
        elif kind == "soft2":
            spr = np.exp(-(r / 0.3) ** 2) * 0.9
        elif kind == "softw":
            spr = np.exp(-(r / 0.18) ** 2) * 0.6
        elif kind.startswith("cross"):
            arm = (np.abs(dx) < 0.07) | (np.abs(dy) < 0.07)
            spr = arm * np.clip(1 - r / (0.5 if kind == "cross" else 0.75), 0, 1) + np.exp(-(r / 0.16) ** 2)
        else:  # ray6
            ang = np.arctan2(dy, dx)
            ray = np.abs(np.cos(ang * 3)) ** 40
            spr = ray * np.clip(1 - r / 0.8, 0, 1) * 0.8 + np.exp(-(r / 0.2) ** 2)
        a[cy:cy + cell, cx:cx + cell] = np.clip(spr, 0, 1)
        # a touch of colour temperature per sprite (blue-white / warm) baked at 10 % so tint still rules
        temp = rng.uniform(-1, 1)
        rgb[cy:cy + cell, cx:cx + cell] = np.array([1.0 - 0.08 * max(temp, 0), 1.0 - 0.04 * abs(temp), 1.0 - 0.08 * max(-temp, 0)])
    return to_image(rgb, a)


# ======================================================================================= particles

def particle_frames(kind):
    """Four 8x8 RGBA frames per type. Returns a list of PIL images."""
    frames = []
    for f in range(4):
        rng = rng_of("%s_%d" % (kind, f))
        dx, dy, r = radial(8)
        if kind == "dust":
            col = [hexc("#C8683D"), hexc("#B5532A"), hexc("#7A4A2E"), hexc("#D9A066")][f]
            n = value_noise(rng, 8, 8, 3, 3)
            a = np.clip((0.9 - r) / 0.45, 0, 1) * (0.35 + 0.65 * n) * 0.9
            a *= (r < 0.8 + 0.3 * (n - 0.5))
            rgb = col[None, None, :] * (0.85 + 0.3 * n[..., None])
        elif kind == "ash":
            grey = [0.62, 0.5, 0.42, 0.34][f]
            n = value_noise(rng, 8, 8, 3, 3)
            shape = (r < 0.55 + 0.35 * (n - 0.5)) & (np.abs(dx * 0.6 + dy) < 0.75)
            a = shape.astype(np.float32) * (0.7 + 0.3 * n)
            rgb = np.stack([grey * (0.8 + 0.4 * n)] * 3, axis=-1) * np.array([1.0, 0.97, 0.95])
        elif kind == "spore":
            size_ = [0.55, 0.7, 0.6, 0.45][f]
            a = np.exp(-(r / size_) ** 2) * 0.9
            core = np.exp(-(r / (size_ * 0.45)) ** 2)
            rgb = hexc("#FF7AC8")[None, None, :] * (1 - core[..., None] * 0.5) + hexc("#FFD6EC")[None, None, :] * core[..., None] * 0.5
        elif kind == "diamond_dust":
            arm = [0.9, 0.55, 0.3, 0.7][f]
            cross = ((np.abs(dx) < 0.3) | (np.abs(dy) < 0.3)) * np.clip(1 - r / arm, 0, 1)
            a = np.clip(cross + np.exp(-(r / 0.3) ** 2), 0, 1)
            rgb = hexc("#F2FBFF")[None, None, :] * np.ones((8, 8, 1)) - np.array([0.0, 0.0, 0.0])
            rgb[..., 2] = 1.0
            rgb[..., 1] = 0.97 + 0.03 * a
        elif kind == "ember":
            cols = [("#FFE08A", "#FF6B35"), ("#FFC860", "#FF5A1F"), ("#FF9A4A", "#D94E1F"), ("#D94E1F", "#5A2416")][f]
            core = np.exp(-(r / 0.28) ** 2)
            halo = np.exp(-(r / 0.6) ** 2) * (0.9 - 0.2 * f)
            a = np.clip(core + halo * 0.7, 0, 1)
            rgb = hexc(cols[1])[None, None, :] * (1 - core[..., None]) + hexc(cols[0])[None, None, :] * core[..., None]
        else:  # nebula_mote
            cols = ["#C77DFF", "#9B5DE5", "#4DD0E1", "#FF6EC7"]
            col = hexc(cols[f])
            a = np.exp(-(r / 0.5) ** 2) * (0.75 + 0.25 * math.sin(f * 1.7)) * 0.8
            core = np.exp(-(r / 0.22) ** 2)
            rgb = col[None, None, :] * (1 - core[..., None] * 0.4) + np.ones((8, 8, 3)) * core[..., None] * 0.4
        frames.append(to_image(rgb, a))
    return frames



# ======================================================================================= sky upgrade: stars

def star_sheet_natural(name="stars_natural", cell=16, n=4):
    """Like ``star_sheet`` but each sprite carries a real colour temperature. The renderer picks a cell per star by
    hash, so the cell counts set the sky's colour census: 4 blue-white (O/B/A), 6 white and yellow-white (F/G),
    3 yellow (G/K), 2 orange (K) and 1 red (M) - a dark-sky look, not a flat white field."""
    rng = rng_of(name)
    size = cell * n
    rgb = np.ones((size, size, 3), dtype=np.float32)
    a = np.zeros((size, size), dtype=np.float32)
    kinds = ["point", "soft", "cross", "point2", "soft2", "point", "ray6", "softw",
             "point", "soft", "point2", "cross2", "soft", "pointw", "ray6b", "soft2"]
    temps = ["#9FB7FF", "#AEC4FF", "#CAD7FF", "#DCE5FF", "#F3F4FF", "#FFFFFF", "#FFF8F0", "#FFF4E6",
             "#FFF1DE", "#FFEBD0", "#FFE4B5", "#FFDCA8", "#FFC98A", "#FFB070", "#FF9A60", "#FF7A50"]
    for i, kind in enumerate(kinds):
        cx = (i % n) * cell
        cy = (i // n) * cell
        dx, dy, r = radial(cell)
        if kind == "point":
            spr = np.exp(-(r / 0.13) ** 2) * 0.8
        elif kind == "point2":
            spr = np.clip(1 - r / 0.16, 0, 1)
        elif kind == "pointw":
            spr = np.exp(-(r / 0.13) ** 2) * 0.5
        elif kind == "soft":
            spr = np.exp(-(r / 0.22) ** 2)
        elif kind == "soft2":
            spr = np.exp(-(r / 0.3) ** 2) * 0.9
        elif kind == "softw":
            spr = np.exp(-(r / 0.18) ** 2) * 0.6
        elif kind.startswith("cross"):
            arm = (np.abs(dx) < 0.07) | (np.abs(dy) < 0.07)
            spr = arm * np.clip(1 - r / (0.5 if kind == "cross" else 0.75), 0, 1) + np.exp(-(r / 0.16) ** 2)
        else:
            ang = np.arctan2(dy, dx)
            ray = np.abs(np.cos(ang * 3)) ** 40
            spr = ray * np.clip(1 - r / 0.8, 0, 1) * 0.8 + np.exp(-(r / 0.2) ** 2)
        a[cy:cy + cell, cx:cx + cell] = np.clip(spr, 0, 1)
        col = hexc(temps[i])
        # the core stays near white so bright stars do not read as coloured dots; the colour lives in the skirt
        core = np.exp(-(r / 0.12) ** 2)[..., None]
        rgb[cy:cy + cell, cx:cx + cell] = col[None, None, :] * (1 - core * 0.6) + core * 0.6
        rng.random()  # keep one draw per sprite so a future sprite tweak does not shift its neighbours
    return to_image(rgb, a)


# ======================================================================================= sky upgrade: aurora

def gradient_curtain(name, w, h, stops, ray_base=22, ray_gain=1.0, glow=0.0):
    """A horizontally periodic aurora curtain: RGB is the vertical gradient (``stops`` = [(v, hex)] from the
    bottom, v in 0..1 where 0 is the bottom edge of the curtain), alpha is the ray mask - dense vertical rays that
    are brightest just above the lower edge and fray upward. The renderer folds this geometry; the texture holds
    no fold of its own, so stacked layers never share a silhouette."""
    rng = rng_of(name)
    ys = np.linspace(1.0, 0.0, h, dtype=np.float32)[:, None]  # row 0 = top of the curtain
    rays = fbm(rng, w, 1, base=ray_base, octaves=4, persistence=0.62, wrap_x=True)[0]
    rays2 = fbm(rng, w, 1, base=max(3, ray_base // 3), octaves=3, wrap_x=True)[0]
    fine = fbm(rng, w, 1, base=ray_base * 3, octaves=2, wrap_x=True)[0]
    ray = np.clip((rays - 0.38) / 0.36, 0, 1) * (0.35 + 0.65 * rays2) * (0.7 + 0.3 * fine)
    ray = np.clip(ray * ray_gain, 0, 1)
    base_y = 0.10 + 0.06 * (fbm(rng, w, 1, base=4, octaves=2, wrap_x=True)[0] - 0.5)
    # bright lower edge, long upward fray whose reach varies along the curtain
    reach = 0.55 + 0.4 * fbm(rng, w, 1, base=6, octaves=2, wrap_x=True)[0]
    edge = np.exp(-((ys - base_y[None, :]) / 0.06) ** 2)
    up = np.clip(1 - (ys - base_y[None, :]) / reach[None, :], 0, 1) ** 1.6 * (ys > base_y[None, :])
    below = np.exp(-((base_y[None, :] - ys) / 0.05) ** 2) * (ys < base_y[None, :])
    vert = edge + up * 0.75 + below * 0.6
    wisps = fbm(rng, w, h, base=14, octaves=3, wrap_x=True, aspect=0.5)
    inten = ray[None, :] * vert * (0.55 + 0.45 * wisps) + glow * up * 0.2
    inten = np.clip(inten, 0, 1) * np.clip((1.0 - ys) / 0.14, 0, 1)  # nothing reaches the top edge
    rgb = np.zeros((h, w, 3), dtype=np.float32)
    vv = ys[:, 0]
    for i in range(len(stops) - 1):
        v0, c0 = stops[i]
        v1, c1 = stops[i + 1]
        t = np.clip((vv - v0) / max(1e-6, v1 - v0), 0, 1)
        seg = (vv >= v0) & (vv <= v1) if i < len(stops) - 2 else (vv >= v0)
        col = hexc(c0)[None, :] * (1 - t[:, None]) + hexc(c1)[None, :] * t[:, None]
        rgb[seg] = np.repeat(col[seg][:, None, :], w, axis=1)
    rgb[vv < stops[0][0]] = hexc(stops[0][1])
    return to_image(rgb, inten)


def aurora_fringe(name="aurora_fringe_rose", w=512, h=128):
    """Rose upper fringe for diamond-dust nights: colour only in the top 45 % of the curtain, soft rays."""
    rng = rng_of(name)
    ys = np.linspace(1.0, 0.0, h, dtype=np.float32)[:, None]
    rays = fbm(rng, w, 1, base=16, octaves=3, persistence=0.6, wrap_x=True)[0]
    ray = np.clip((rays - 0.3) / 0.6, 0, 1) ** 0.8
    band = np.clip((ys - 0.40) / 0.25, 0, 1) * np.clip((1.0 - ys) / 0.14, 0, 1)
    wisps = fbm(rng, w, h, base=10, octaves=3, wrap_x=True, aspect=0.5)
    a = np.clip(ray[None, :] * band * (0.5 + 0.5 * wisps), 0, 1)
    rgb = np.ones((h, w, 3), dtype=np.float32) * hexc("#FF7AC8")[None, None, :]
    rgb = rgb * (1 - ys[..., None] * 0.3) + hexc("#FFB3E0")[None, None, :] * ys[..., None] * 0.3
    return to_image(rgb, a)


# ======================================================================================= sky upgrade: decals

def meteor_streak(name="meteor_streak", w=128, h=16):
    """Shooting-star streak: bright head on the right, tail fading to the left. Drawn additively, tinted."""
    rng = rng_of(name)
    xs = np.linspace(0, 1, w, dtype=np.float32)[None, :]
    ys = (np.linspace(-1, 1, h, dtype=np.float32))[:, None]
    width = 0.25 + 0.75 * xs
    core = np.exp(-(ys / (width * 0.55)) ** 2)
    tail = np.clip(xs, 0, 1) ** 1.8
    head = np.exp(-(((xs - 0.93) / 0.06) ** 2 + (ys / 0.5) ** 2))
    flick = 0.85 + 0.15 * fbm(rng, w, 1, base=10, octaves=2)[0][None, :]
    a = np.clip(core * tail * flick + head, 0, 1)
    rgb = np.ones((h, w, 3), dtype=np.float32)
    rgb[..., 2] = 1.0 - 0.15 * tail  # a touch warm toward the head
    return to_image(rgb, a)


def limb_glow(name="limb_glow", size=256, radius=0.86, width=0.07):
    """Atmospheric limb: a soft ring on the disc edge of the earth textures (radius 0.86 of the quad); the profile's
    ``limbGlow.scale`` fits it to bodies whose disc radius differs."""
    dx, dy, r = radial(size)
    inner = np.exp(-(np.maximum(radius - r, 0) / (width * 1.2)) ** 2)
    outer = np.exp(-(np.maximum(r - radius, 0) / width) ** 1.2)
    a = np.where(r < radius, inner * 0.55, outer) * np.clip((1.0 - r) / 0.03, 0, 1)
    rgb = np.ones((size, size, 3), dtype=np.float32)
    return to_image(rgb, a)


def terminator(name="terminator", size=256, softness=0.3):
    """Night-side shade: black on the left half with a soft edge; alpha 0 on the right. Rotated by the renderer so the
    dark half points away from the sun, scaled to the body. Circular so nothing spills past the disc."""
    dx, dy, r = radial(size)
    edge = np.clip((-dx) / softness * 0.5 + 0.5, 0, 1)
    disc = np.clip((0.98 - r) / 0.02, 0, 1)
    a = smoothstep(edge) * disc
    rgb = np.zeros((size, size, 3), dtype=np.float32)
    return to_image(rgb, a)


def terminator_phase(name, k, size=256, softness=0.3):
    """Night-shade phase sheet frame for the lit fraction ``k`` (0 = new, 1 = full): dark where
    ``x < (1 - 2k) * sqrt(1 - y^2)`` (an elliptical terminator, local +x toward the sun), the same soft edge and
    circular mask as ``terminator``. Sixteen frames ``terminator_00..15`` (``k = i / 15``); the renderer picks
    ``round(k * 15)`` and draws it centred on the body, turned so +x faces the sun."""
    dx, dy, r = radial(size)
    yy = np.clip(dy / 0.98, -1.0, 1.0)
    limb = 0.98 * np.sqrt(np.maximum(0.0, 1.0 - yy * yy))
    c = 1.0 - 2.0 * k
    # The soft edge is pushed fully off the disc at the two ends (frame 0 all dark, frame 15 clear) with a margin
    # that vanishes towards quadrature, so the in-between frames keep the true ellipse.
    s = c * limb + softness * c * abs(c) ** 3 - dx
    edge = np.clip(s / softness * 0.5 + 0.5, 0, 1)
    disc = np.clip((0.98 - r) / 0.02, 0, 1)
    a = smoothstep(edge) * disc
    rgb = np.zeros((size, size, 3), dtype=np.float32)
    return to_image(rgb, a)


TERMINATOR_FRAMES = 16


def city_lights(name, size, source):
    """Warm light specks on the land of ``source`` (``earth`` or ``earth_disk``), denser near coasts. Additive."""
    rng = rng_of(name)
    _, _, land = earth_parts(source, size)
    land_img = Image.fromarray((land * 255).astype(np.uint8))
    coast = np.array(land_img.filter(ImageFilter.GaussianBlur(size / 64.0)), dtype=np.float32) / 255.0
    coast = coast * (1 - coast) * 4.0  # peaks on the land/sea boundary
    density = (0.35 + 0.65 * coast) * land
    n = fbm(rng, size, size, base=max(4, size // 24), octaves=3)
    specks = (rng.random((size, size)).astype(np.float32) < density * 0.028 * (0.5 + n)).astype(np.float32)
    specks *= 0.6 + 0.4 * rng.random((size, size)).astype(np.float32)
    bloom = np.array(Image.fromarray((np.clip(specks, 0, 1) * 255).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(max(0.6, size / 200.0))), dtype=np.float32) / 255.0
    a = np.clip(specks * 0.9 + bloom * 2.2, 0, 1) * land
    dx, dy, r = radial(size)
    a *= (r < 0.86)
    rgb = np.ones((size, size, 3), dtype=np.float32) * hexc("#FFD9A0")[None, None, :]
    return to_image(rgb, a)


def corona(name="corona", size=256):
    """Solar corona for totality: pearl streamers, brightest at the limb, reaching ~2.2 solar radii (the quad is
    sized by the profile so the inner edge sits on the occulting disc)."""
    rng = rng_of(name)
    dx, dy, r = radial(size)
    ang = np.arctan2(dy, dx)
    inner = 0.30
    radial_fall = np.exp(-np.maximum(r - inner, 0) / 0.22)
    streamers = fbm(rng, size, 1, base=9, octaves=4, persistence=0.6, wrap_x=True)[0]
    idx = ((ang + math.pi) / (2 * math.pi) * (size - 1)).astype(int)
    st = 0.45 + 0.55 * np.clip((streamers[idx] - 0.3) / 0.5, 0, 1)
    polar = 0.75 + 0.25 * np.abs(np.cos(ang))  # equatorial streamers reach further, polar plumes are short
    wisps = fbm(rng, size, size, base=12, octaves=3)
    a = radial_fall * st * polar * (0.7 + 0.3 * wisps)
    a *= np.clip((r - inner + 0.02) / 0.04, 0, 1)
    a = np.clip(a * 1.1, 0, 1)
    rgb = np.ones((size, size, 3), dtype=np.float32) * hexc("#FFF6EA")[None, None, :]
    rgb = rgb * (1 - np.clip(r, 0, 1)[..., None] * 0.2) + hexc("#FFD2B0")[None, None, :] * np.clip(r, 0, 1)[..., None] * 0.2
    return to_image(rgb, a)


def diamond_ring(name="diamond_ring", size=64):
    """The diamond-ring flash: a hard white point with four long rays. Placed on the limb by the renderer."""
    dx, dy, r = radial(size)
    ang = np.arctan2(dy, dx)
    core = np.exp(-(r / 0.10) ** 2)
    rays = np.abs(np.cos(ang * 2)) ** 30 * np.clip(1 - r / 0.95, 0, 1) * 0.9
    rays += np.abs(np.sin(ang * 2)) ** 60 * np.clip(1 - r / 0.6, 0, 1) * 0.5
    halo = np.exp(-(r / 0.35) ** 2) * 0.5
    a = np.clip(core + rays + halo, 0, 1)
    rgb = np.ones((size, size, 3), dtype=np.float32)
    return to_image(rgb, a)


def halo_22(name="halo_22", size=512, half_angle=26.0):
    """22-degree halo with sun dogs and an upper tangent arc, on a tangent quad of ``half_angle`` degrees.
    Inner edge red, outer edge blue-white, as ice-crystal halos are; sun dogs sit on the horizontal through the
    sun (the quad's local x axis, which the renderer keeps level)."""
    rng = rng_of(name)
    dx, dy, r = radial(size)
    r0 = math.tan(math.radians(22.0)) / math.tan(math.radians(half_angle))
    width = 0.045
    ring = np.exp(-((r - r0) / width) ** 2) * (r >= r0 * 0.93) + np.exp(-((r0 - r) / (width * 0.5)) ** 2) * (r < r0 * 0.93) * 0.4
    t = np.clip((r - r0 + width) / (2.2 * width), 0, 1)
    inner = hexc("#FFB080")
    outer = hexc("#CFE8FF")
    rgb = inner[None, None, :] * (1 - t[..., None]) + outer[None, None, :] * t[..., None]
    dogs = np.exp(-(((np.abs(dx) - r0) / 0.06) ** 2 + (dy / 0.05) ** 2)) * 1.6
    dogs += np.exp(-(((np.abs(dx) - r0 - 0.08) / 0.12) ** 2 + (dy / 0.045) ** 2)) * 0.7  # tails pointing away
    arc = np.exp(-((dy + r0) / 0.05) ** 2) * np.exp(-(dx / 0.35) ** 2) * 0.9 * (dy < 0)
    glow = np.exp(-(r / 0.22) ** 2) * 0.35  # forward-scatter brightening near the sun
    grain = 0.85 + 0.15 * fbm(rng, size, size, base=6, octaves=2)
    a = np.clip((ring * 0.45 + dogs * 1.2 + arc * 0.8 + glow) * grain, 0, 1)
    a *= np.clip((1.0 - r) / 0.05, 0, 1)
    rgb = np.where(dogs[..., None] > 0.5, hexc("#FFE8C8")[None, None, :], rgb)
    return to_image(rgb, a)


def rainbow_arc(name="rainbow_arc", size=512, half_angle=46.0):
    """Crystal-refracted bow around the anti-solar point: a 42-degree ring, violet inside to red outside, with a
    faint secondary at 51 degrees reversed. Full ring; the horizon hides what is below it."""
    rng = rng_of(name)
    dx, dy, r = radial(size)
    r1 = math.tan(math.radians(42.0)) / math.tan(math.radians(half_angle))
    r2 = math.tan(math.radians(51.0)) / math.tan(math.radians(half_angle))
    bands = ["#8A5CFF", "#4D8CFF", "#4DD0E1", "#60E0A0", "#F2E86B", "#FFA050", "#FF6B6B"]
    rgb = np.zeros((size, size, 3), dtype=np.float32)
    a = np.zeros((size, size), dtype=np.float32)
    w = 0.09
    for i, c in enumerate(bands):
        rc = r1 - w / 2 + w * (i + 0.5) / len(bands)
        m = np.exp(-((r - rc) / (w / len(bands) * 0.9)) ** 2)
        rgb += hexc(c)[None, None, :] * m[..., None]
        a += m
    for i, c in enumerate(reversed(bands)):
        rc = r2 - w / 2 + w * (i + 0.5) / len(bands)
        m = np.exp(-((r - rc) / (w / len(bands) * 1.1)) ** 2) * 0.35
        rgb += hexc(c)[None, None, :] * m[..., None]
        a += m
    rgb = rgb / np.maximum(a, 1e-4)[..., None]
    shimmer = 0.75 + 0.25 * fbm(rng, size, size, base=5, octaves=3)
    a = np.clip(a * 0.85 * shimmer, 0, 1)
    a *= np.clip((1.0 - r) / 0.04, 0, 1)
    return to_image(rgb, a)


def flare_rays(name="flare_rays", size=512):
    """Crepuscular burst for Nyxara flares: dozens of thin radial streaks of random length, brightest at the
    centre and dying before the quad edge. Two copies counter-rotate in the renderer."""
    rng = rng_of(name)
    dx, dy, r = radial(size)
    ang = (np.arctan2(dy, dx) + math.pi) / (2 * math.pi)
    n_rays = 1024
    prof = fbm(rng, n_rays, 1, base=48, octaves=4, persistence=0.65, wrap_x=True)[0]
    prof2 = fbm(rng, n_rays, 1, base=12, octaves=2, wrap_x=True)[0]
    idx = np.clip((ang * n_rays).astype(int), 0, n_rays - 1)
    strength = np.clip((prof[idx] - 0.42) / 0.3, 0, 1) ** 1.5
    reach = 0.35 + 0.6 * prof2[idx]
    a = strength * np.clip(1 - r / reach, 0, 1) ** 1.2 * np.clip(r / 0.08, 0, 1)
    a += np.exp(-(r / 0.10) ** 2) * 0.8
    grain = 0.8 + 0.2 * fbm(rng, size, size, base=20, octaves=2)
    a = np.clip(a * grain, 0, 1)
    rgb = np.ones((size, size, 3), dtype=np.float32)
    rgb = rgb * (1 - r[..., None] * 0.25) + hexc("#FFD6A0")[None, None, :] * r[..., None] * 0.25
    return to_image(rgb, a)


def caustic_band(name="caustic_band", w=512, h=64):
    """Crystal-scatter shimmer: a horizontally periodic strip of soft caustic blotches, violet-white, for a low
    band over the horizon (lit by the flare rays through the crystal)."""
    rng = rng_of(name)
    ys = np.linspace(0, 1, h, dtype=np.float32)[:, None]
    n1 = fbm(rng, w, h, base=10, octaves=4, persistence=0.6, wrap_x=True, aspect=0.4)
    n2 = fbm(rng, w, h, base=22, octaves=3, wrap_x=True, aspect=0.4)
    ridges = 1.0 - np.abs(n1 * 2 - 1)
    caustic = np.clip((ridges - 0.55) / 0.35, 0, 1) ** 1.8 * (0.6 + 0.4 * n2)
    vert = np.exp(-((ys - 0.6) / 0.22) ** 2) * np.clip(ys / 0.12, 0, 1) * np.clip((1 - ys) / 0.12, 0, 1)
    xs = np.linspace(0, 1, w, endpoint=False, dtype=np.float32)[None, :]
    ends = np.clip(np.minimum(xs, 1 - xs) / 0.12, 0, 1)  # drawn as one quad, not tiled: fade both ends
    a = np.clip(caustic * vert * ends, 0, 1)
    rgb = hexc("#E6D2FF")[None, None, :] * (1 - ys[..., None] * 0.4) + hexc("#9B5DE5")[None, None, :] * ys[..., None] * 0.4
    rgb = np.repeat(rgb, w, axis=1)
    return to_image(rgb, a)


def nebula_patch(name, size, cols, density=1.0):
    x, y, r = radial(size)
    sample = noise_space(name)
    warp = volume_fbm(sample, x * 2 + 9, y * 2, np.full_like(x, 4), 4) - 0.5
    u, v = x * 3 + warp * 2, y * 3 - warp
    cloud = volume_fbm(sample, u, v, np.full_like(x, 17), 6)
    dust = volume_fbm(sample, u * 2.7, v * 2.7, np.full_like(x, 29), 5)
    ridge = np.exp(-((cloud - 0.51) / 0.055) ** 2)
    dark = smoothstep(np.clip((dust - 0.48) / 0.18, 0, 1))
    edge = smoothstep(np.clip((0.96 - r) / 0.38, 0, 1))
    alpha = (ridge * 0.45 + np.clip((cloud - 0.43) * 2.8, 0, 1)) * (1 - dark * 0.88) * edge * density
    mix = np.clip((dust - 0.3) * 2, 0, 1)[..., None]
    rgb = hexc(cols[0]) * (1 - mix) + hexc(cols[1]) * mix
    hot = (ridge * np.clip((cloud - 0.48) * 9, 0, 1))[..., None]
    rgb = rgb * (1 - hot * 0.55) + hexc(cols[2]) * hot * 0.55
    return to_image(rgb, alpha)


def clouds_ash(name="clouds_ash", size=512):
    """Cindara's ash deck: a tileable (both axes) broken cloud layer. RGB is a neutral warm grey so the profile
    tints it (day / night / lava underglow); alpha is coverage with ragged holes."""
    rng = rng_of(name)
    lat = rng.random((9, 9)).astype(np.float32)
    lat[:, -1] = lat[:, 0]
    lat[-1, :] = lat[0, :]
    out = np.zeros((size, size), dtype=np.float32)
    amp, tot, f = 1.0, 0.0, 4
    for o in range(5):
        l = rng.random((f + 1, f + 1)).astype(np.float32)
        l[:, -1] = l[:, 0]
        l[-1, :] = l[0, :]
        xs = np.linspace(0, f, size, endpoint=False, dtype=np.float32)
        x0 = np.floor(xs).astype(int)
        tx = smoothstep(xs - x0)
        x1 = (x0 + 1) % (f + 1)
        row = l[x0][:, x0] * (1 - tx)[None, :] * (1 - tx)[:, None]
        row += l[x0][:, x1] * tx[None, :] * (1 - tx)[:, None]
        row += l[x1][:, x0] * (1 - tx)[None, :] * tx[:, None]
        row += l[x1][:, x1] * tx[None, :] * tx[:, None]
        out += amp * row
        tot += amp
        amp *= 0.55
        f *= 2
    n = out / tot
    cover = np.clip((n - 0.46) / 0.22, 0, 1)
    detail = np.clip((n - 0.52) / 0.1, 0, 1)
    a = np.clip(cover * (0.55 + 0.45 * detail), 0, 1) * 0.9
    lum = 0.55 + 0.45 * detail
    rgb = np.stack([lum, lum * 0.96, lum * 0.92], axis=-1)
    return to_image(rgb, a)


def sun_aureole(name="sun_aureole", size=128):
    """Broad soft disc for the Martian blue sunset aureole (and any horizon glow): white, tinted by the profile."""
    dx, dy, r = radial(size)
    a = np.exp(-(r / 0.45) ** 1.6) * 0.9
    a *= np.clip((1.0 - r) / 0.1, 0, 1)
    rgb = np.ones((size, size, 3), dtype=np.float32)
    return to_image(rgb, a)


AURORA_GRADIENTS = {
    # bottom -> top. Glacira: the concept sheet's aurora green into teal, pale blue at the top (the rose fringe is a
    # separate weather-gated texture). Nyxara: violet into cyan over the crystal. Mars: a ghost of green with a
    # red upper edge (weak field, CO2 emission). Greenxertz: a pink spore shimmer, no night gate.
    "glacira": [(0.0, "#1FC46A"), (0.3, "#4FE89A"), (0.6, "#3FC8C8"), (1.0, "#9ADCFF")],
    "nyxara": [(0.0, "#6B2FB8"), (0.3, "#9B5DE5"), (0.65, "#5FB8E8"), (1.0, "#4DD0E1")],
    "mars": [(0.0, "#3E9A66"), (0.5, "#60A080"), (1.0, "#C85A6A")],
    "greenxertz": [(0.0, "#FF7AC8"), (0.6, "#FFA6DC"), (1.0, "#FFD6EC")],
}

# ======================================================================================= table

def sky_textures():
    return {
        "sun_white": lambda: sun_disc("sun_white", 256, 0.34, 0.16, rays=4),
        "sun_small": lambda: sun_disc("sun_small", 128, 0.42, 0.18),
        "sun_pale": lambda: sun_disc("sun_pale", 256, 0.26, 0.28),
        "sun_amber": lambda: sun_disc("sun_amber", 256, 0.36, 0.2, rim="#FFD27A"),
        "red_giant": lambda: red_giant(size=512),
        "dark_moon": lambda: dark_moon(),
        "earth": lambda: earth("earth", 128),
        "earth_disk": lambda: earth("earth_disk", 512),
        "phobos": lambda: rocky_moon("phobos", 128, 0.7, 0.58, 4, potato=0.35),
        "deimos": lambda: rocky_moon("deimos", 128, 0.62, 0.66, 2, potato=0.25),
        "rime": lambda: rocky_moon("rime", 256, 0.8, 0.95, 7),
        "hoar": lambda: rocky_moon("hoar", 256, 0.8, 0.8, 12),
        "moon_small": lambda: rocky_moon("moon_small", 128, 0.8, 0.85, 5),
        "galaxy": lambda: galaxy_band("galaxy", 2048, 512, "#FFD0EF", "#697CFF", "#170E35", "#E8F5FF",
                                      nebula=("#E95ABD", "#3DE4E1")),
        "galaxy_nyx": lambda: galaxy_band("galaxy_nyx", 2048, 512, "#FFC0EC", "#7E3FE0", "#180A2E", "#FFF0FF",
                                          density=1.25, nebula=("#C64DFF", "#34E6DE")),
        "aurora_0": lambda: aurora_strip("aurora_0", 512, 128, 0.0),
        "aurora_1": lambda: aurora_strip("aurora_1", 512, 128, 0.5),
        "stars": lambda: star_sheet(),
        "horizon_ramp": lambda: horizon_ramp(),
        # ---- sky upgrade ----
        "stars_natural": lambda: star_sheet_natural(),
        "aurora_glacira_0": lambda: gradient_curtain("aurora_glacira_0", 512, 128, AURORA_GRADIENTS["glacira"], ray_base=22),
        "aurora_glacira_1": lambda: gradient_curtain("aurora_glacira_1", 512, 128, AURORA_GRADIENTS["glacira"], ray_base=16, ray_gain=0.9),
        "aurora_glacira_2": lambda: gradient_curtain("aurora_glacira_2", 512, 128, AURORA_GRADIENTS["glacira"], ray_base=30, ray_gain=0.8, glow=0.4),
        "aurora_nyxara_0": lambda: gradient_curtain("aurora_nyxara_0", 512, 128, AURORA_GRADIENTS["nyxara"], ray_base=20),
        "aurora_nyxara_1": lambda: gradient_curtain("aurora_nyxara_1", 512, 128, AURORA_GRADIENTS["nyxara"], ray_base=28, ray_gain=0.85, glow=0.3),
        "aurora_mars_0": lambda: gradient_curtain("aurora_mars_0", 512, 128, AURORA_GRADIENTS["mars"], ray_base=12, ray_gain=0.7, glow=0.5),
        "aurora_greenxertz_0": lambda: gradient_curtain("aurora_greenxertz_0", 512, 128, AURORA_GRADIENTS["greenxertz"], ray_base=10, ray_gain=0.6, glow=0.8),
        "aurora_fringe_rose": lambda: aurora_fringe(),
        "meteor_streak": lambda: meteor_streak(),
        "limb_glow": lambda: limb_glow(),
        "terminator": lambda: terminator(),
        **{"terminator_%02d" % i: (lambda i=i: terminator_phase("terminator_%02d" % i, i / (TERMINATOR_FRAMES - 1.0)))
           for i in range(TERMINATOR_FRAMES)},
        "earth_globe": lambda: earth_globe("earth_globe", 512, "earth"),
        "earth_disk_globe": lambda: earth_globe("earth_disk_globe", 1024, "earth_disk"),
        "earth_lights": lambda: city_lights("earth_lights", 512, "earth"),
        "earth_disk_lights": lambda: city_lights("earth_disk_lights", 1024, "earth_disk"),
        "corona": lambda: corona(),
        "diamond_ring": lambda: diamond_ring(),
        "halo_22": lambda: halo_22(),
        "rainbow_arc": lambda: rainbow_arc(),
        "flare_rays": lambda: flare_rays(),
        "caustic_band": lambda: caustic_band(),
        "dust_copper": lambda: nebula_patch("dust_copper", 512, ("#FF754A", "#A34DD9", "#FFE0A0"), 0.7),
        "dust_jade": lambda: nebula_patch("dust_jade", 512, ("#34E9B2", "#397BCC", "#DAFF9A"), 0.8),
        "dust_ice": lambda: nebula_patch("dust_ice", 512, ("#48D9FF", "#7E64F4", "#FFD0F0"), 0.6),
        "nebula_a": lambda: nebula_patch("nebula_a", 1024, ("#FF57B5", "#9147E8", "#FFD6F1")),
        "nebula_b": lambda: nebula_patch("nebula_b", 1024, ("#36DFDB", "#4363DC", "#B1EDFF"), density=0.8),
        "clouds_ash": lambda: clouds_ash(),
        "sun_aureole": lambda: sun_aureole(),
        "horizon_ramp_soft": lambda: horizon_ramp_soft(),
    }


def horizon_ramp(name="horizon_ramp", w=4, h=64):
    """White strip whose alpha rises from 0 (top) to 1 (bottom) on a t^1.6 curve. The renderer wraps it round the
    horizon and tints it with the profile's horizon colour (and again with the haze colour), so one texture makes
    the zenith-to-horizon gradient of every sky."""
    t = (np.arange(h, dtype=np.float32) + 0.5) / h
    alpha = np.repeat((t ** 1.6)[:, None], w, axis=1)
    rgb = np.ones((h, w, 3), dtype=np.float32)
    return to_image(rgb, alpha)


def horizon_ramp_soft(name="horizon_ramp_soft", w=4, h=64):
    """Like ``horizon_ramp`` but eased at the top (smoothstep^1.3) for a taller ramp (72 degrees), so the zenith
    colour has no visible rim where the ramp begins."""
    t = (np.arange(h, dtype=np.float32) + 0.5) / h
    alpha = np.repeat((smoothstep(t) ** 1.3)[:, None], w, axis=1)
    rgb = np.ones((h, w, 3), dtype=np.float32)
    return to_image(rgb, alpha)


PARTICLE_TYPES = ["dust", "ash", "spore", "diamond_dust", "ember", "nebula_mote"]


def write_all(root, force=False, preview=None):
    base = os.path.join(root, ASSET_DIR)
    written = 0
    sheet = []
    for name, fn in sky_textures().items():
        img = fn()
        if save(img, os.path.join(base, "sky", name + ".png"), force):
            written += 1
        sheet.append((name, img))
    for kind in PARTICLE_TYPES:
        for i, img in enumerate(particle_frames(kind)):
            fname = "%s_%d" % (kind, i)
            if save(img, os.path.join(base, "particle", fname + ".png"), force):
                written += 1
            sheet.append((fname, img))
    print("gen_sky_textures: %d file(s) written under %s" % (written, base))
    if preview:
        contact_sheet(sheet, os.path.join(preview, "sky_textures_contact.png"))


def contact_sheet(items, path):
    cell = 136
    cols = 8
    rows = (len(items) + cols - 1) // cols
    from PIL import ImageDraw
    out = Image.new("RGBA", (cols * cell, rows * (cell + 14)), (24, 28, 36, 255))
    d = ImageDraw.Draw(out)
    for i, (name, img) in enumerate(items):
        x = (i % cols) * cell
        y = (i // cols) * (cell + 14)
        # checker so alpha is visible
        for cy in range(0, cell, 8):
            for cx in range(0, cell, 8):
                c = (52, 56, 64, 255) if ((cx + cy) // 8) % 2 else (40, 44, 52, 255)
                d.rectangle([x + cx, y + cy, x + cx + 7, y + cy + 7], fill=c)
        im = img.copy()
        scale = max(1, min(cell // im.width, cell // im.height))
        if im.width < 32:
            scale = 128 // im.width
        im = im.resize((min(cell, im.width * scale), min(cell, im.height * scale)), Image.NEAREST if im.width <= 32 else Image.BILINEAR)
        out.alpha_composite(im, (x + (cell - im.width) // 2, y + (cell - im.height) // 2))
        d.text((x + 3, y + cell + 1), name, fill=(220, 230, 240, 255))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path)
    print("gen_sky_textures: contact sheet -> %s" % path)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    ap.add_argument("--root", default=DEFAULT_ROOT, help="resource root (default: parent of tools/)")
    ap.add_argument("--force", action="store_true", help="overwrite existing files")
    ap.add_argument("--preview", default=None, help="also write a contact sheet into this folder")
    ap.add_argument("--check", action="store_true", help="compare a fresh render with the committed files (writes nothing)")
    args = ap.parse_args(argv)
    if args.check:
        sys.exit(check(args.root))
    write_all(args.root, args.force, args.preview)


def check(root):
    """Renders every texture into a temp root and compares bytes with ``root``; returns 1 on any drift."""
    tmp = tempfile.mkdtemp(prefix="gen_sky_check_")
    try:
        write_all(tmp, True)
        fresh = os.path.join(tmp, ASSET_DIR)
        bad = []
        count = 0
        for dirpath, _, files in os.walk(fresh):
            for f in sorted(files):
                rel = os.path.relpath(os.path.join(dirpath, f), fresh)
                count += 1
                target = os.path.join(root, ASSET_DIR, rel)
                if not os.path.isfile(target):
                    bad.append(rel + " (missing)")
                    continue
                with open(os.path.join(dirpath, f), "rb") as a, open(target, "rb") as b:
                    if a.read() != b.read():
                        bad.append(rel)
        for b in bad:
            print("gen_sky_textures: stale " + b)
        print("gen_sky_textures: %d of %d texture(s) ok" % (count - len(bad), count))
        return 1 if bad else 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
