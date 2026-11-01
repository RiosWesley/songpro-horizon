"""Validation sheet: the physical-size (1280x480) mocks with captions, on one page."""
import sys
from PIL import Image, ImageDraw, ImageFont

out_dir, sheet = sys.argv[1], sys.argv[2]
items = [
    ("night_instruments", "Noite — instrumentos: ADAS 3D de fábrica, música com letra sincronizada, energia"),
    ("night_map", "Noite — mapa: Waze do celular no quadro central, ADAS 3D na janelinha, letra no card"),
    ("day_instruments", "Dia — instrumentos (regenerando)"),
    ("day_map", "Dia — mapa (o Waze segue o tema dia/noite do celular)"),
    ("night_no_lyrics", "Letra desligada: capa grande e progresso da música"),
    ("night_trip", "Setas esquerda/direita do volante: card de viagem"),
]
font = ImageFont.truetype("/usr/share/fonts/noto/NotoSans-Regular.ttf", 22)
title_font = ImageFont.truetype("/usr/share/fonts/noto/NotoSans-Medium.ttf", 30)
pad, cap = 36, 44
w = 2 * 1280 + 3 * pad
h = 90 + 3 * (480 + cap + pad)
page = Image.new("RGB", (w, h), (12, 15, 20))
d = ImageDraw.Draw(page)
d.text((pad, 28), "Horizon — cluster 8,8\" do Song Pro (mocks em tamanho físico, 1280×480)", fill=(230, 238, 246), font=title_font)
for i, (name, caption) in enumerate(items):
    x = pad + (i % 2) * (1280 + pad)
    y = 90 + (i // 2) * (480 + cap + pad)
    page.paste(Image.open(f"{out_dir}/fisico/{name}.png").convert("RGB"), (x, y + cap))
    d.text((x, y + 6), caption, fill=(150, 170, 190), font=font)
page.save(sheet)
print("ok", sheet, page.size)
