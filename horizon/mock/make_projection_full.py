"""Build a mock projection from the windowed one (projection.png): its map crop scaled to cover a
rectangle of the 1280x480 display (default: all of it). A preview of the bleed and full-screen
layouts only: the phone's cards come out a little larger than the phone would draw them.

    python make_projection_full.py assets/projection.png assets/projection_full.png
    python make_projection_full.py assets/projection.png assets/projection_window.png 390 68 500 344
"""
import sys
from PIL import Image

src, out_path = sys.argv[1], sys.argv[2]
x, y, w, h = (int(v) for v in sys.argv[3:7]) if len(sys.argv) > 3 else (0, 0, 1280, 480)
window = Image.open(src).convert("RGB").crop((390, 100, 890, 392))
scale = max(w / window.width, h / window.height)
big = window.resize((round(window.width * scale), round(window.height * scale)), Image.LANCZOS)
left, top = (big.width - w) // 2, (big.height - h) // 2
out = Image.new("RGB", (1280, 480), "black")
out.paste(big.crop((left, top, left + w, top + h)), (x, y))
out.save(out_path)
print("ok", out_path)
