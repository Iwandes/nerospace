#!/usr/bin/env python3
"""
Generate the NEROSPACE mod logo (square).

NeroSpace is the flagship, so it wears the shared Neroland family emblem — the
faceted pointy-top "core" hexagon with a glowing centre node (same geometry as
Neroland Core, Nerotech, NeroLogistics and NeroLink) — rendered in NeroSpace's
crimson/magenta/violet palette and made the hero: a ringed planet and a clean
flat-style rocket orbit the core over a deep-space starfield, beneath the
NEROSPACE wordmark.

Renders supersampled (SS) then downsamples for clean edges.
Outputs:
  art/logo/nerospace_logo.png                     (1024x1024 master)
  art/logo/nerospace_logo_400.png                 (CurseForge/Modrinth-ready)
  common/src/main/resources/nerospace_logo.png    (256x256 in-game mods-list icon)
"""
import math
import os
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "art/logo")
ICON = os.path.join(ROOT, "common/src/main/resources")
os.makedirs(OUT, exist_ok=True)
os.makedirs(ICON, exist_ok=True)

FINAL = 1024
SS = 2
R = FINAL * SS
rng = random.Random(7)

# palette
N_DARK   = (43, 13, 58)
N_PURPLE = (106, 31, 140)
N_MAG    = (179, 39, 158)
N_RED    = (224, 58, 58)
N_REDHI  = (255, 96, 110)
N_REDDK  = (176, 38, 44)
N_GLOW   = (255, 138, 216)
N_BRIGHT = (255, 214, 242)
# rocket colours (from the approved Option-A mockup)
RK_RED    = (230, 57, 70)
RK_PURPLE = (122, 47, 160)
RK_MAG    = (199, 36, 177)
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def _font(size):
    for p in (FONT_BOLD, "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


# ---------- background ----------
def background():
    top = np.array([8, 5, 20], float); bot = np.array([26, 10, 40], float)
    yy = np.linspace(0, 1, R)[:, None, None]
    img = top[None, None, :] * (1 - yy) + bot[None, None, :] * yy
    img = np.repeat(img, R, axis=1)
    Y, X = np.mgrid[0:R, 0:R].astype(float)

    def glow(cx, cy, rad, color, strength):
        d = np.sqrt((X - cx) ** 2 + (Y - cy) ** 2)
        f = np.clip(1 - d / rad, 0, 1) ** 2 * strength
        for c in range(3):
            img[:, :, c] += color[c] * f

    glow(R * 0.50, R * 0.42, R * 0.46, (150, 30, 90), 0.38)   # central red-magenta aura
    glow(R * 0.74, R * 0.28, R * 0.44, (70, 30, 150), 0.40)   # purple nebula (planet side)
    glow(R * 0.26, R * 0.62, R * 0.40, (150, 40, 70), 0.30)   # warm nebula (rocket side)

    d = np.sqrt((X - R / 2) ** 2 + (Y - R / 2) ** 2) / (R * 0.72)
    vig = np.clip(1 - (d ** 2) * 0.85, 0.25, 1)
    img *= vig[:, :, None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def add_stars(base):
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0)); d = ImageDraw.Draw(layer)
    for _ in range(520):
        x, y = rng.randint(0, R), rng.randint(0, R)
        s = rng.choice([1, 1, 1, 2, 2, 3]) * SS
        b = rng.randint(120, 255)
        tint = rng.choice([(b, b, b), (b, b, 255), (255, b, b), (255, 200, 255)])
        d.ellipse([x, y, x + s, y + s], fill=tint + (rng.randint(120, 255),))
    for _ in range(7):
        x, y = rng.randint(int(R * 0.05), int(R * 0.95)), rng.randint(int(R * 0.05), int(R * 0.6))
        L = rng.randint(14, 26) * SS
        col = (255, 230, 250, 230)
        d.line([x - L, y, x + L, y], fill=col, width=SS)
        d.line([x, y - L, x, y + L], fill=col, width=SS)
        d.ellipse([x - 3 * SS, y - 3 * SS, x + 3 * SS, y + 3 * SS], fill=(255, 255, 255, 255))
    base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(3 * SS)))
    base.alpha_composite(layer)
    return base


def soft_glow(draw_fn, blur):
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    return layer.filter(ImageFilter.GaussianBlur(blur))


# ---------- orbital ring ----------
def orbit(base, cx, cy):
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0)); d = ImageDraw.Draw(layer)
    rw, rh = int(R * 0.365), int(R * 0.152)
    d.ellipse([cx - rw, cy - rh, cx + rw, cy + rh], outline=(255, 120, 190, 150), width=max(1, SS * 2))
    base.alpha_composite(layer.rotate(-20, center=(cx, cy), resample=Image.BICUBIC))
    return base


# ---------- planet + ring ----------
def planet(base, cx, cy, rad, moon=True):
    base.alpha_composite(soft_glow(
        lambda d: d.ellipse([cx - rad - 18 * SS, cy - rad - 18 * SS, cx + rad + 18 * SS, cy + rad + 18 * SS],
                            fill=(150, 60, 180, 150)), 16 * SS))
    ring_layer = Image.new("RGBA", (R, R), (0, 0, 0, 0)); rd = ImageDraw.Draw(ring_layer)
    rw, rh = int(rad * 2.15), int(rad * 0.62)
    for i, col in enumerate([(150, 90, 170, 210), (210, 140, 200, 230), (120, 70, 150, 200)]):
        off = i * 6 * SS
        rd.ellipse([cx - rw + off, cy - rh // 2 + off, cx + rw - off, cy + rh // 2 - off], outline=col, width=5 * SS)
    ring_rot = ring_layer.rotate(-22, center=(cx, cy), resample=Image.BICUBIC)
    base.alpha_composite(ring_rot)
    pl = np.zeros((R, R, 4), np.uint8)
    Y, X = np.mgrid[0:R, 0:R].astype(float)
    dist = np.sqrt((X - cx) ** 2 + (Y - cy) ** 2); mask = dist <= rad
    band = (np.sin((Y - cy) / rad * 6.0) * 0.5 + 0.5)
    base_c = np.array(N_PURPLE, float); band_c = np.array(N_RED, float)
    body = base_c[None, None, :] * (1 - band[:, :, None] * 0.5) + band_c[None, None, :] * (band[:, :, None] * 0.5)
    lx, ly = -0.6, -0.7
    nx = (X - cx) / rad; ny = (Y - cy) / rad
    nz = np.sqrt(np.clip(1 - nx ** 2 - ny ** 2, 0, 1))
    lit = np.clip(nx * lx + ny * ly + nz * 0.65, 0, 1)
    shade = 0.25 + 0.95 * lit
    body = body * shade[:, :, None]
    rim = np.clip((dist - rad * 0.82) / (rad * 0.18), 0, 1) * (lit ** 0.5)
    body += np.array(N_GLOW, float)[None, None, :] * (rim[:, :, None] * 0.5)
    pl[:, :, :3] = np.clip(body, 0, 255).astype(np.uint8); pl[:, :, 3] = (mask * 255).astype(np.uint8)
    base.alpha_composite(Image.fromarray(pl, "RGBA"))
    front = ring_rot.copy(); fa = np.array(front)
    ygrid = np.mgrid[0:R, 0:R][0]; keep = (ygrid > cy - rad * 0.05); fa[~keep] = 0
    base.alpha_composite(Image.fromarray(fa, "RGBA"))
    return base


# ---------- rocket (Option-A style) ----------
def rocket(base, cx, cy, scale=1.12, tilt=25):
    """White body, red nose + swept fins, blue window with a white ring up top,
    purple then magenta band, narrow orange flame, right-side shading. Leans left."""
    W = int(360 * scale); H = int(620 * scale); pad = int(170 * scale)
    lay = Image.new("RGBA", (W + pad * 2, H + pad * 2), (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
    ox, oy = pad, pad; bw = 150 * scale; mx = ox + W / 2
    body_top = oy + 150 * scale; body_bot = oy + 470 * scale
    # flame
    fl = Image.new("RGBA", lay.size, (0, 0, 0, 0)); fd = ImageDraw.Draw(fl)
    fd.polygon([(mx - bw * 0.32, body_bot), (mx + bw * 0.32, body_bot), (mx, body_bot + 180 * scale)], fill=(255, 170, 60, 255))
    fd.polygon([(mx - bw * 0.18, body_bot), (mx + bw * 0.18, body_bot), (mx, body_bot + 120 * scale)], fill=(255, 90, 40, 255))
    fl = fl.filter(ImageFilter.GaussianBlur(3 * SS)); lay = Image.alpha_composite(lay, fl); d = ImageDraw.Draw(lay)
    # fins
    d.polygon([(mx - bw / 2, body_bot - 90 * scale), (mx - bw / 2 - 55 * scale, body_bot + 30 * scale), (mx - bw / 2, body_bot)], fill=RK_RED)
    d.polygon([(mx + bw / 2, body_bot - 90 * scale), (mx + bw / 2 + 55 * scale, body_bot + 30 * scale), (mx + bw / 2, body_bot)], fill=RK_RED)
    # body
    d.rounded_rectangle([mx - bw / 2, body_top, mx + bw / 2, body_bot], radius=28 * scale, fill=(245, 245, 250))
    # right-side shading
    sh = Image.new("RGBA", lay.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([mx, body_top, mx + bw / 2, body_bot], radius=28 * scale, fill=(60, 40, 90, 60))
    lay = Image.alpha_composite(lay, sh); d = ImageDraw.Draw(lay)
    # nose cone
    d.polygon([(mx - bw / 2, body_top + 6), (mx + bw / 2, body_top + 6), (mx, oy + 30 * scale)], fill=RK_RED)
    # accent bands (purple upper, magenta lower)
    d.rectangle([mx - bw / 2, body_top + 150 * scale, mx + bw / 2, body_top + 180 * scale], fill=RK_PURPLE)
    d.rectangle([mx - bw / 2, body_bot - 60 * scale, mx + bw / 2, body_bot - 30 * scale], fill=RK_MAG)
    # window
    wr = 42 * scale; wy = body_top + 95 * scale
    d.ellipse([mx - wr, wy - wr, mx + wr, wy + wr], fill=(150, 205, 235))
    d.ellipse([mx - wr * 0.95, wy - wr * 0.95, mx + wr * 0.95, wy + wr * 0.95], outline=(235, 245, 255), width=int(6 * scale))
    d.ellipse([mx - wr * 0.5, wy - wr * 0.55, mx + wr * 0.1, wy + wr * 0.05], fill=(210, 235, 250))

    lay = lay.rotate(tilt, center=(lay.size[0] / 2, lay.size[1] / 2), resample=Image.BICUBIC, expand=False)
    base.alpha_composite(lay, (int(cx - lay.size[0] / 2), int(cy - lay.size[1] / 2)))
    return base


# ---------- shared family core hexagon ----------
def core_hex(base, cx, cy, rad):
    base.alpha_composite(soft_glow(
        lambda dr: dr.ellipse([cx - rad * 1.3, cy - rad * 1.3, cx + rad * 1.3, cy + rad * 1.3], fill=(255, 70, 140, 110)), 26 * SS))
    base.alpha_composite(soft_glow(
        lambda dr: dr.ellipse([cx - rad * 1.05, cy - rad * 1.05, cx + rad * 1.05, cy + rad * 1.05], fill=(180, 60, 200, 90)), 14 * SS))
    hexpts = [(cx + math.cos(math.radians(60 * i - 90)) * rad, cy + math.sin(math.radians(60 * i - 90)) * rad) for i in range(6)]
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0)); d = ImageDraw.Draw(layer)
    facet_cols = [N_MAG, N_PURPLE, N_REDHI, N_PURPLE, N_RED, N_MAG]
    for i in range(6):
        p1 = hexpts[i]; p2 = hexpts[(i + 1) % 6]
        shade = 0.60 + 0.40 * (i / 5.0)
        col = tuple(int(c * shade) for c in facet_cols[i])
        d.polygon([(cx, cy), p1, p2], fill=col + (255,))
    ir = rad * 0.34
    d.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], fill=N_BRIGHT + (255,))
    d.ellipse([cx - ir * 0.5, cy - ir * 0.5, cx + ir * 0.5, cy + ir * 0.5], fill=(255, 255, 255, 255))
    for i in range(6):
        d.line([hexpts[i], hexpts[(i + 1) % 6]], fill=(255, 220, 240, 235), width=max(1, SS * 2))
        d.line([(cx, cy), hexpts[i]], fill=(255, 210, 235, 150), width=max(1, SS))
    base.alpha_composite(layer)
    sx, sy = cx - rad * 0.30, cy - rad * 0.42
    base.alpha_composite(soft_glow(lambda dr: dr.ellipse([sx - 9 * SS, sy - 9 * SS, sx + 9 * SS, sy + 9 * SS], fill=(255, 255, 255, 255)), 5 * SS))
    dd = ImageDraw.Draw(base); L = 20 * SS
    dd.line([sx - L, sy, sx + L, sy], fill=(255, 255, 255, 230), width=SS * 2)
    dd.line([sx, sy - L, sx, sy + L], fill=(255, 255, 255, 230), width=SS * 2)
    return base


# ---------- wordmark ----------
def wordmark(base):
    text = "NEROSPACE"; fsize = int(R * 0.118); font = _font(fsize); spacing = int(fsize * 0.10)
    widths = [ImageDraw.Draw(base).textlength(c, font=font) for c in text]
    total = sum(widths) + spacing * (len(text) - 1); x0 = (R - total) / 2; y0 = int(R * 0.80)

    def draw_text(dr, fill):
        x = x0
        for c, w in zip(text, widths):
            dr.text((x, y0), c, font=font, fill=fill); x += w + spacing

    gl = Image.new("RGBA", (R, R), (0, 0, 0, 0)); draw_text(ImageDraw.Draw(gl), (255, 80, 160, 255))
    base.alpha_composite(gl.filter(ImageFilter.GaussianBlur(10 * SS)))
    base.alpha_composite(gl.filter(ImageFilter.GaussianBlur(4 * SS)))
    out = Image.new("RGBA", (R, R), (0, 0, 0, 0)); draw_text(ImageDraw.Draw(out), (20, 8, 30, 255))
    out = out.filter(ImageFilter.MaxFilter(2 * SS + 1)); base.alpha_composite(out)
    draw_text(ImageDraw.Draw(base), (245, 240, 255, 255))
    tf = _font(int(R * 0.030))
    tag = "S P A C E   •   T E C H   •   E X P L O R A T I O N"
    tw = ImageDraw.Draw(base).textlength(tag, font=tf)
    ImageDraw.Draw(base).text(((R - tw) / 2, y0 + fsize * 1.06), tag, font=tf, fill=(200, 170, 220, 255))
    return base


def main():
    img = background()
    img = add_stars(img)
    cx, cy, rad = int(R * 0.50), int(R * 0.42), int(R * 0.205)
    img = orbit(img, cx, cy)
    img = planet(img, int(R * 0.822), int(R * 0.273), int(R * 0.078), moon=False)
    img = rocket(img, int(R * 0.168), int(R * 0.584), scale=1.12, tilt=25)
    img = core_hex(img, cx, cy, rad)
    img = wordmark(img)

    final = img.convert("RGB").resize((FINAL, FINAL), Image.LANCZOS)
    p1 = os.path.join(OUT, "nerospace_logo.png")
    p2 = os.path.join(OUT, "nerospace_logo_400.png")
    p3 = os.path.join(ICON, "nerospace_logo.png")
    final.save(p1)
    final.resize((400, 400), Image.LANCZOS).save(p2)
    final.resize((256, 256), Image.LANCZOS).save(p3)
    for p in (p1, p2, p3):
        print("wrote", os.path.relpath(p, ROOT))




def generate_gif(source=None, output=None):
    source = source or os.path.join(OUT, "nerospace_logo.png")
    output = output or os.path.join(OUT, "nerospace_logo.gif")
    with Image.open(source) as image:
        logo = image.convert("RGB")
    logo.thumbnail((400, 400), Image.Resampling.LANCZOS)
    w, h = logo.size
    color = (255, 138, 216)
    frames = []
    for index in range(16):
        t = index / 16
        phase = 2 * math.pi * t
        overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        draw = ImageDraw.Draw(overlay)
        for j in range(5):
            u=(t+j/5)%1
            x=0.17-0.07*u
            y=0.57+0.09*u
            r=0.013*(1-u)+0.004
            draw.ellipse((w*(x-r),h*(y-r),w*(x+r),h*(y+r)),fill=(255,155,90,int(200*(1-u))))
        for trail in range(6, -1, -1):
            a = phase - trail * 0.07
            x, y = 0.5 + 0.34 * math.cos(a), 0.42 + 0.18 * math.sin(a)
            r = 0.012 * (1 - trail / 9)
            draw.ellipse((w*(x-r), h*(y-r), w*(x+r), h*(y+r)), fill=color+(int(230*(1-trail/7)),))
        pulse = (1 - math.cos(phase)) / 2
        draw.ellipse((w*0.46, h*0.38, w*0.54, h*0.46), fill=(255, 200, 245, int(85*pulse)))
        overlay = overlay.filter(ImageFilter.MaxFilter(3))
        image = logo.convert("RGBA")
        image.alpha_composite(overlay.filter(ImageFilter.GaussianBlur(max(1, w * 0.008))))
        image.alpha_composite(overlay)
        frames.append(image.convert("RGB"))
    frames[0].save(output, save_all=True, append_images=frames[1:], duration=180, loop=0, optimize=True)
    return output


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Generate the logo or animate its existing master PNG.")
    parser.add_argument("--gif", action="store_true", help="generate the animated logo")
    args = parser.parse_args()
    if args.gif:
        print("wrote", os.path.relpath(generate_gif(), ROOT))
    else:
        main()
