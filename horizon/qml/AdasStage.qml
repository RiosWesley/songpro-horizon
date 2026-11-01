import QtQuick 2.15

// The factory ADAS 3D scene (cars, trucks, bikes, pedestrians, lanes, radar), which ships in the
// car's own theme RCC but is commented out of the factory centre. It is loaded from the RCC as
// is: the container keeps its own DataSource wiring and only needs to be made visible.
//
// If it cannot load on this cluster runtime, `failed` turns true and Horizon keeps the native 2D
// ADAS pane instead (see HorizonCluster).
Item {
    id: stage
    property bool active: true
    // false: the factory Quick3D scene is never created (the native 2D pane stays, see `ready`).
    property bool use3d: true
    // Instrument mode shows the whole road; map mode the factory's own rounded window.
    property bool windowed: false
    // A window's fades, left, top, right, bottom (the full-screen map's overlay window is tighter).
    property vector4d windowFeather: Qt.vector4d(56, 70, 56, 62)

    readonly property bool failed: loader.status === Loader.Error
                                   || (loader.status === Loader.Ready && loader.item === null)
    readonly property bool ready: loader.status === Loader.Ready && loader.item !== null

    // Where the 960x370 scene sits inside the container's coordinates: whole, or through the
    // 535x325 window the container masks it with in navigation mode.
    readonly property var sceneWindow: windowed ? { x: 0, y: 234, width: 535, height: 325 }
                                                : { x: -215, y: 193, width: 960, height: 370 }
    // The whole road fills the stage height and may run under the cards; the window fits inside.
    readonly property real sceneScale: windowed
        ? Math.min(width / sceneWindow.width, height / sceneWindow.height)
        : height / sceneWindow.height

    clip: true

    // The road melts into the backdrop. The factory scene lays a dark ground plane under the road
    // that starts with a straight edge at the horizon and widens to the sides like a trapezoid; a
    // rectangle fading from the stage's edges leaves both edges showing (seen on the car). So the
    // whole road is seen through an oval centred low on the car, gone before the horizon and the
    // plane's sides, and the near end still fades before the bottom bar. A window fades all round,
    // so its lanes never stop at an edge. A real alpha mask, so it works over any backdrop.
    Item {
        id: scene
        anchors.fill: parent
        layer.enabled: true
        layer.effect: SoftMask {
            feather: stage.windowed ? stage.windowFeather : Qt.vector4d(0, 0, 0, height * 0.28)
            ellipse: stage.windowed ? Qt.vector4d(0, 0, 0, 0)
                                    : Qt.vector4d(width / 2, height * 0.98, width * 0.46, height * 0.8)
            ellipseFeather: 0.5
        }

        Loader {
            id: loader
            active: stage.use3d
            asynchronous: true
            source: "qrc:/qml10_25/adas3d/Adas3dContainer.qml"
            transformOrigin: Item.TopLeft
            scale: stage.sceneScale
            x: (stage.width - stage.sceneWindow.width * stage.sceneScale) / 2 - stage.sceneWindow.x * stage.sceneScale
            y: (stage.height - stage.sceneWindow.height * stage.sceneScale) / 2 - stage.sceneWindow.y * stage.sceneScale
            onLoaded: item.visible = Qt.binding(function () { return stage.active })
        }
    }
}
