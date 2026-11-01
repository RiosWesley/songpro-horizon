import QtQuick 2.15

// Top centre: the speed, the recognised speed limit beside it and the gear.
Item {
    id: hero
    property HorizonTheme theme
    property HorizonData car
    width: 720
    height: 132

    readonly property bool overLimit: car.speedLimit > 0 && car.speed > car.speedLimit

    Text {
        id: speedText
        anchors.horizontalCenter: parent.horizontalCenter
        y: -10
        text: hero.car.speed
        color: hero.overLimit ? hero.theme.alert : hero.theme.text
        font { family: hero.theme.font; pixelSize: 124; weight: Font.Light }
        Behavior on color { ColorAnimation { duration: 250 } }
    }
    Text {
        anchors { horizontalCenter: parent.horizontalCenter; top: speedText.baseline; topMargin: 10 }
        text: hero.car.speedUnit
        color: hero.theme.textDim
        font { family: hero.theme.font; pixelSize: 24; letterSpacing: 1 }
    }

    // Speed limit sign, as recognised by the camera.
    Rectangle {
        visible: hero.car.speedLimit > 0
        anchors { right: speedText.left; rightMargin: 44; verticalCenter: speedText.verticalCenter; verticalCenterOffset: 8 }
        width: 72; height: 72; radius: 36
        color: "#f5f6f7"
        border { color: "#d8242d"; width: 6 }
        Text {
            anchors.centerIn: parent
            text: hero.car.speedLimit
            color: "#121212"
            font { family: hero.theme.fontBold; pixelSize: hero.car.speedLimit >= 100 ? 26 : 32; weight: Font.DemiBold }
        }
    }

    // P R N D: the engaged gear in full, the others receding.
    Row {
        anchors { left: speedText.right; leftMargin: 44; verticalCenter: speedText.verticalCenter; verticalCenterOffset: 8 }
        spacing: 14
        Repeater {
            model: ["P", "R", "N", "D"]
            Text {
                readonly property bool engaged: modelData === hero.car.gear
                text: modelData
                color: engaged ? hero.theme.text : hero.theme.textFaint
                font { family: hero.theme.font; pixelSize: 30; weight: engaged ? Font.DemiBold : Font.Normal }
            }
        }
    }
}
