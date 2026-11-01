import QtQuick 2.15

// Second page of the left column: this trip, three figures over hairlines.
Column {
    id: trip
    property HorizonTheme theme
    property HorizonData car

    Repeater {
        model: [
            { label: "Viagem", value: trip.car.tripKm, unit: "km" },
            { label: "Tempo", value: trip.car.driveTime, unit: "" },
            { label: "Média", value: trip.car.avgSpeed, unit: trip.car.speedUnit }
        ]
        Item {
            width: trip.width
            height: 96
            Rectangle {
                visible: index > 0
                anchors { left: parent.left; right: parent.right; top: parent.top }
                height: 2
                color: trip.theme.hairline
            }
            Text {
                anchors { left: parent.left; verticalCenter: parent.verticalCenter }
                text: modelData.label
                color: trip.theme.textDim
                font { family: trip.theme.font; pixelSize: 26 }
            }
            Row {
                anchors { right: parent.right; verticalCenter: parent.verticalCenter }
                spacing: 8
                Text {
                    id: value
                    text: modelData.value
                    color: trip.theme.text
                    font { family: trip.theme.font; pixelSize: 50; weight: Font.Light }
                }
                Text {
                    anchors.baseline: value.baseline
                    text: modelData.unit
                    color: trip.theme.textDim
                    font { family: trip.theme.font; pixelSize: 23 }
                }
            }
        }
    }
}
