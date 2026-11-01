import QtQuick 2.15
import "HorizonGeometry.js" as Geometry

// Horizon: the custom instrument scene for the BYD Song Pro 8.8" cluster.
//
// Instrument mode: the factory ADAS 3D road in the middle, music (with synced lyrics) or the trip
// on the left, power on the right. Map mode, full screen (chosen in the app): the map under the
// whole panel, Horizon only a frame round it with the speed, the gear and power at the top. A steering wheel key can open Horizon's own menu in the left
// card's place (the app's WheelMenuService; see HorizonMenuFeed). Map mode (the Android projection is on): the map in a centred
// window cut through this scene, ADAS 3D in a window on the left.
//
// It runs in the factory "Simples" slot of the cluster's theme menu (the carrier's root patch);
// the drive mode (ECO, NORMAL, SPORT) tints the world behind the instruments.
//
// The native telltales, warning pop-ups and settings menu are not part of this item: they stay
// the factory's, drawn above it. While one of those screens is in the centre, the road steps back
// and the warnings get a card (FactoryCentre, WarningCard).
Item {
    id: horizon
    width: 1920
    height: 720

    // Read by the carrier's root patch: the native 2D ADAS pane is hidden only once the 3D scene
    // is actually up (it loads asynchronously), and kept when 3D cannot load at all.
    readonly property bool adasReady: stage.ready
    readonly property bool adasFallback: stage.failed
    // 0 music, 1 trip: as chosen in the wheel menu, or automatic (music while there is music).
    property int leftPage: menuFeed.leftCard === "music" ? 0 : menuFeed.leftCard === "trip" ? 1 : (car.mediaPresent ? 0 : 1)
    property string clock: "--:--"
    property alias mediaDirectory: car.mediaDirectory
    property alias powerHistorySeed: energy.historySeed
    // The power gauge chosen in the wheel menu or the BYD panel (see EnergyCard).
    property string powerStyle: menuFeed.powerStyle
    // Where the map goes (chosen in the app or the wheel menu; switches live): a centre window, the
    // right side with the road kept in the middle ("side"), or under the whole panel ("full").
    property bool fullMap: car.mapMode && menuFeed.mapLayout === "full"
    property bool sideMap: car.mapMode && menuFeed.mapLayout === "side"
    // The centre-window layout; the side layout keeps the instrument layout's left card.
    readonly property bool windowMap: car.mapMode && !fullMap && !sideMap

    readonly property var layout: windowMap ? Geometry.map : Geometry.native
    // The factory Quick3D road. false keeps the native 2D ADAS instead (the root patch moves it
    // into this scene); the carrier makes the road's target loaders synchronous, see the builder.
    readonly property bool adas3dEnabled: true
    // Diagnostic builds only: the raw ADAS values over the left card (see DiagOverlay).
    readonly property bool diagnostics: false

    HorizonData { id: car }
    HorizonMenuFeed { id: menuFeed; directory: car.mediaDirectory }
    HorizonTheme { id: theme; night: car.night; mode: car.driveMode }
    // The factory screens drawn above Horizon (menu, warnings, 0-100, charging...), see FactoryCentre.
    FactoryCentre { id: centre }

    Timer {
        interval: 1000; running: true; repeat: true; triggeredOnStart: true
        onTriggered: horizon.clock = Qt.formatTime(new Date(), "hh:mm")
    }

    Backdrop {
        id: backdrop
        anchors.fill: parent
        theme: theme
        mapMode: car.mapMode
        fullMap: horizon.fullMap
        hole: horizon.sideMap ? Geometry.side.video : Geometry.map.video
        holeFeather: horizon.sideMap ? Qt.vector4d(Geometry.side.feather[0], Geometry.side.feather[1],
                                                   Geometry.side.feather[2], Geometry.side.feather[3])
                                     : Qt.vector4d(90, 60, 90, 44)
        topSolid: Geometry.full.topSolid
        topBand: Geometry.full.topBand
        speed: car.speed
    }

    // Over the full-screen map the ADAS window sits on a card of its own, like the factory's.
    Rectangle {
        visible: horizon.fullMap
        readonly property var r: Geometry.full.adas
        x: r.x; y: r.y; width: r.width; height: r.height
        radius: 24
        color: theme.night ? "#e6070b12" : "#e6f3f6f9"
        border { width: 1; color: theme.hairline }
    }

    // ADAS: the whole road in the middle (instruments, and the side layout, narrower), the
    // factory's own window on the left (centre-window layout) or a small window over the
    // full-screen map.
    AdasStage {
        id: stage
        active: car.poweredOn
        use3d: horizon.adas3dEnabled
        windowed: horizon.windowMap || horizon.fullMap
        windowFeather: horizon.fullMap ? Qt.vector4d(24, 28, 24, 24) : Qt.vector4d(56, 70, 56, 62)
        // The road steps back while a factory screen is in the centre over it.
        opacity: centre.busy && !horizon.windowMap && !horizon.fullMap ? 0.12 : 1
        Behavior on opacity { NumberAnimation { duration: 250 } }
        readonly property var r: horizon.fullMap ? Geometry.full.adas
                               : horizon.sideMap ? Geometry.side.stage
                               : car.mapMode ? Geometry.map.adasMini : Geometry.native.stage
        x: r.x; y: r.y; width: r.width; height: r.height
    }

    SpeedHero {
        theme: theme
        car: car
        x: (horizon.width - width) / 2
        y: 0
    }

    // Left column: music or the trip (compact music only in the centre-window layout); the wheel menu while it is open
    // (full screen, only the menu, on its own backing over the map).
    Item {
        id: leftColumn
        readonly property var r: horizon.fullMap ? Geometry.full.menu : horizon.layout.leftCard
        x: r.x; y: r.y; width: r.width; height: r.height

        MusicCard {
            anchors.fill: parent
            theme: theme
            car: car
            compact: horizon.windowMap
            opacity: !menuFeed.open && !horizon.fullMap && (horizon.windowMap || horizon.leftPage === 0) ? 1 : 0
            visible: opacity > 0
            Behavior on opacity { NumberAnimation { duration: 220 } }
        }
        TripCard {
            anchors { left: parent.left; right: parent.right; verticalCenter: parent.verticalCenter }
            theme: theme
            car: car
            opacity: !menuFeed.open && (!car.mapMode || horizon.sideMap) && horizon.leftPage === 1 ? 1 : 0
            visible: opacity > 0
            Behavior on opacity { NumberAnimation { duration: 220 } }
        }
        HorizonMenu {
            anchors.fill: parent
            theme: theme
            feed: menuFeed
            backed: horizon.fullMap
            opacity: menuFeed.open ? 1 : 0
            visible: opacity > 0
            Behavior on opacity { NumberAnimation { duration: 160 } }
        }
    }

    EnergyCard {
        id: energy
        theme: theme
        car: car
        style: horizon.powerStyle
        readonly property var r: horizon.layout.rightCard
        x: r.x; y: r.y; width: r.width; height: r.height
        visible: !horizon.fullMap && !horizon.sideMap
    }

    PowerPill {
        theme: theme
        car: car
        visible: horizon.fullMap || horizon.sideMap
        readonly property var r: Geometry.full.power
        x: r.x; y: r.y; width: r.width; height: r.height
    }

    WarningCard {
        theme: theme
        shown: centre.warning
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
        backed: horizon.fullMap
        x: Geometry.lampBands.left + 12
        width: Geometry.lampBands.right - Geometry.lampBands.left - 24
        y: 600
        gapX: Geometry.readyLamp.x - x
        gapWidth: Geometry.readyLamp.width
    }
}
