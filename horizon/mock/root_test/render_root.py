"""Render the PATCHED factory root (qml10_25/cluster.qml from the Horizon carrier) on the desktop.

Resource order mirrors the car's: our fixture (singletons + an empty Center), then the carrier
under test, then the car's stock cluster_theme2.rcc. Native BYD types come from mock/stubs/byd.
The DataSource is generated: every `DataSource.xxx` the QML mentions defaults to 0, the native
enum constants come from moc_enums.json (read out of libBydCluster.so), then the scenario wins.

    render_root.py CARRIER.rcc STOCK_theme2.rcc SCENARIO.json OUT.png [--platform offscreen|wayland]
"""
import argparse, glob, json, os, re, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOCK = os.path.dirname(HERE)
ENUMS = os.environ.get("MOC_ENUMS", os.path.join(HERE, "moc_enums.json"))
SCAN = [os.environ["THEME2_QML_DIR"]] if "THEME2_QML_DIR" in os.environ else []

FIXTURE = {
    "common/singleton/qmldir": "singleton CustomStyle 1.0 CustomStyle.qml\nsingleton TranslateStr 1.0 TranslateStr.qml\n",
    # Real values, read from the CustomStyle.qml text embedded in libBydCluster.so (SX309).
    "common/singleton/CustomStyle.qml": """pragma Singleton
import QtQuick 2.15
QtObject {
    readonly property int bgZ: -2
    readonly property int presentationZ: -3
    readonly property int themeSwithAnimZ: -1
    readonly property int adasZ: 99
    readonly property int warningLightZ: 100
    readonly property int powerOnAnimationZ: 200
    readonly property int approachAnimaionZ: 199
    readonly property int fpsZ: 201
    readonly property int adasPriority: 4
    readonly property int warningInfoPriority: 3
    readonly property int centerChildOpacityAnimateDuration: 200
    readonly property int themeOpacityChangeTimer: 300
    readonly property string naviFontFamily: "Noto Sans"
    readonly property string naviBoldFontFamily: "Noto Sans"
}
""",
    "common/singleton/TranslateStr.qml": "pragma Singleton\nimport QtQuick 2.15\nQtObject { property string adasCarTimeSet: 'ACC distance' }\n",
    # The real Center hosts the menu / drive info / warning pop-ups: out of scope for this test.
    "qml10_25/Center.qml": "import QtQuick 2.15\nItem { id: centerContainerId; z: 3; anchors.fill: parent\n"
                           "  property var adasUi: null; property var adasDowWarningUi: null; property var adas3r1vUi: null\n"
                           "  property bool horizonActive: false; property bool horizonAdas3d: false }\n",
}


STUB_3D = ("import QtQuick 2.15\nItem { width: 600; height: 352; visible: false\n"
           "  Rectangle { anchors.fill: parent; color: '#2b6cb0'; opacity: 0.55; radius: 12 }\n"
           "  Text { anchors.centerIn: parent; color: 'white'; font.pixelSize: 40; text: 'ADAS 3D (stub)' } }\n")


def build_fixture(tmp, stub3d):
    if stub3d:   # QtQuick3D needs a GL context: the software/offscreen backend segfaults on View3D
        FIXTURE["qml10_25/adas3d/Adas3dContainer.qml"] = STUB_3D
    qrc = ["<RCC><qresource prefix=\"/\">"]
    for name, text in FIXTURE.items():
        path = os.path.join(tmp, "fx", name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "w").write(text)
        qrc.append(f"<file>fx/{name}</file>")
    qrc.append("</qresource></RCC>")
    open(os.path.join(tmp, "fx.qrc"), "w").write("".join(qrc))
    out = os.path.join(tmp, "fixture.rcc")
    # alias-less qrc would prefix fx/; use aliases
    text = open(os.path.join(tmp, "fx.qrc")).read()
    text = re.sub(r"<file>fx/([^<]+)</file>", r'<file alias="\1">fx/\1</file>', text)
    open(os.path.join(tmp, "fx.qrc"), "w").write(text)
    subprocess.run(["rcc-qt5", "--binary", os.path.join(tmp, "fx.qrc"), "-o", out], check=True)
    return out


def qml_value(v):
    return "true" if v is True else "false" if v is False else repr(v) if isinstance(v, (int, float)) else json.dumps(v)


def datasource(scenario_values, names, funcs, tmp):
    enums = json.load(open(ENUMS))["DataSourceManager"]
    consts = {k: v for group in enums.values() for k, v in group.items()}
    used = {n for n in names if n in consts}
    lines = ["pragma Singleton", "import QtQuick 2.15", "QtObject {"]
    if used:
        lines.append("    enum Constants { " + ", ".join(f"{k} = {consts[k]}" for k in sorted(used)) + " }")
    missing_consts = sorted(n for n in names if n[:1].isupper() and n not in consts)
    if missing_consts: print('unknown constants (ignored):', missing_consts)
    names = names - funcs
    for n in sorted(x for x in names - used if x[:1].islower() or x[:1] == '_'):
        if n not in scenario_values:
            lines.append(f"    property var {n}: 0")
    for n, v in scenario_values.items():
        if n not in used and n[:1].islower():
            lines.append(f"    property var {n}: {qml_value(v)}")
    lines.append("    signal pluginMsgReceived(var id, var value)")
    lines += [f"    function {f}() {{ return 0 }}" for f in sorted(funcs)]
    lines.append("}")
    path = os.path.join(tmp, "DataSource.qml")
    open(path, "w").write("\n".join(lines) + "\n")
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("carrier"); ap.add_argument("theme2"); ap.add_argument("scenario"); ap.add_argument("out")
    ap.add_argument("--platform", default="offscreen"); ap.add_argument("--wait-ms", type=int, default=4000)
    ap.add_argument("--qml-dir", required=True, help="dir with the extracted stock qml10_25 tree (to list DataSource names)")
    ap.add_argument("--set", action="append", default=[], help="override NAME=VALUE (json)")
    ap.add_argument("--js", action="append", default=["alwyasDisplayId.visible = true", "warningLayoutId.visible = true", "centerContainerId.visible = true"])
    ap.add_argument("--stub-3d", action="store_true", help="replace Adas3dContainer by a flat stub (offscreen runs)")
    ap.add_argument("--probe", action="append", default=[], help="QML expression to print at capture time")
    a = ap.parse_args()
    if a.platform == "offscreen":
        os.environ["QT_QPA_PLATFORM"] = "offscreen"; os.environ["QT_QUICK_BACKEND"] = "software"
    from PyQt5.QtCore import QResource, QTimer, QUrl
    from PyQt5.QtGui import QGuiApplication
    from PyQt5.QtQml import qmlRegisterSingletonType, qmlRegisterType
    from PyQt5.QtQuick import QQuickView

    scen = json.load(open(a.scenario))
    values = {}
    for base in scen.get("extends", []):
        values.update(json.load(open(os.path.join(os.path.dirname(a.scenario), base)))["datasource"])
    values.update(scen.get("datasource", {}))
    for kv in a.set:
        k, v = kv.split("=", 1); values[k] = json.loads(v)
    names = set(); funcs = set()
    sources = glob.glob(a.qml_dir + "/**/*.qml", recursive=True)
    sources += glob.glob(os.path.join(MOCK, "..", "qml", "*"))
    if a.carrier != "none":
        sources += glob.glob(os.path.dirname(a.carrier) + "/patched/**/*.qml", recursive=True)
    for f in sources:
        text = open(f, encoding="utf-8-sig", errors="ignore").read()
        names |= set(re.findall(r"DataSource\.([A-Za-z_]\w*)", text))
        funcs |= set(re.findall(r"DataSource\.([A-Za-z_]\w*)\(", text))
    app = QGuiApplication(sys.argv)
    tmp = tempfile.mkdtemp(prefix="rt-")
    for rcc in [x for x in (build_fixture(tmp, a.stub_3d), a.carrier, a.theme2) if x != 'none']:
        if not QResource.registerResource(rcc):
            sys.exit("cannot register " + rcc)
    qmlRegisterSingletonType(QUrl.fromLocalFile(datasource(values, names, funcs, tmp)), "com.byd.DataSource", 1, 0, "DataSource")
    for file in sorted(os.listdir(os.path.join(MOCK, "stubs/byd"))):
        name = os.path.splitext(file)[0]
        qmlRegisterType(QUrl.fromLocalFile(os.path.join(MOCK, "stubs/byd", file)), f"com.byd.{name}", 1, 0, name)
    view = QQuickView()
    view.engine().rootContext().setContextProperty("mockAssets", QUrl.fromLocalFile(os.path.join(MOCK, "assets")).toString())
    view.setSource(QUrl("qrc:/qml10_25/cluster.qml"))
    errs = [e.toString() for e in view.errors()]
    if errs:
        sys.exit("\n".join(errs))
    view.show()
    root = view.rootObject()
    from PyQt5.QtQml import QQmlExpression
    def settle():   # what the factory does when the approach animation ends
        for expr in a.js:
            QQmlExpression(view.engine().contextForObject(root), root, expr).evaluate()
    def probe():
        g = lambda n: root.property(n)
        print("horizonWanted", g("horizonWanted"), "horizonActive", g("horizonActive"), "horizonAdas3d", g("horizonAdas3d"))
        for expr in a.probe:
            print("probe", expr, "=", QQmlExpression(view.engine().contextForObject(root), root, expr).evaluate())
        for child in root.findChildren(type(root.childItems()[0])) if False else []: pass
        res = root.grabToImage()
        def save():
            res.image().save(a.out); print("saved", a.out, res.image().width(), res.image().height()); app.quit()
        res.ready.connect(save)
    QTimer.singleShot(500, settle)
    QTimer.singleShot(a.wait_ms, probe)
    sys.exit(app.exec_())

main()
