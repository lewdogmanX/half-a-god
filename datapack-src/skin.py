"""Paints the "paper and ink" skin for Mebahel's quest board and contract notes (the always-on Half a God skin pack).

Run by hand when the look changes:  python3 skin.py <Mebahel's jar> <mod root>
It reads Mebahel's own textures only to keep their shapes (where the paper is, where the pin and the seal go, where
the writing sits) so his screens' layouts still line up, and paints everything at sixteen times the resolution: the
board as dark walnut with brass corners and a gold line, the notes as aged parchment with ink, brass tacks and red
wax seals. The textures are drawn by his code with explicit texture sizes, so bigger images just look sharper."""
import io
import math
import os
import random
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 16  # how much bigger the notes get
INK = (59, 42, 26)
PAPER = (237, 224, 194)
GOLD = (201, 165, 90)
RED = (142, 27, 27)
WOOD = (42, 28, 17)


def noise(w, h, scale, seed, octaves=3):
    """smooth value noise in [-1, 1]"""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        cw, ch = max(2, int(w / scale) + 2), max(2, int(h / scale) + 2)
        grid = Image.fromarray(((rng.random((ch, cw)) * 255).astype(np.uint8)))
        up = np.asarray(grid.resize((w, h), Image.BICUBIC), np.float32) / 127.5 - 1
        out += up * amp
        total += amp
        amp *= 0.5
        scale /= 2
    return out / total


def classify(im):
    """per pixel: 0 empty, 1 paper, 2 edge, 3 ink, 4 pin, 5 seal"""
    a = np.asarray(im.convert("RGBA")).astype(int)
    h, w = a.shape[:2]
    cls = np.zeros((h, w), int)
    for y in range(h):
        for x in range(w):
            r, g, b, al = a[y, x]
            if al == 0:
                continue
            lum = (r + g + b) / 3
            if r > g + 50 and r > b + 50:
                cls[y, x] = 5
            elif max(r, g, b) - min(r, g, b) < 18 and lum < 140:
                cls[y, x] = 4
            elif lum > 205:
                cls[y, x] = 1
            else:
                edge = any(not (0 <= y + dy < h and 0 <= x + dx < w) or a[y + dy, x + dx][3] == 0
                           for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)))
                cls[y, x] = 2 if edge else 3
    return cls


def paper_mask(cls, seed):
    """the original silhouette, smoothed, with a torn (deckled) edge"""
    h, w = cls.shape
    m = Image.fromarray((cls > 0).astype(np.uint8) * 255).resize((w * S, h * S), Image.NEAREST)
    m = np.asarray(m.filter(ImageFilter.GaussianBlur(S * 0.45)), np.float32) / 255
    m = m + noise(w * S, h * S, 9, seed, 2) * 0.09
    return (m > 0.5).astype(np.float32)


def note(src, seed, clean, kind):
    cls = classify(src)
    if kind == "escort":
        cls[cls == 5] = 3  # the escort maps mark the way in ink, not wax
    h, w = cls.shape
    W, H = w * S, h * S
    rnd = random.Random(seed)
    mask = paper_mask(cls, seed)
    # parchment: warm in the middle, browner towards the edges, with fibres and old stains
    dist = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(S * 0.9)), np.float32) / 255
    edge = np.clip((1 - dist) * 1.8, 0, 1) * mask
    fib = noise(W, H, 3, seed + 1, 2) * 7 + noise(W, H, 40, seed + 2, 3) * 9
    base = np.zeros((H, W, 3), np.float32)
    for i, c in enumerate(PAPER):
        dark = (168, 135, 90)[i]
        base[..., i] = c * (1 - edge * 0.55) + dark * edge * 0.55 + fib
    # a hairline of darker paper right at the torn edge
    rim = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(5)), np.float32) / 255
    rim = mask - rim
    for i, c in enumerate((122, 92, 58)):
        base[..., i] = base[..., i] * (1 - rim * 0.8) + c * rim * 0.8
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8)).convert("RGBA")
    img.putalpha(Image.fromarray((mask * 255).astype(np.uint8)))
    d = ImageDraw.Draw(img, "RGBA")

    ink_pts = [(x, y) for y in range(h) for x in range(w) if cls[y, x] == 3]
    if not clean and ink_pts:
        if kind == "escort":
            # a dashed road across a little map, and an X where it ends
            pts = sorted(ink_pts)
            path = [((x + 0.5) * S, (y + 0.5) * S) for x, y in pts]
            for i in range(len(path) - 1):
                (x0, y0), (x1, y1) = path[i], path[i + 1]
                if abs(x1 - x0) + abs(y1 - y0) > S * 2.5:
                    continue
                for t in np.linspace(0, 1, 4):
                    if int(t * 3) % 2 == 0:
                        xa, ya = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
                        d.ellipse((xa - 3, ya - 3, xa + 3, ya + 3), fill=INK + (215,))
            xe, ye = path[-1]
            for s in (-1, 1):
                d.line((xe - 14, ye - 14 * s, xe + 14, ye + 14 * s), fill=RED + (230,), width=6)
        else:
            # handwriting: a few lines of words in ink
            rows = {}
            for x, y in ink_pts:
                rows.setdefault(y, []).append(x)
            for y, xs in rows.items():
                x0, x1 = min(xs) * S + 4, (max(xs) + 1) * S - 4
                cy = (y + 0.5) * S
                x = x0
                while x < x1 - 8:
                    word = rnd.randint(18, 54)
                    xe = min(x1, x + word)
                    pts = [(xx, cy + math.sin(xx * 0.35 + seed) * 1.6 + rnd.uniform(-0.8, 0.8)) for xx in range(int(x), int(xe), 3)]
                    if len(pts) > 1:
                        d.line(pts, fill=INK + (205,), width=rnd.choice((3, 4)))
                    x = xe + rnd.randint(7, 12)
    # the wax seal (the Watch's red), embossed
    seal = [(x, y) for y in range(h) for x in range(w) if cls[y, x] == 5]
    if seal:
        cx = (sum(p[0] for p in seal) / len(seal) + 0.5) * S
        cy = (sum(p[1] for p in seal) / len(seal) + 0.5) * S
        r = 24
        for k in range(10):
            a = rnd.uniform(0, math.tau)
            rr = r + rnd.uniform(1, 6)
            d.ellipse((cx + math.cos(a) * rr * 0.6 - 9, cy + math.sin(a) * rr * 0.6 - 9, cx + math.cos(a) * rr * 0.6 + 9, cy + math.sin(a) * rr * 0.6 + 9), fill=(110, 18, 18, 255))
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(94, 15, 15, 255))
        d.ellipse((cx - r + 4, cy - r + 4, cx + r - 4, cy + r - 4), fill=RED + (255,))
        d.ellipse((cx - 13, cy - 13, cx + 13, cy + 13), outline=(178, 64, 50, 255), width=3)
        d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=(178, 64, 50, 255))
    # the brass tack holding it to the board
    pin = [(x, y) for y in range(h) for x in range(w) if cls[y, x] == 4]
    if pin:
        cx = (sum(p[0] for p in pin) / len(pin) + 0.5) * S
        cy = (min(p[1] for p in pin) + 1.0) * S
        d.ellipse((cx - 13 + 4, cy - 13 + 5, cx + 13 + 4, cy + 13 + 5), fill=(40, 26, 14, 110))
        d.ellipse((cx - 13, cy - 13, cx + 13, cy + 13), fill=(120, 88, 38, 255))
        d.ellipse((cx - 10, cy - 11, cx + 10, cy + 9), fill=GOLD + (255,))
        d.ellipse((cx - 6, cy - 8, cx + 1, cy - 2), fill=(240, 214, 150, 255))
    return img


def board(src):
    """the board: dark walnut planks in a frame, a gold line inside it, brass corners (4x, same layout)"""
    k = 4
    sheet = Image.new("RGBA", (src.width * k, src.height * k), (0, 0, 0, 0))
    a = np.asarray(src.convert("RGBA"))
    ys, xs = np.nonzero(a[..., 3])
    x0, y0, x1, y1 = xs.min() * k, ys.min() * k, (xs.max() + 1) * k, (ys.max() + 1) * k
    W, H = x1 - x0, y1 - y0
    grain = noise(W, H, 6, 7, 3)
    streak = noise(W, H // 8 + 1, 30, 8, 2)
    streak = np.asarray(Image.fromarray(((streak + 1) * 127).astype(np.uint8)).resize((W, H), Image.BICUBIC), np.float32) / 127 - 1
    wood = np.zeros((H, W, 3), np.float32)
    frame = 26
    planks = 4
    inner_h = H - 2 * frame
    for y in range(H):
        for c in range(3):
            pass
    yy, xx = np.mgrid[0:H, 0:W]
    plank = np.clip(((yy - frame) * planks) // max(1, inner_h), 0, planks - 1)
    tone = np.array([0.0, 6.0, -4.0, 3.0])[plank]
    for i, (lo, hi) in enumerate(((58, 82), (38, 55), (24, 34))):
        v = (lo + hi) / 2 + grain * 6 + streak * 9 + tone
        wood[..., i] = v
    # seams between planks
    seam = np.zeros((H, W), bool)
    for p in range(1, planks):
        sy = frame + p * inner_h // planks
        seam |= (yy >= sy - 1) & (yy <= sy + 1)
    wood[seam] = (26, 17, 10)
    # the frame: darker, with its own grain, mitred at the corners
    fr = (yy < frame) | (yy >= H - frame) | (xx < frame) | (xx >= W - frame)
    fgrain = noise(W, H, 4, 9, 3)
    for i, c in enumerate(WOOD):
        wood[..., i] = np.where(fr, c + 10 + fgrain * 7, wood[..., i])
    # a gold line just inside the frame, and a shadow under it
    gl = ((yy == frame + 5) | (yy == H - frame - 6)) & (xx >= frame + 5) & (xx < W - frame - 5) \
        | ((xx == frame + 5) | (xx == W - frame - 6)) & (yy >= frame + 5) & (yy < H - frame - 5)
    gl2 = ((yy == frame + 6) | (yy == H - frame - 5)) & (xx >= frame + 5) & (xx < W - frame - 5) \
        | ((xx == frame + 6) | (xx == W - frame - 5)) & (yy >= frame + 5) & (yy < H - frame - 5)
    wood[gl] = GOLD
    wood[gl2] = (122, 96, 48)
    # vignette
    v = 1 - 0.35 * (((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2) / 2
    wood *= v[..., None]
    img = Image.fromarray(np.clip(wood, 0, 255).astype(np.uint8)).convert("RGBA")
    d = ImageDraw.Draw(img, "RGBA")
    # bevel on the frame
    d.rectangle((0, 0, W - 1, H - 1), outline=(18, 11, 6, 255), width=3)
    d.rectangle((frame - 2, frame - 2, W - frame + 1, H - frame + 1), outline=(18, 11, 6, 255), width=2)
    # brass corners with a rivet
    c = 34
    for cx, cy in ((0, 0), (W - c, 0), (0, H - c), (W - c, H - c)):
        d.rectangle((cx, cy, cx + c - 1, cy + c - 1), fill=(120, 88, 38, 255))
        d.rectangle((cx + 3, cy + 3, cx + c - 4, cy + c - 4), fill=GOLD + (255,))
        d.rectangle((cx + 3, cy + 3, cx + c - 4, cy + 6), fill=(232, 205, 140, 255))
        d.ellipse((cx + c / 2 - 5, cy + c / 2 - 5, cx + c / 2 + 5, cy + c / 2 + 5), fill=(96, 70, 30, 255))
        d.ellipse((cx + c / 2 - 3, cy + c / 2 - 4, cx + c / 2 + 1, cy + c / 2), fill=(240, 214, 150, 255))
    sheet.paste(img, (x0, y0))
    return sheet


def ornament(src):
    a = np.asarray(src.convert("RGBA")).astype(np.float32)
    lum = a[..., :3].mean(axis=2) / 255
    out = np.zeros_like(a)
    for i, (dark, light) in enumerate(((96, 232), (68, 205), (30, 140))):
        out[..., i] = dark + (light - dark) * lum
    out[..., 3] = a[..., 3]
    return Image.fromarray(out.astype(np.uint8))


def main(jar, root):
    out = os.path.join(root, "src/main/resources/skin")
    tex = os.path.join(out, "assets/mebahelrpgrquest/textures")
    os.makedirs(os.path.join(tex, "gui"), exist_ok=True)
    os.makedirs(os.path.join(tex, "item"), exist_ok=True)
    z = zipfile.ZipFile(jar)
    def load(p):
        return Image.open(io.BytesIO(z.read("assets/mebahelrpgrquest/textures/" + p))).convert("RGBA")
    for name in ("quest_board_gui_padded", "quest_board_gui"):
        board(load(f"gui/{name}.png")).save(os.path.join(tex, f"gui/{name}.png"))
    ornament(load("gui/dialogue_option_ornament.png")).save(os.path.join(tex, "gui/dialogue_option_ornament.png"))
    n = 0
    for p in sorted(z.namelist()):
        if p.startswith("assets/mebahelrpgrquest/textures/item/") and "note" in p and p.endswith(".png"):
            name = p.rsplit("/", 1)[1]
            kind = "escort" if name.startswith("escort") else "elite" if name.startswith("elite") else "quest"
            img = note(load("item/" + name), sum(map(ord, name)), name.endswith("_clean.png"), kind)
            img.save(os.path.join(tex, "item", name))
            n += 1
    with open(os.path.join(out, "pack.mcmeta"), "w") as f:
        f.write('{\n  "pack": {\n    "pack_format": 34,\n    "description": "Half a God: paper and ink for Mebahel\'s quest board"\n  }\n}\n')
    print("skin: board, ornament and", n, "notes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
