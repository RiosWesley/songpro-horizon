"""Render a cluster QML scene to PNG on the desktop, with a mock BYD DataSource.

The factory cluster resources (meshes, textures, the ADAS 3D scene) come from the car's own
`cluster_theme2.rcc`, registered as-is. Native BYD QML types the RCC does not carry (DataSource,
CustomStyle, CenterChild, AndroidNativeWindowItem) are replaced by mocks. QtQuick3D needs a real
OpenGL context, so this runs on the desktop session (Wayland), not the offscreen platform; a
window may flash briefly while a frame renders.

    python render.py SCENE.qml SCENARIO.json OUT.png [--wait-ms 2500]
"""
import argparse
import json
import os
import sys
import tempfile

from PyQt5.QtCore import QResource, QTimer, QUrl
from PyQt5.QtGui import QGuiApplication
from PyQt5.QtQml import qmlRegisterSingletonType, qmlRegisterType
from PyQt5.QtQuick import QQuickView

HERE = os.path.dirname(os.path.abspath(__file__))


def qml_value(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return repr(value)
    return json.dumps(value)


def qml_type(value):
    if isinstance(value, bool):
        return "bool"
    if isinstance(value, int):
        return "int"
    if isinstance(value, float):
        return "real"
    return "string"


def write_datasource(values, directory):
    """The mock DataSource is generated per scenario: one QML property per value.

    UPPER_CASE names are the native enum constants (SEND_MSG_ID_KEY_LEFT…); QML only allows
    those as enum values, which a singleton exposes the same way: DataSource.NAME."""
    lines = ["pragma Singleton", "import QtQuick 2.15", "QtObject {"]
    constants = {k: v for k, v in values.items() if k[:1].isupper()}
    if constants:
        lines.append("    enum Constants { " + ", ".join(f"{k} = {int(v)}" for k, v in sorted(constants.items())) + " }")
    for name, value in sorted(values.items()):
        if name in constants:
            continue
        lines.append(f"    property {qml_type(value)} {name}: {qml_value(value)}")
    lines.append("    function sendPluginMsgInt(id, value) {}")
    lines.append("}")
    path = os.path.join(directory, "DataSource.qml")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    return path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scene")
    parser.add_argument("scenario")
    parser.add_argument("out")
    parser.add_argument("--wait-ms", type=int, default=2500)
    parser.add_argument("--rcc", default=os.environ.get("CLUSTER_RCC"))
    args = parser.parse_args()

    with open(args.scenario, encoding="utf-8") as f:
        scenario = json.load(f)
    values = {}
    for base in scenario.get("extends", []):
        with open(os.path.join(os.path.dirname(args.scenario), base), encoding="utf-8") as f:
            values.update(json.load(f)["datasource"])
    values.update(scenario.get("datasource", {}))

    app = QGuiApplication(sys.argv)
    if args.rcc and not QResource.registerResource(args.rcc):
        sys.exit(f"cannot register {args.rcc}")
    sys.path.insert(0, os.path.join(HERE, "stubs"))
    import stubs_rc  # noqa: F401  registers qrc:/common/singleton

    tmp = tempfile.mkdtemp(prefix="ds-")
    qmlRegisterSingletonType(QUrl.fromLocalFile(write_datasource(values, tmp)),
                             "com.byd.DataSource", 1, 0, "DataSource")
    # Each stubs/byd/<Name>.qml stands in for the native type com.byd.<Name> 1.0.
    for file in sorted(os.listdir(os.path.join(HERE, "stubs/byd"))):
        name = os.path.splitext(file)[0]
        qmlRegisterType(QUrl.fromLocalFile(os.path.join(HERE, "stubs/byd", file)),
                        f"com.byd.{name}", 1, 0, name)

    view = QQuickView()
    view.engine().rootContext().setContextProperty("mockAssets", QUrl.fromLocalFile(os.path.join(HERE, "assets")).toString())
    view.setSource(QUrl.fromLocalFile(os.path.abspath(args.scene)))
    errors = [e.toString() for e in view.errors()]
    if errors:
        sys.exit("\n".join(errors))
    view.show()
    root = view.rootObject()

    def grab():
        result = root.grabToImage()

        def save():
            result.image().save(args.out)
            print(f"saved {args.out} {result.image().width()}x{result.image().height()}")
            app.quit()
        result.ready.connect(save)

    QTimer.singleShot(args.wait_ms, grab)
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
