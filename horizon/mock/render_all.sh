#!/bin/sh
# Render every Horizon mock scenario to ../out (1920x720) and a 1280x480 physical-size copy.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
PY=${PY:-python3}
RCC=${CLUSTER_RCC:?set CLUSTER_RCC to the car cluster_theme2.rcc}
mkdir -p "$HERE/../out/fisico"
for s in night_instruments night_map day_instruments day_map night_trip night_no_lyrics; do
    "$PY" -I "$HERE/render.py" "$HERE/scenes/mock_cluster.qml" "$HERE/scenarios/$s.json" "$HERE/../out/$s.png" --rcc "$RCC" --wait-ms 3500 2>/dev/null | grep saved
    "$PY" -I -c "import sys;from PIL import Image;Image.open(sys.argv[1]).resize((1280,480),Image.LANCZOS).save(sys.argv[2])" "$HERE/../out/$s.png" "$HERE/../out/fisico/$s.png"
done
