import QtQuick 2.15

// Right column: power on the gauge the driver picked (the wheel menu or the app's BYD panel; five
// styles, see Power*.qml), and what the last 50 km cost underneath. Only the chosen gauge exists.
Item {
    id: energy
    property HorizonTheme theme
    property HorizonData car
    property var historySeed: []
    // "ring", "segments", "arc", "column" or "wave"; anything else is the ring.
    property string style: "ring"

    readonly property var gauges: ({ ring: ring, segments: segments, arc: arc, column: column, wave: wave })

    Loader {
        anchors.fill: parent
        sourceComponent: energy.gauges[energy.style] || ring
    }

    Component { id: ring; PowerRing { theme: energy.theme; car: energy.car } }
    Component { id: segments; PowerSegments { theme: energy.theme; car: energy.car } }
    Component { id: arc; PowerEdgeArc { theme: energy.theme; car: energy.car } }
    Component { id: column; PowerColumn { theme: energy.theme; car: energy.car } }
    Component { id: wave; PowerBarWave { theme: energy.theme; car: energy.car; historySeed: energy.historySeed } }

    Consumption {
        theme: energy.theme
        car: energy.car
        anchors { left: parent.left; right: parent.right; bottom: parent.bottom }
    }
}
