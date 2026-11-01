"""Build the mock projection frame: the real Waze cluster capture, placed in the map hole.

    python make_projection.py CAPTURE.png OUT.png X Y W H
The capture is the display-1 grab with the 640x384 map at (320,48); X Y W H is the hole in
physical pixels (HorizonGeometry.physical(map.hole)).
"""
import sys
from PIL import Image

src, out_path = sys.argv[1], sys.argv[2]
x, y, w, h = (int(v) for v in sys.argv[3:7])
frame = Image.open(src).convert("RGB").crop((320, 48, 960, 432))
fw, fh = frame.size
th = round(fw * h / w)
frame = frame.crop((0, (fh - th) // 2, fw, (fh - th) // 2 + th)).resize((w, h), Image.LANCZOS)
out = Image.new("RGB", (1280, 480), "black")
out.paste(frame, (x, y))
out.save(out_path)
print("ok", out_path)
