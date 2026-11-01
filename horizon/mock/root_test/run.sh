#!/bin/sh
# usage: run.sh CARRIER OUT.png SCENARIO [extra render_root args]   (needs STOCK2, QMLDIR, PYQT in env)
HERE=$(cd "$(dirname "$0")" && pwd)
c=$1; o=$2; s=$3; shift 3
timeout 200 "${PYQT:-python3}" -I "$HERE/render_root.py" "$c" "$STOCK2" "$HERE/../scenarios/$s.json" "$o" --qml-dir "$QMLDIR" "$@" 2>&1 | grep -v "^$" | grep -E "horizon|probe|saved|Error|unknown|ReferenceError|TypeError" | cut -c1-220 | sort | uniq -c
