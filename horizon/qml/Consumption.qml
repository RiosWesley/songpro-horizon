import QtQuick 2.15

// What the last 50 km cost: electricity and fuel per 100 km, in two equal columns centred under
// the power gauge, a hairline between them. Each column's colour (regeneration green for
// electricity, power amber for fuel) is a short tick over its figure.
Item {
    id: use
    property HorizonTheme theme
    property HorizonData car
    implicitHeight: 132

    Text {
        anchors { horizontalCenter: parent.horizontalCenter; top: parent.top }
        text: "últimos 50 km"
        color: use.theme.textFaint
        font { family: use.theme.font; pixelSize: 22; letterSpacing: 1 }
    }

    Row {
        id: columns
        anchors { left: parent.left; right: parent.right; bottom: parent.bottom }
        Repeater {
            model: [
                { value: use.car.recent50Power, unit: "kWh/100 km", tint: use.theme.regen },
                { value: use.car.recent50Fuel, unit: "L/100 km", tint: use.theme.power }
            ]
            Column {
                width: columns.width / 2
                Rectangle {
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: 28; height: 4; radius: 2
                    color: modelData.tint
                    opacity: 0.85
                }
                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    topPadding: 8
                    text: modelData.value
                    color: use.theme.text
                    font { family: use.theme.font; pixelSize: 41; weight: Font.Light }
                }
                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    text: modelData.unit
                    color: use.theme.textFaint
                    font { family: use.theme.font; pixelSize: 21 }
                }
            }
        }
    }

    Rectangle {
        x: Math.round(use.width / 2)
        anchors { top: columns.top; bottom: columns.bottom; topMargin: 12 }
        width: 1
        color: use.theme.hairline
    }
}
