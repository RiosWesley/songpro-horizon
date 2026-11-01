import QtQuick 2.15

// What the last 50 km cost: electricity and fuel per 100 km, side by side.
Item {
    id: use
    property HorizonTheme theme
    property HorizonData car
    implicitHeight: 118

    Text {
        anchors { left: parent.left; top: parent.top }
        text: "últimos 50 km"
        color: use.theme.textFaint
        font { family: use.theme.font; pixelSize: 22; letterSpacing: 1 }
    }
    Row {
        anchors { left: parent.left; bottom: parent.bottom }
        spacing: 34
        Repeater {
            model: [
                { value: use.car.recent50Power, unit: "kWh/100 km", tint: use.theme.regen },
                { value: use.car.recent50Fuel, unit: "L/100 km", tint: use.theme.power }
            ]
            Row {
                spacing: 10
                Rectangle {
                    width: 4; height: 40; radius: 2
                    anchors.bottom: parent.bottom
                    color: modelData.tint
                    opacity: 0.8
                }
                Column {
                    Text {
                        text: modelData.value
                        color: use.theme.text
                        font { family: use.theme.font; pixelSize: 41; weight: Font.Light }
                    }
                    Text {
                        text: modelData.unit
                        color: use.theme.textFaint
                        font { family: use.theme.font; pixelSize: 21 }
                    }
                }
            }
        }
    }
}
