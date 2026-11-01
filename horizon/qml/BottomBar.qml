import QtQuick 2.15

// Bottom row, above the lower telltales: clock and temperature, battery | READY | fuel, drive
// mode and odometer. The battery and fuel gauges sit either side of the slot the native "OK"
// lamp lights in. Over the full-screen map (`backed`) each group gets its own dark backing, so the
// map runs on under the row and between the groups.
Item {
    id: strip
    property HorizonTheme theme
    property HorizonData car
    property string clock: "--:--"
    // The READY lamp's slot, in this item's coordinates.
    property real gapX: width / 2 - 60
    property real gapWidth: 120
    property bool backed: false
    height: 48

    // One dark backing per group over the full-screen map.
    Repeater {
        model: [leftGroup, batteryBar, fuelBar, rightGroup]
        Rectangle {
            readonly property Item around: modelData
            visible: strip.backed && around.visible
            x: around.x - 20; y: around.y - 8
            width: around.width + 40; height: around.height + 16
            radius: height / 2
            color: strip.theme.night ? "#e0070b12" : "#e0f3f6f9"
        }
    }

    Row {
        id: leftGroup
        anchors { left: parent.left; verticalCenter: parent.verticalCenter }
        spacing: 22
        Text {
            id: clockText
            text: strip.clock
            color: strip.theme.text
            font { family: strip.theme.font; pixelSize: 37 }
        }
        Text {
            visible: strip.car.tempKnown
            anchors.baseline: clockText.baseline
            text: strip.car.outsideTemp
            color: strip.theme.textDim
            font { family: strip.theme.font; pixelSize: 32 }
        }
    }

    LevelBar {
        id: batteryBar
        theme: strip.theme
        anchors.verticalCenter: parent.verticalCenter
        width: 360
        x: strip.gapX - width - 28
        kind: "battery"
        percent: strip.car.soc
        rangeKm: strip.car.rangeKm
        fill: strip.theme.regen
    }
    LevelBar {
        id: fuelBar
        theme: strip.theme
        anchors.verticalCenter: parent.verticalCenter
        width: 330
        x: strip.gapX + strip.gapWidth + 28
        kind: "fuel"
        percent: strip.car.fuelPercent
        rangeKm: strip.car.fuelRangeKm
        fill: strip.theme.text
    }

    Row {
        id: rightGroup
        anchors { right: parent.right; verticalCenter: parent.verticalCenter }
        spacing: 26
        Text {
            text: strip.car.driveMode
            color: strip.car.driveMode === "SPORT" ? strip.theme.power
                 : strip.car.driveMode === "ECO" ? strip.theme.regen : strip.theme.textDim
            font { family: strip.theme.font; pixelSize: 30; letterSpacing: 1; weight: Font.Medium }
        }
        Text {
            text: strip.car.odometer !== "" ? strip.car.odometer + " km" : ""
            color: strip.theme.textDim
            font { family: strip.theme.font; pixelSize: 30 }
        }
    }
}
