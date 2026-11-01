import QtQuick 2.15

// Power in one line, like the battery's bar, where the energy card gives way to the map (the
// full-screen map and the side layout): a hairline gauge that leaves zero to the right (power,
// amber) or to the left (regeneration, green), then the figure. The figure keeps a fixed width so
// the gauge does not jump when a digit is added.
Item {
    id: pill
    property HorizonTheme theme
    property HorizonData car
    height: 48

    readonly property real maxPower: 150
    readonly property real maxRegen: 60
    readonly property real v: Math.max(-maxRegen, Math.min(maxPower, car.powerKw))
    readonly property bool regenerating: v < -0.5

    Text {
        id: unit
        anchors { right: parent.right; verticalCenter: parent.verticalCenter }
        text: "kW"
        color: pill.regenerating ? pill.theme.regen : pill.theme.textDim
        font { family: pill.theme.font; pixelSize: 26 }
    }
    Text {
        id: figure
        anchors { right: unit.left; rightMargin: 8; baseline: unit.baseline }
        width: 72
        horizontalAlignment: Text.AlignRight
        text: Math.round(Math.abs(pill.v))
        color: pill.regenerating ? pill.theme.regen : pill.theme.text
        font { family: pill.theme.font; pixelSize: 40; weight: Font.Light }
    }

    Item {
        id: bar
        anchors { left: parent.left; right: figure.left; rightMargin: 16; verticalCenter: parent.verticalCenter }
        height: 8
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
        Rectangle { x: bar.zeroX - 1; y: -6; width: 2; height: parent.height + 12; color: pill.theme.textDim }
    }
}
