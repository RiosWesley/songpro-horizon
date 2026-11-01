"""Patch the factory WarinLightLayout.qml for Horizon. Used by the carrier builder and the mock.

Two marked, reversible edits; every telltale keeps its own native bindings:
1. navigationFlag is always true: the telltales take the arrangement BYD drew for full-screen
   navigation (side columns, top corners, bottom-left row), which leaves the middle free.
2. The camera speed-limit sign telltale is faded out: Horizon draws the limit beside the speed.

    python patch_lamps.py FACTORY_WarinLightLayout.qml OUT.qml
"""
import re
import sys

NAV_FLAG = re.compile(r"property bool navigationFlag:\s*\(DataSource\.dilinkName===0x02&&DataSource\.naviType===0x04\)\?true:false")
TSR_STATE = "displayState:DataSource.limitTrafficSignRecognition"


def patch(source):
    patched, count = NAV_FLAG.subn("property bool navigationFlag: true // HORIZON: edge telltale layout", source)
    if count != 1:
        raise SystemExit(f"navigationFlag anchor found {count} times, expected 1")
    if patched.count(TSR_STATE) != 1:
        raise SystemExit(f"speed-limit telltale anchor found {patched.count(TSR_STATE)} times, expected 1")
    patched = patched.replace(TSR_STATE, TSR_STATE + "\n        opacity: 0 // HORIZON: limit drawn by Horizon")
    return patched


def main():
    src, out = sys.argv[1], sys.argv[2]
    with open(src, encoding="utf-8-sig") as f:
        text = f.read()
    with open(out, "w", encoding="utf-8") as f:
        f.write(patch(text))
    print("patched", out)


if __name__ == "__main__":
    main()
