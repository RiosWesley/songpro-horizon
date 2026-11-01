"""Mock now-playing feeds for the renders: an abstract cover and now_playing.json variants.
The song, artist and lines are invented for the mock."""
import json, math, os, sys
from PIL import Image, ImageDraw, ImageFilter

root = sys.argv[1]
size = 512
img = Image.new("RGB", (size, size))
px = img.load()
for y in range(size):
    for x in range(size):
        t = (x + y) / (2 * size)
        r = int(255 * (0.95 - 0.55 * t)); g = int(60 + 90 * t); b = int(120 + 120 * t)
        px[x, y] = (r, g, b)
d = ImageDraw.Draw(img)
for i in range(7):
    rad = 60 + i * 38
    d.ellipse((size * 0.62 - rad, size * 0.70 - rad, size * 0.62 + rad, size * 0.70 + rad), outline=(255, 230, 200), width=3)
d.rectangle((0, int(size * 0.72), size, size), fill=(18, 14, 40))
img = img.filter(ImageFilter.GaussianBlur(1.2))

base = {"seq": 41, "title": "Neon Horizon", "artist": "Aurora Drive", "playing": True,
        "positionMs": 83000, "durationMs": 214000, "art": "art_mock.png"}
variants = {
    "lyrics": dict(base, lyrics={"prev": "Seguindo as luzes da estrada",
                                 "current": "o céu acende em azul e a cidade fica pra trás",
                                 "next": "e o motor canta baixinho"}),
    "plain": dict(base, lyrics=None),
}
for name, data in variants.items():
    out = os.path.join(root, name)
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, "art_mock.png"))
    with open(os.path.join(out, "now_playing.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
print("ok")
