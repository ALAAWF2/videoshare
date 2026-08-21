import os

from PIL import Image, ImageDraw

BASE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
C1, C2 = (255, 138, 61), (194, 46, 14)


def gradient(size):
    img = Image.new("RGBA", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(1, size - 1)
        c = tuple(int(a + (b - a) * t) for a, b in zip(C1, C2))
        d.line([(0, y), (size, y)], fill=c + (255,))
    return img


def make(size, round_icon):
    img = gradient(size)
    if round_icon:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
        img.putalpha(mask)
    d = ImageDraw.Draw(img)
    cx, cy = size * 0.55, size * 0.50
    r = size * 0.16
    d.polygon(
        [(cx + r * 0.95, cy), (cx - r * 0.55, cy - r), (cx - r * 0.55, cy + r)],
        fill=(255, 255, 255, 255),
    )
    return img


for density, size in SIZES.items():
    outdir = os.path.join(BASE, "mipmap-" + density)
    os.makedirs(outdir, exist_ok=True)
    make(size, False).save(os.path.join(outdir, "ic_launcher.png"))
    make(size, True).save(os.path.join(outdir, "ic_launcher_round.png"))
    print(density + ": " + str(size) + "px ok")
