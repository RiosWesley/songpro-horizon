#!/usr/bin/env python3
"""OFFLINE builder of the Horizon carrier for the BYD Song Pro 8.8" cluster (firmware SX309).

Reads the car's own stock resources (copied off the car beforehand), refuses unless every input
hash matches the SX309 cohort, and writes a translate-RCC carrier that contains:

  * the 80 stock commonTranslate entries, byte for byte (stored bytes, metadata, order of keys);
  * qml10_25/cluster.qml  (factory root + marked HORIZON_BEGIN/END insertions, fail-open);
  * qml10_25/WarinLightLayout.qml (factory + marked insertions, active only while Horizon is);
  * qml10_25/Center.qml   (factory + marked insertions: drive info and 2D ADAS yield to Horizon);
  * qml10_25/adas3d/components/Model3D.qml (factory, its 6 target Loader3D made synchronous);
  * qml10_25/adas3d/Adas3dContainer.qml (factory + marked insertions: the car stays centred in its
    lane unless the camera gives its real position, neighbour lanes beyond dashed lines, targets the
    camera places but does not classify (ACC off) drawn as cars, no popup
    background behind the map-mode window, the road surface in every ADAS mode);
  * qml/cluster.qml       (theme 1 root, byte-identical to factory; the 8.8" never uses it);
  * horizon/*             (the Horizon QML/JS files, loaded as qrc:/horizon/...).

Nothing here talks to the car. The output is named OFFLINE_ONLY_*: it is NOT a selectable carrier
(the DashCast/OEM name pattern is Di3F_DynastyOceanTranslate_V0001_Translate<name>.rcc).

    build_horizon_sx309_88.py --stock DIR --horizon DIR --out DIR [--speed-fix] [--check]

DIR for --stock holds cluster_theme1.rcc, cluster_theme2.rcc and commonTranslate.rcc.
"""
import argparse
import difflib
import hashlib
import json
import mmap
import os
import re
import struct
import sys
import zlib
from pathlib import Path

# DashCast's RCC codec (MIT), vendored unchanged; see third_party/dashcast/LICENSE.
DEFAULT_CODEC = str(Path(__file__).resolve().parent / "third_party" / "dashcast")

# ---- SX309 / Song Pro 8.8" cohort (hashes measured on the car, 2026-10-31) ------------------------
STOCK_FILES = {
    "cluster_theme1.rcc": "7ff2bde70effb76ce06b701c161d7600cd8bd4918c7705a58e30507de67ab009",
    "cluster_theme2.rcc": "64cda4218526d815036f5f8dce95e6152033e653e58ce061efaf037265bf1795",
    "commonTranslate.rcc": "b41bd89a9e3a881c5514992af7033a062cb6207355f809384f0469205d27fcc3",
}
ROOT1 = ("cluster_theme1.rcc", "qml/cluster.qml", "f74082cafeed18274942e74dd2890909b24085d0fc6e2fa8113d6069fd595df2")
ROOT2 = ("cluster_theme2.rcc", "qml10_25/cluster.qml", "046749d4743eb04de4d9454f548ba970c487610b0c25bbe56c9baa2238750fd8")
LAMPS = ("cluster_theme2.rcc", "qml10_25/WarinLightLayout.qml", "4c3651a6202deeedd8da80c03efd7f17c34ccbb063c0f88edee4545848247092")
MODEL3D = ("cluster_theme2.rcc", "qml10_25/adas3d/components/Model3D.qml",
           "d8be8a34e500e61b77220da17253674f8f5831fa52a0e40375e0b8a9433367fe")
ADAS3D = ("cluster_theme2.rcc", "qml10_25/adas3d/Adas3dContainer.qml",
          "9f4af828aff95dfd887fb087105f3acf2ff8f409385a42a2a6203051df5efd29")
CENTER = ("cluster_theme2.rcc", "qml10_25/Center.qml", "b284c991e474a8b2a0369a4151d15b2ee5ecfeaa19fa01a5f617b4eb1293b4d1")
LOCALES = 80

BEGIN = b"// HORIZON_BEGIN\r\n"
END = b"// HORIZON_END\r\n"
MAX_CARRIER = 4 * 1024 * 1024  # DashCast's recovery bound for a translate carrier


def sha(b):
    return hashlib.sha256(b).hexdigest()


def import_codec(path):
    sys.path.insert(0, path)
    import build_live_marker as codec  # DashCast's RCC format-3 reader/writer
    return codec


# ---- reading entries out of the big stock RCCs without decompressing everything -------------------
def rcc_entry(path, alias):
    with open(path, "rb") as f, mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ) as b:
        assert b[:4] == b"qres"
        version, tree, data, names = struct.unpack_from(">4I", b, 4)
        assert version == 3
        found = []

        def walk(i, parts):
            o = tree + 22 * i
            name_off, flags = struct.unpack_from(">IH", b, o)
            n = struct.unpack_from(">H", b, names + name_off)[0]
            name = b[names + name_off + 6:names + name_off + 6 + 2 * n].decode("utf-16be") if i else ""
            p = parts + ([name] if name else [])
            if flags & 2:
                count, first = struct.unpack_from(">II", b, o + 6)
                for j in range(first, first + count):
                    walk(j, p)
            elif "/".join(p) == alias:
                _, _, off = struct.unpack_from(">HHI", b, o + 6)
                size = struct.unpack_from(">I", b, data + off)[0]
                raw = bytes(b[data + off + 4:data + off + 4 + size])
                found.append(zlib.decompress(raw[4:]) if flags & 1 else raw)

        walk(0, [])
        if len(found) != 1:
            raise SystemExit(f"{alias}: expected exactly one entry in {path}, found {len(found)}")
        return found[0]


# ---- patches (bytes in, bytes out, CRLF + BOM preserved) ------------------------------------------
def block(text, indent):
    """A marked insertion. `text` lines are LF separated, written CRLF like the factory source."""
    body = b"".join(indent + line.encode() + b"\r\n" for line in text.strip("\n").split("\n"))
    return indent + BEGIN + body + indent + END


def insert_after(src, anchor, text, indent):
    if src.count(anchor) != 1:
        raise SystemExit(f"anchor is not unique ({src.count(anchor)}x): {anchor!r}")
    return src.replace(anchor, anchor + block(text, indent), 1)


def insert_before(src, anchor, text, indent):
    if src.count(anchor) != 1:
        raise SystemExit(f"anchor is not unique ({src.count(anchor)}x): {anchor!r}")
    return src.replace(anchor, block(text, indent) + anchor, 1)


def strip_markers(src):
    return re.sub(rb"[ ]*// HORIZON_BEGIN\r\n.*?[ ]*// HORIZON_END\r\n", b"", src, flags=re.S)


ROOT_PROPS = """\
// Horizon runs only while the factory instruments would: powered on, power-on animation done,
// the always-displayed layer and the telltale layer visible, on the 8.8" panel (screenSize 1).
readonly property bool horizonWanted: DataSource.screenSize === 0x01 && rootItemId.width === 1920 && rootItemId.height === 720 && DataSource.powerGear === 0x03 && DataSource.playAnim === true && alwyasDisplayId.visible && warningLayoutId.visible
// Fail-open: every factory item Horizon replaces keeps its stock look unless this is true.
readonly property bool horizonActive: rootItemId.horizonWanted && horizonLoader.status === Loader.Ready && horizonLoader.item !== null
// The native 2D ADAS window yields only if Horizon's own 3D road is up (adasReady if Horizon
// exposes it, else the absence of adasFallback).
readonly property bool horizonAdas3d: rootItemId.horizonActive && (horizonLoader.item.adasReady !== undefined ? horizonLoader.item.adasReady === true : horizonLoader.item.adasFallback === false)
// Without the 3D road the native compact 2D ADAS window stays, but in Horizon's ADAS place (the
// centre, or the left window in map mode) instead of its corner over the energy card.
readonly property bool horizonAdas2d: rootItemId.horizonActive && !rootItemId.horizonAdas3d
readonly property bool horizonMap: DataSource.naviType === 0x04 || DataSource.naviType === 0x03"""

# The compact window is 189x120 at (1323, 446): centre (1417.5, 506). Horizon's ADAS place is the
# stage centre (960, 390) or the map-mode left window centre (336.5, 286); the scale fills it and
# the layer is rendered at the scaled size so the road stays sharp.
ADAS2D_PLACE = """\
opacity: rootItemId.horizonAdas3d ? 0 : 1
scale: rootItemId.horizonAdas2d ? (rootItemId.horizonMap ? 2.1 : 2.4) : 1
layer.textureSize: rootItemId.horizonAdas2d ? Qt.size(189 * scale, 120 * scale) : Qt.size(0, 0)
transform: Translate {
    x: rootItemId.horizonAdas2d ? (rootItemId.horizonMap ? 336.5 : 960) - 1417.5 : 0
    y: rootItemId.horizonAdas2d ? (rootItemId.horizonMap ? 286 : 390) - 506 : 0
}"""

ROOT_LOADER = """\
// z: above the factory always-displayed layer (0) and the Android surface (-3), below the centre
// container (3: menu, drive info, warning pop-ups), the ADAS text tips (111) and every telltale (100).
Loader {
    id: horizonLoader
    z: 1
    active: rootItemId.horizonWanted
    asynchronous: true
    source: "qrc:/horizon/HorizonCluster.qml"
    visible: rootItemId.horizonActive
}"""

SPEED_FIX = """\
x: 1504
y: 588
width: 267
height: 75"""


def patch_root(src, speed_fix):
    out = src
    out = insert_after(out, b"    id:rootItemId\r\n", ROOT_PROPS, b"    ")
    # factory layer Horizon draws over: background, header (gear/mode/clock/temp), speed, power, SOC/fuel/ODO band
    out = insert_after(out, b"        id: bgImageContainerId\r\n", "opacity: rootItemId.horizonActive ? 0 : 1", b"        ")
    out = insert_after(out, b"        id:alwyasDisplayId\r\n", "opacity: rootItemId.horizonActive ? 0 : 1", b"        ")
    # factory ADAS 2D: the compact window's corner frame never shows under Horizon; its road yields
    # to the 3D one, or moves into Horizon's ADAS place when there is no 3D
    out = insert_after(out, b"        id: adasWindowBackground\r\n", "opacity: rootItemId.horizonActive ? 0 : 1", b"        ")
    out = insert_after(out, b"        id: adas2dNaviWindowBg\r\n", "opacity: rootItemId.horizonAdas3d ? 0 : 1", b"        ")
    out = insert_after(out, b"        id:adasContainerParentId\r\n", ADAS2D_PLACE, b"        ")
    # tell the (patched) telltale layer that Horizon is up
    out = insert_after(out, b"        id:warningLayoutId\r\n", "horizonActive: rootItemId.horizonActive", b"        ")
    # and the centre container (drive info, 2D ADAS), see patch_center
    out = insert_after(out, b"        id:centerContainerId\r\n",
                       "horizonActive: rootItemId.horizonActive\nhorizonAdas3d: rootItemId.horizonAdas3d", b"        ")
    out = insert_before(out, b"    Loader{\r\n        id:presentationLoaderId\r\n", ROOT_LOADER, b"    ")
    if speed_fix:
        out = insert_after(out, b"            id: naviSpeedId\r\n", SPEED_FIX, b"            ")
    return out


NAV_ORIG = (b"    property bool navigationFlag: (DataSource.dilinkName===0x02&&DataSource.naviType===0x04)"
            b"?true:false\r\n")
NAV_NEW = (b"    property bool navigationFlag: ((DataSource.dilinkName===0x02&&DataSource.naviType===0x04)?true:false)"
           b" || horizonLamps.horizonActive // HORIZON: edge telltale layout while Horizon is active\r\n")
TSR_ANCHOR = b"        displayState:DataSource.limitTrafficSignRecognition\r\n"
LAMP_ROOT_ANCHOR = b"import com.byd.WarningLight 1.0\r\n\r\nItem {\r\n"


def patch_lamps(src):
    out = insert_after(src, LAMP_ROOT_ANCHOR, "id: horizonLamps\nproperty bool horizonActive: false", b"    ")
    if out.count(NAV_ORIG) != 1:
        raise SystemExit("navigationFlag anchor is not unique")
    out = out.replace(NAV_ORIG, NAV_NEW, 1)
    # Horizon draws the speed limit itself: hide the native camera/TSR plate only while it does.
    out = insert_after(out, TSR_ANCHOR, "opacity: horizonLamps.horizonActive ? 0 : 1", b"        ")
    return out


CENTER_PROPS = """\
// Set by the patched root. Horizon draws energy and trip itself, so while it is up the drive-info
// pages (energy flow included) yield; while its 3D road is up the large 2D ADAS pane yields too.
// Menu, warnings, charging and the rest of the centre are untouched.
property bool horizonActive: false
property bool horizonAdas3d: false
onHorizonActiveChanged: driveInfoUIUpdate()
onHorizonAdas3dChanged: updateAdas()"""

DRIVEINFO_GUARD = """\
if(centerContainerId.horizonActive){
    if(centerContainerId.driveInfoUi){
        centerContainerId.driveInfoUi.destroy()
        centerContainerId.driveInfoUi = null
    }
    energyFlow.requestToDisplay = false
    return
}"""

ADAS_GUARD = """\
if(centerContainerId.horizonAdas3d){
    if(centerContainerId.adasUi != null){
        centerContainerId.adasUi.destroy()
        centerContainerId.adasUi = null
        centerContainerId.preAdasUiState = 0x00
    }
    return
}"""


def patch_center(src):
    out = insert_after(src, b"    id:centerContainerId\r\n", CENTER_PROPS, b"    ")
    out = insert_after(out, b"    function driveInfoUIUpdate(){\r\n", DRIVEINFO_GUARD, b"        ")
    return insert_after(out, b"    function updateAdas(){\r\n", ADAS_GUARD, b"        ")


# The 3D scene swaps each target slot's Loader3D source as targets come and go. Asynchronous
# loading there lets a swap land mid-incubation, which crashes Quick3D 5.15 and restarts the
# cluster (seen on the car); synchronous loading creates the model inside the swap itself.
ASYNC_OLD = "asynchronous: true //\u5f02\u6b65\u52a0\u8f7d".encode()
ASYNC_NEW = "asynchronous: false // HORIZON: was true (\u5f02\u6b65\u52a0\u8f7d)".encode()


def patch_model3d(src):
    if src.count(ASYNC_OLD) != 6:
        raise SystemExit("Model3D.qml: expected 6 asynchronous target loaders")
    return src.replace(ASYNC_OLD, ASYNC_NEW)


def unpatch_model3d(src):
    return src.replace(ASYNC_NEW, ASYNC_OLD)


# The 3D road puts the lane lines at -carToLeftLaneDistance*0.96 and +96 (one lane = 96; both
# factory UIs read ~50 as "centred"). With no lane found that distance is 0 and a line ran through
# the car, so a missing or implausible distance keeps the car centred (the factory 2D's rule since
# V2.3); a real one is followed, so a lane change shows the car crossing the line. The camera only
# reports the ego lane's two lines, but a dashed one means there is a lane beyond it: that
# neighbour lane is drawn too (the factory only shows it during the ICC lane-change animation).
HORIZON_LANES = """\
function horizonLanes(){
    if(DataSource.laneChangeAnimationDirection !== 0){
        return
    }
    var d = DataSource.carToLeftLaneDistance
    var left = (d > 0 && d < 100) ? -d * 0.96 : -48
    adas3dId.middleLaneLeftX = left
    adas3dId.middleLaneRightX = left + 96
    adas3dId.leftLeftLanePositionX = left - 91
    adas3dId.rightRightLanePositionX = left + 96 + 91
    adas3dId.leftLeftLaneSolid = true
    adas3dId.rightRightLaneSolid = true
    adas3dId.leftLeftLaneOpacity = DataSource.leftLineType === 0x1 ? 0.6 : 0
    adas3dId.rightRightLaneOpacity = DataSource.rightLineType === 0x1 ? 0.6 : 0
}"""
LANES_NOW = "horizonLanes()\nreturn"
LANES_AFTER = "Qt.callLater(horizonLanes)"

# Only Horizon instantiates this container (the factory centre has it commented out), so these
# changes reach nothing else. The popup background behind the map-mode window (light grey when the
# cluster is not in night mode) gives way to Horizon's own soft window; the road surface shows in
# every ADAS mode, not only in the large one that open-headunit's ADAS restore switches to.
NO_POPUP_BG = "opacity: 0"

# Seen on the car (diagnostic build, 2026-10-31): with ACC/ICC off the camera still reports where
# the target ahead is (frontTargetOnePosX/Y, metres) but not what it is (state 0), and the factory
# draws nothing for state 0. Such a target is drawn as a car, placed exactly as the factory places
# one. Its model is created once and then only shown or hidden, so flickering detections never
# reload it (see the synchronous loaders in Model3D).
TARGET_SLOTS = [(0, "LeftFrontTarget", "leftFrontTarget"), (1, "RightFrontTarget", "rightFrontTarget"),
                (2, "LeftRearTarget", "leftRearTarget"), (3, "RightRearTarget", "rightRearTarget"),
                (4, "FrontTargetOne", "frontTargetOne"), (5, "FrontTargetTwo", "frontTargetTwo")]
GHOST_STATE = """\
// Untyped targets: when each one's position last changed (see horizonGhost).
property var horizonSeen: ({})
Timer {
    interval: 250
    running: true
    repeat: true
    onTriggered: {
        var slots = ["leftFrontTarget", "rightFrontTarget", "leftRearTarget",
                     "rightRearTarget", "frontTargetOne", "frontTargetTwo"]
        for (var n = 0; n < slots.length; ++n) {
            if (DataSource[slots[n] + "State"] === 0) {
                horizonGhost(n, slots[n])
            }
        }
    }
}"""
# A lost target is not cleared by the camera, it keeps its last position (on the road: "st 0 x 0
# y 70" for a minute at a red light), while a live track moves every frame. So an untyped target
# is only drawn while its position keeps changing.
HORIZON_GHOST = """\
function horizonGhost(n, slot){
    if(DataSource.adasShowMode === 1){
        return
    }
    var x = DataSource[slot + "PosX"]
    var y = DataSource[slot + "PosY"]
    var key = "adasModel_0" + n + "_"
    var now = Date.now()
    var last = horizonSeen[slot]
    if(!last || last.x !== x || last.y !== y){
        last = { x: x, y: y, t: now }
        horizonSeen[slot] = last
    }
    var overSelf = Math.abs(x) < 1.4 && y <= 0
    var seen = (Math.abs(y) > 0.5 || Math.abs(x) > 0.5) && !overSelf && now - last.t < 1500
    if(seen && adas3dId[key + "source"] !== getTargetSource(1)){
        adas3dId[key + "source"] = getTargetSource(1)
    }
    adas3dId[key + "visible"] = seen
    if(seen){
        adas3dId[key + "pos_x"] = setTargetX(-x * roadWidthPer, 1)
        adas3dId[key + "pos_z"] = setTargetY(y, 1)
    }
}"""
ROAD_ALWAYS = ("adas3dId.adasModel_roadBgVisible = Qt.binding(function () {\n"
               "    return DataSource.naviType !== 4 && DataSource.naviType !== 3\n"
               "})")
ROAD_ANCHOR = (b"        adas3dId.adasModel_roadBgVisible = DataSource.adasInterfaceDisplay === 0x1"
               b" && DataSource.naviType !== 4? true : false\r\n")


def patch_adas3d(src):
    out = insert_before(src, b"    function updateSelfCarToLeftLinePosition(){\r\n", HORIZON_LANES, b"    ")
    out = insert_after(out, b"    function updateSelfCarToLeftLinePosition(){\r\n", LANES_NOW, b"        ")
    for anchor in (b"    function updateLeftLineType(){\r\n", b"    function updateRightLineType(){\r\n"):
        out = insert_after(out, anchor, LANES_AFTER, b"        ")
    out = insert_after(out, b"        id: bgId\r\n", NO_POPUP_BG, b"        ")
    out = insert_before(out, b"    function setTargetX(temp,type)\r\n", HORIZON_GHOST, b"    ")
    out = insert_after(out, b"    id:adas3dContainerRootId\r\n", GHOST_STATE, b"    ")
    for n, name, prop in TARGET_SLOTS:
        guard = "if(DataSource.%sState === 0){\n    horizonGhost(%d, \"%s\")\n    return\n}" % (prop, n, prop)
        for part in ("Type", "PosX", "PosY"):
            out = insert_after(out, b"    function update" + name.encode() + part.encode() + b"(){\r\n", guard, b"        ")
    return insert_after(out, ROAD_ANCHOR, ROAD_ALWAYS, b"        ")


def unpatch_lamps(src):
    return strip_markers(src).replace(NAV_NEW, NAV_ORIG, 1)


def unpatch_root(src):
    return strip_markers(src)


# ---- build ------------------------------------------------------------------------------------------
def horizon_files(directory):
    files = {}
    for p in sorted(Path(directory).iterdir()):
        if p.is_dir() or p.name.startswith("."):
            continue
        if p.suffix not in (".qml", ".js"):
            raise SystemExit(f"unexpected file in the Horizon directory (only .qml/.js): {p.name}")
        files["horizon/" + p.name] = p.read_bytes()
    for required in ("horizon/HorizonCluster.qml", "horizon/HorizonGeometry.js"):
        if required not in files:
            raise SystemExit("missing " + required)
    return files


def diff(a, b, name):
    return "".join(difflib.unified_diff(a.decode("utf-8-sig").splitlines(True), b.decode("utf-8-sig").splitlines(True),
                                        "original/" + name, "patched/" + name))


def build(args):
    codec = import_codec(args.codec_dir)
    stock = Path(args.stock)
    for name, expected in STOCK_FILES.items():
        digest = hashlib.sha256()
        with open(stock / name, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                digest.update(chunk)
        if digest.hexdigest() != expected:
            raise SystemExit(f"{name}: SHA-256 {digest.hexdigest()} is not the SX309 cohort ({expected}); refusing")

    roots = {}
    for rcc_name, alias, expected in (ROOT1, ROOT2, LAMPS, CENTER, MODEL3D, ADAS3D):
        data = rcc_entry(stock / rcc_name, alias)
        if sha(data) != expected:
            raise SystemExit(f"{alias}: unexpected factory source {sha(data)}; refusing")
        roots[alias] = data

    translate = (stock / "commonTranslate.rcc").read_bytes()
    rows = codec.decode(translate)
    if len(rows) != LOCALES or any(not r[0].startswith("common/language/") for r in rows):
        raise SystemExit("unexpected commonTranslate inventory; refusing")
    original_rows = list(rows)

    root2 = patch_root(roots[ROOT2[1]], args.speed_fix)
    lamps = patch_lamps(roots[LAMPS[1]])
    center = patch_center(roots[CENTER[1]])
    model3d = patch_model3d(roots[MODEL3D[1]])
    adas3d = patch_adas3d(roots[ADAS3D[1]])
    if (unpatch_root(root2) != roots[ROOT2[1]] or unpatch_lamps(lamps) != roots[LAMPS[1]]
            or strip_markers(center) != roots[CENTER[1]] or unpatch_model3d(model3d) != roots[MODEL3D[1]]
            or strip_markers(adas3d) != roots[ADAS3D[1]]):
        raise SystemExit("patch is not exactly reversible")

    owned = horizon_files(args.horizon)
    # Stored uncompressed, like the Horizon files: zlib output differs between implementations, and
    # the app builds this same carrier on the car (byd/theme/HorizonCarrier.kt), byte for byte.
    entries = [(ROOT1[1], roots[ROOT1[1]], 0), (ROOT2[1], root2, 0), (LAMPS[1], lamps, 0), (CENTER[1], center, 0),
               (MODEL3D[1], model3d, 0), (ADAS3D[1], adas3d, 0)]
    entries += [(alias, data, 0) for alias, data in owned.items()]
    for alias, data, compressed in entries:
        raw = struct.pack(">I", len(data)) + zlib.compress(data, 9) if compressed else data
        rows.append((alias, 0, 1, 1 if compressed else 0, raw, data))
    carrier = codec.build(rows)
    if not 0 < len(carrier) < MAX_CARRIER:
        raise SystemExit(f"carrier size {len(carrier)} outside the recovery bound")

    # decode back and prove: stock locale records untouched, owned entries exact
    decoded = {r[0]: r for r in codec.decode(carrier)}
    if len(decoded) != len(rows):
        raise SystemExit("duplicate aliases in the carrier")
    for r in original_rows:
        d = decoded[r[0]]
        if (d[1], d[2], d[3], d[4], d[5]) != (r[1], r[2], r[3], r[4], r[5]):
            raise SystemExit("stock locale entry changed: " + r[0])
    for alias, data, _ in entries:
        if decoded[alias][5] != data:
            raise SystemExit("owned entry does not round-trip: " + alias)

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    name = "OFFLINE_ONLY_horizon_sx309_88.rcc"
    manifest = {
        "schema": 1, "variant": "horizon-sx309-88", "vehicle_validated": False, "runtime_activation_allowed": False,
        "cohort": {"libBydCluster": "9f8a0b269fbee37bad510e8dfbc239b857e00a87663fc9fb7ae39913f86017ca",
                   "libBydDataSource": "46c367844f6dee435485107d83f709f69cb7d7cd9f84bb1cf40c7a38deeb83bf",
                   "stock": STOCK_FILES},
        "carrier": name, "carrier_sha256": sha(carrier), "carrier_size": len(carrier),
        "speed_fix": bool(args.speed_fix),
        "locale_entries": LOCALES, "locales_preserved_byte_for_byte": True,
        "factory_sources": {"qml/cluster.qml": ROOT1[2], "qml10_25/cluster.qml": ROOT2[2],
                            "qml10_25/WarinLightLayout.qml": LAMPS[2], "qml10_25/Center.qml": CENTER[2],
                            MODEL3D[1]: MODEL3D[2], ADAS3D[1]: ADAS3D[2]},
        "patched_sources": {"qml/cluster.qml": ROOT1[2] + " (unchanged)", "qml10_25/cluster.qml": sha(root2),
                            "qml10_25/WarinLightLayout.qml": sha(lamps), "qml10_25/Center.qml": sha(center),
                            MODEL3D[1]: sha(model3d), ADAS3D[1]: sha(adas3d)},
        "owned_resources": {alias: sha(data) for alias, data in owned.items()},
        "entries": len(rows),
    }
    files = {name: carrier,
             "manifest.json": (json.dumps(manifest, indent=2) + "\n").encode(),
             "patched/qml10_25/cluster.qml": root2, "patched/qml10_25/WarinLightLayout.qml": lamps,
             "patched/qml10_25/Center.qml": center,
             "patches/Center.qml.diff": diff(roots[CENTER[1]], center, "qml10_25/Center.qml").encode(),
             "patches/Model3D.qml.diff": diff(roots[MODEL3D[1]], model3d, MODEL3D[1]).encode(),
             "patches/Adas3dContainer.qml.diff": diff(roots[ADAS3D[1]], adas3d, ADAS3D[1]).encode(),
             "patches/cluster.qml.diff": diff(roots[ROOT2[1]], root2, "qml10_25/cluster.qml").encode(),
             "patches/WarinLightLayout.qml.diff": diff(roots[LAMPS[1]], lamps, "qml10_25/WarinLightLayout.qml").encode()}
    for rel, data in files.items():
        target = out / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if args.check:
            if target.read_bytes() != data:
                raise SystemExit("not reproducible: " + rel)
        else:
            target.write_bytes(data)
    sums = "".join(f"{sha(files[k])}  {k}\n" for k in sorted(files))
    (out / "SHA256SUMS").write_text(sums) if not args.check else None
    print(f"{'CHECK' if args.check else 'BUILT'} {name}: {len(rows)} entries, {len(carrier)} bytes, SHA-256 {sha(carrier)}")
    print("root diff lines:", diff(roots[ROOT2[1]], root2, "x").count("\n+") , "added;",
          "lamps diff lines:", diff(roots[LAMPS[1]], lamps, "x").count("\n+"), "added")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--stock", required=True, help="dir with the car's cluster_theme1/2.rcc and commonTranslate.rcc")
    p.add_argument("--horizon", required=True, help="dir with the Horizon .qml/.js files")
    p.add_argument("--out", required=True)
    p.add_argument("--codec-dir", default=os.environ.get("DASHCAST_CLUSTER_TOOLS", DEFAULT_CODEC))
    p.add_argument("--speed-fix", action="store_true",
                   help="also give the factory naviSpeedId a geometry (fixes the 'm/h' bug with Horizon off)")
    p.add_argument("--check", action="store_true", help="rebuild and require identical bytes in --out")
    build(p.parse_args())


if __name__ == "__main__":
    main()
