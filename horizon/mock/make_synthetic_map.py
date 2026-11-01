"""Draw the mock projection: a made-up street grid in the phone cluster map's style (dark blocks,
lit streets, a route that turns left and the car arrow), seen in perspective, with no names. The
mock used to carry real captures from the car; this keeps the previews free of real places.

    python make_synthetic_map.py assets/projection_full.png
    python make_projection_full.py ...   (not needed: projection.png and projection_window.png are
                                          written here too, with the same geometry as before)
"""
import os
import sys
from PIL import Image, ImageDraw

SCALE = 2  # drawn at twice the size, then reduced: smooth lines without an anti-aliasing pass
W, H = 1280 * SCALE, 480 * SCALE
BLOCK, STREET, EDGE = (37, 45, 56), (61, 77, 96), (78, 96, 118)
ROUTE, ARROW, HALO = (43, 196, 232), (64, 206, 226), (52, 74, 90)

# A camera above the ground, looking ahead and down: world (x across, z ahead) -> screen.
F, HORIZON, Z0, CX = 520 * SCALE, -70 * SCALE, 1.1, 640 * SCALE


def screen(x, z):
    d = z + Z0
    return CX + x * F / d, HORIZON + 0.9 * F / d


def street(draw, a, b, width, colour):
    # Short segments, so the width follows the depth.
    steps = 24
    for i in range(steps):
        t0, t1 = i / steps, (i + 1) / steps
        x0, z0 = a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0
        x1, z1 = a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1
        w = max(1, int(width * SCALE / ((z0 + z1) / 2 + Z0)))
        draw.line([screen(x0, z0), screen(x1, z1)], fill=colour, width=w)


def main(out_full):
    img = Image.new("RGB", (W, H), BLOCK)
    draw = ImageDraw.Draw(img)
    far = 14.0
    # Streets running ahead, a little irregular, and cross streets at uneven spacing.
    for x in (-5.2, -3.9, -2.7, -1.35, 0.0, 1.25, 2.6, 3.8, 5.3):
        street(draw, (x, -0.6), (x * 1.04, far), 30 if x == 0.0 else 18, STREET)
    for z in (-0.35, 0.55, 1.6, 2.4, 3.5, 4.4, 5.6, 7.0, 8.8, 11.0):
        street(draw, (-7.0, z), (7.0, z), 18, STREET)
    # A diagonal avenue for some variety.
    street(draw, (1.25, 4.4), (6.5, 11.5), 22, EDGE)
    # The route: ahead on the main street, then left.
    street(draw, (0.0, -0.05), (0.0, 3.5), 34, ROUTE)
    street(draw, (0.0, 3.5), (-2.7, 3.5), 34, ROUTE)

    # The car arrow on a soft disc, low in the frame like the phone draws it.
    ax, ay = 640 * SCALE, 400 * SCALE
    r = 46 * SCALE
    draw.ellipse([ax - r, ay - r * 0.55, ax + r, ay + r * 0.55], fill=HALO)
    s = 26 * SCALE
    arrow = [(ax, ay - s), (ax + s, ay + s * 0.55), (ax, ay + s * 0.15), (ax - s, ay + s * 0.55)]
    draw.polygon(arrow, fill=ARROW, outline=(235, 250, 255))

    full = img.resize((1280, 480), Image.LANCZOS)
    full.save(out_full)
    here = os.path.dirname(out_full)

    # The window frame the older scenarios use: the map in the centre hole (390,100,500,292).
    window = full.crop((390, 100, 890, 392))
    frame = Image.new("RGB", (1280, 480), "black")
    frame.paste(window, (390, 100))
    frame.save(os.path.join(here, "projection.png"))
    # And that window scaled to cover the taller hole (390,68,500,344).
    scale = max(500 / window.width, 344 / window.height)
    big = window.resize((round(window.width * scale), round(window.height * scale)), Image.LANCZOS)
    left, top = (big.width - 500) // 2, (big.height - 344) // 2
    tall = Image.new("RGB", (1280, 480), "black")
    tall.paste(big.crop((left, top, left + 500, top + 344)), (390, 68))
    tall.save(os.path.join(here, "projection_window.png"))
    print("ok", out_full)


if __name__ == "__main__":
    main(sys.argv[1])
