import QtQuick 2.15
import com.byd.DataSource 1.0
import "HorizonGeometry.js" as Geometry

// Horizon: the custom instrument scene for the BYD Song Pro 8.8" cluster.
//
// Instrument mode: the factory ADAS 3D road in the middle, music (with synced lyrics) or the trip
// on the left, power on the right. Map mode (the Android projection is on): the map in a centred
// window cut through this scene, ADAS 3D in a window on the left.
//
// The native telltales, warning pop-ups and settings menu are not part of this item: they stay
// the factory's, drawn above it.
Item {
    id: horizon
    width: 1920
    height: 720

    // Read by the carrier's root patch: the native 2D ADAS pane is hidden only once the 3D scene
    // is actually up (it loads asynchronously), and kept when 3D cannot load at all.
    readonly property bool adasReady: stage.ready
    readonly property bool adasFallback: stage.failed
    // 0 music, 1 trip; flipped with the steering wheel.
    property int leftPage: 0
    property string clock: "--:--"
    property alias mediaDirectory: car.mediaDirectory
    property alias powerHistorySeed: energy.historySeed

    readonly property var layout: car.mapMode ? Geometry.map : Geometry.native
    // The factory Quick3D road. false keeps the native 2D ADAS instead (the root patch moves it
    // into this scene); the carrier makes the road's target loaders synchronous, see the builder.
    readonly property bool adas3dEnabled: true
    // Diagnostic builds only: the raw ADAS values over the left card (see DiagOverlay).
    readonly property bool diagnostics: false

    HorizonData { id: car }
    HorizonTheme { id: theme; night: car.night }

    Timer {
        interval: 1000; running: true; repeat: true; triggeredOnStart: true
        onTriggered: horizon.clock = Qt.formatTime(new Date(), "hh:mm")
    }

    Backdrop {
        id: backdrop
        anchors.fill: parent
        theme: theme
        mapMode: car.mapMode
        hole: Geometry.map.hole
    }

    // ADAS: the whole road in the middle, or the factory's own window on the left in map mode.
    AdasStage {
        id: stage
        active: car.poweredOn
        use3d: horizon.adas3dEnabled
        windowed: car.mapMode
        readonly property var r: car.mapMode ? Geometry.map.adasMini : Geometry.native.stage
        x: r.x; y: r.y; width: r.width; height: r.height
    }

    SpeedHero {
        theme: theme
        car: car
        x: (horizon.width - width) / 2
        y: 0
    }

    // Left column: music or the trip, flipped with the steering wheel; music only in map mode.
    Item {
        id: leftColumn
        readonly property var r: horizon.layout.leftCard
        x: r.x; y: r.y; width: r.width; height: r.height

        MusicCard {
            anchors.fill: parent
            theme: theme
            car: car
            compact: car.mapMode
            opacity: car.mapMode || horizon.leftPage === 0 ? 1 : 0
            visible: opacity > 0
            Behavior on opacity { NumberAnimation { duration: 220 } }
        }
        TripCard {
            anchors { left: parent.left; right: parent.right; verticalCenter: parent.verticalCenter }
            theme: theme
            car: car
            opacity: !car.mapMode && horizon.leftPage === 1 ? 1 : 0
            visible: opacity > 0
            Behavior on opacity { NumberAnimation { duration: 220 } }
        }
    }

    EnergyCard {
        id: energy
        theme: theme
        car: car
        readonly property var r: horizon.layout.rightCard
        x: r.x; y: r.y; width: r.width; height: r.height
    }

    Loader {
        active: horizon.diagnostics
        x: Geometry.native.leftCard.x
        y: Geometry.native.leftCard.y - 20
        z: 10
        sourceComponent: DiagOverlay { theme: theme }
    }

    BottomBar {
        theme: theme
        car: car
        clock: horizon.clock
        x: Geometry.lampBands.left + 12
        width: Geometry.lampBands.right - Geometry.lampBands.left - 24
        y: 600
        gapX: Geometry.readyLamp.x - x
        gapWidth: Geometry.readyLamp.width
    }

    // Steering wheel: left/right flips the left card while the native menu is closed. The native
    // handler still receives every key, so the factory menu keeps working as before.
    Connections {
        target: DataSource
        function onPluginMsgReceived(id, value) {
            if (car.mapMode || DataSource.menuNeedDisplay === 1)
                return
            // value: KEY_CLICK (1); releases (0) and long clicks (2) are left to the native UI.
            if (value === 1 && (id === DataSource.SEND_MSG_ID_KEY_LEFT || id === DataSource.SEND_MSG_ID_KEY_RIGHT))
                horizon.leftPage = (horizon.leftPage + 1) % 2
        }
    }
}
