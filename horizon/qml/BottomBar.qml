import QtQuick 2.15

// Bottom row, above the lower telltales: clock and temperature, battery | READY | fuel, drive
// mode and odometer. The battery and fuel gauges sit either side of the slot the native "OK"
// lamp lights in.
Item {
    id: bottom
    property HorizonTheme theme
    property HorizonData car
    property string clock: "--:--"
    // The READY lamp's slot, in this item's coordinates.
    property real gapX: width / 2 - 60
    property real gapWidth: 120
    height: 48

    Row {
        anchors { left: parent.left; verticalCenter: parent.verticalCenter }
        spacing: 22
        Text {
            id: clockText
            text: bottom.clock
            color: bottom.theme.text
            font { family: bottom.theme.font; pixelSize: 32 }
        }
        Text {
            visible: bottom.car.tempKnown
            anchors.baseline: clockText.baseline
            text: bottom.car.outsideTemp
            color: bottom.theme.textDim
            font { family: bottom.theme.font; pixelSize: 28 }
        }
    }

    LevelBar {
        theme: bottom.theme
        anchors.verticalCenter: parent.verticalCenter
        width: 360
        x: bottom.gapX - width - 28
        kind: "battery"
        percent: bottom.car.soc
        rangeKm: bottom.car.rangeKm
        fill: bottom.theme.regen
    }
    LevelBar {
        theme: bottom.theme
        anchors.verticalCenter: parent.verticalCenter
        width: 330
        x: bottom.gapX + bottom.gapWidth + 28
        kind: "fuel"
        percent: bottom.car.fuelPercent
        rangeKm: bottom.car.fuelRangeKm
        fill: bottom.theme.text
    }

    Row {
        anchors { right: parent.right; verticalCenter: parent.verticalCenter }
        spacing: 26
        Text {
            text: bottom.car.driveMode
            color: bottom.car.driveMode === "SPORT" ? bottom.theme.power
                 : bottom.car.driveMode === "ECO" ? bottom.theme.regen : bottom.theme.textDim
            font { family: bottom.theme.font; pixelSize: 26; letterSpacing: 1; weight: Font.Medium }
        }
        Text {
            text: bottom.car.odometer !== "" ? bottom.car.odometer + " km" : ""
            color: bottom.theme.textDim
            font { family: bottom.theme.font; pixelSize: 26 }
        }
    }
}
