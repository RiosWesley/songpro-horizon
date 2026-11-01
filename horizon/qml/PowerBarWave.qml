import QtQuick 2.15

// Power gauge "Faixa e onda" (style "wave"): the figure, a bar across that leaves zero to the right
// (power, amber) or to the left (regeneration, green), and the last minute as a soft wave under it.
Item {
    id: gauge
    property HorizonTheme theme
    property HorizonData car
    property alias historySeed: wave.seed

    readonly property real maxPower: 150
    readonly property real maxRegen: 60
    readonly property real v: Math.max(-maxRegen, Math.min(maxPower, car.powerKw))
    readonly property bool regenerating: v < -0.5

    Text {
        id: figure
        x: 0; y: 6
        text: Math.round(Math.abs(gauge.v))
        color: gauge.regenerating ? gauge.theme.regen : gauge.theme.text
        font { family: gauge.theme.font; pixelSize: 112; weight: Font.Light }
    }
    Column {
        anchors { left: figure.right; leftMargin: 14; bottom: figure.baseline }
        Text {
            text: gauge.regenerating ? "regenerando" : gauge.v > 0.5 ? "tração" : "em repouso"
            color: gauge.regenerating ? gauge.theme.regen : gauge.theme.textDim
            font { family: gauge.theme.font; pixelSize: 25 }
        }
        Text {
            text: "kW"
            color: gauge.theme.textDim
            font { family: gauge.theme.font; pixelSize: 34 }
        }
    }

    // The bar: zero two sevenths in, so each side keeps its own scale.
    Item {
        id: bar
        anchors { left: parent.left; right: parent.right }
        y: 150; height: 12
        readonly property real zeroX: width * gauge.maxRegen / (gauge.maxPower + gauge.maxRegen)
        readonly property real tipX: gauge.v >= 0 ? zeroX + gauge.v / gauge.maxPower * (width - zeroX)
                                                  : zeroX - (-gauge.v / gauge.maxRegen) * zeroX
        Rectangle { anchors.fill: parent; radius: height / 2; color: gauge.theme.track }
        Rectangle {
            visible: Math.abs(gauge.v) > 0.5
            x: Math.min(bar.zeroX, bar.tipX); width: Math.abs(bar.tipX - bar.zeroX)
            height: parent.height; radius: height / 2
            gradient: Gradient {
                orientation: Gradient.Horizontal
                GradientStop { position: 0; color: gauge.regenerating ? gauge.theme.regen : Qt.darker(gauge.theme.power, 1.6) }
                GradientStop { position: 1; color: gauge.regenerating ? Qt.darker(gauge.theme.regen, 1.6) : gauge.theme.power }
            }
        }
        Rectangle {
            width: 40; height: 40; radius: 20
            x: bar.tipX - width / 2; y: (parent.height - height) / 2
            color: gauge.regenerating ? gauge.theme.regen : gauge.theme.power
            opacity: Math.abs(gauge.v) > 0.5 ? 0.25 : 0
        }
        Rectangle { x: bar.zeroX - 1.5; y: -8; width: 3; height: parent.height + 16; radius: 1.5; color: gauge.theme.textDim }
        Text {
            anchors { top: parent.bottom; topMargin: 10; horizontalCenter: parent.left }
            text: "-" + gauge.maxRegen
            color: gauge.theme.textFaint
            font { family: gauge.theme.font; pixelSize: 20 }
        }
        Text {
            anchors { top: parent.bottom; topMargin: 10; right: parent.right }
            text: gauge.maxPower
            color: gauge.theme.textFaint
            font { family: gauge.theme.font; pixelSize: 20 }
        }
    }

    PowerHistory {
        id: wave
        theme: gauge.theme
        value: gauge.car.powerKw
        anchors { left: parent.left; right: parent.right }
        y: 214; height: 104
    }

}
