import QtQuick 2.15

// Right column: instant power on a numbered gauge, the last minute of power under it, and what
// the last 50 km cost in electricity and fuel.
Item {
    id: energy
    property HorizonTheme theme
    property HorizonData car
    property alias historySeed: history.seed

    readonly property bool regenerating: car.powerKw < -0.5

    PowerArc {
        id: arc
        theme: energy.theme
        value: energy.car.powerKw
        width: 340; height: 340
        anchors.horizontalCenter: parent.horizontalCenter
        y: -46
    }
    Text {
        id: kw
        anchors { horizontalCenter: arc.horizontalCenter; verticalCenter: arc.verticalCenter; verticalCenterOffset: -8 }
        text: Math.round(Math.abs(energy.car.powerKw))
        color: energy.regenerating ? energy.theme.regen : energy.theme.text
        font { family: energy.theme.font; pixelSize: 76; weight: Font.Light }
    }
    Text {
        anchors { horizontalCenter: kw.horizontalCenter; top: kw.baseline; topMargin: 10 }
        text: energy.regenerating ? "kW  regenerando" : "kW"
        color: energy.regenerating ? energy.theme.regen : energy.theme.textDim
        font { family: energy.theme.font; pixelSize: 22; letterSpacing: 0.5 }
    }

    PowerHistory {
        id: history
        theme: energy.theme
        value: energy.car.powerKw
        anchors { left: parent.left; right: parent.right }
        y: 276
        height: 78
    }
    Text {
        anchors { right: history.right; bottom: history.top; bottomMargin: 2 }
        text: "último minuto"
        color: energy.theme.textFaint
        font { family: energy.theme.font; pixelSize: 20 }
    }

    // Last 50 km: electricity and fuel per 100 km.
    Row {
        id: consumption
        anchors { left: parent.left; bottom: parent.bottom }
        spacing: 30
        Repeater {
            model: [
                { value: energy.car.recent50Power, unit: "kWh/100 km" },
                { value: energy.car.recent50Fuel, unit: "L/100 km" }
            ]
            Column {
                spacing: 2
                Text {
                    text: modelData.value
                    color: energy.theme.text
                    font { family: energy.theme.font; pixelSize: 36; weight: Font.Light }
                }
                Text {
                    text: modelData.unit
                    color: energy.theme.textFaint
                    font { family: energy.theme.font; pixelSize: 20 }
                }
            }
        }
    }
    Text {
        // On the numbers' line, clear of the units below them.
        anchors { right: parent.right; top: consumption.top; topMargin: 12 }
        text: "últimos 50 km"
        color: energy.theme.textFaint
        font { family: energy.theme.font; pixelSize: 20 }
    }
}
