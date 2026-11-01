import QtQuick 2.15

// Power for the full-screen map, in the top band opposite the gear: the figure and a thin bar that
// leaves zero to the right (power, amber) or to the left (regeneration, green).
Item {
    id: pill
    property HorizonTheme theme
    property HorizonData car

    readonly property real maxPower: 150
    readonly property real maxRegen: 60
    readonly property real v: Math.max(-maxRegen, Math.min(maxPower, car.powerKw))
    readonly property bool regenerating: v < -0.5

    Text {
        id: figure
        anchors.right: unit.left
        anchors.rightMargin: 8
        y: 0
        text: Math.round(Math.abs(pill.v))
        color: pill.regenerating ? pill.theme.regen : pill.theme.text
        font { family: pill.theme.font; pixelSize: 54; weight: Font.Light }
    }
    Text {
        id: unit
        anchors { right: parent.right; baseline: figure.baseline }
        text: "kW"
        color: pill.regenerating ? pill.theme.regen : pill.theme.textDim
        font { family: pill.theme.font; pixelSize: 26 }
    }

    Item {
        id: bar
        anchors { left: parent.left; right: parent.right; top: figure.bottom; topMargin: 4 }
        height: 7
        readonly property real zeroX: width * pill.maxRegen / (pill.maxPower + pill.maxRegen)
        readonly property real tipX: pill.v >= 0 ? zeroX + pill.v / pill.maxPower * (width - zeroX)
                                                : zeroX - (-pill.v / pill.maxRegen) * zeroX
        Rectangle { anchors.fill: parent; radius: height / 2; color: pill.theme.track }
        Rectangle {
            visible: Math.abs(pill.v) > 0.5
            x: Math.min(bar.zeroX, bar.tipX); width: Math.abs(bar.tipX - bar.zeroX)
            height: parent.height; radius: height / 2
            color: pill.regenerating ? pill.theme.regen : pill.theme.power
        }
        Rectangle { x: bar.zeroX - 1; y: -5; width: 2; height: parent.height + 10; color: pill.theme.textDim }
    }
}
