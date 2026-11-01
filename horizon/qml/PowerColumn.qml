import QtQuick 2.15

// Power gauge "Coluna" (style "column"): a vertical power column on the right, zero marked; power
// rises amber, regeneration sinks green. The figure stands beside it; the last 50 km underneath.
Item {
    id: gauge
    property HorizonTheme theme
    property HorizonData car

    readonly property real maxPower: 150
    readonly property real maxRegen: 60
    readonly property real v: Math.max(-maxRegen, Math.min(maxPower, car.powerKw))
    readonly property bool regenerating: v < -0.5
    readonly property color tint: regenerating ? theme.regen : theme.power

    Text {
        x: 6; y: 8
        text: "POTÊNCIA"
        color: gauge.theme.textFaint
        font { family: gauge.theme.font; pixelSize: 23; letterSpacing: 4 }
    }
    Text {
        id: figure
        x: 0; y: 54
        text: Math.round(Math.abs(gauge.v))
        color: gauge.regenerating ? gauge.theme.regen : gauge.theme.text
        font { family: gauge.theme.font; pixelSize: 121; weight: Font.Light }
    }
    Text {
        anchors { left: figure.right; leftMargin: 10; baseline: figure.baseline }
        text: "kW"
        color: gauge.theme.textDim
        font { family: gauge.theme.font; pixelSize: 34 }
    }
    Text {
        x: 6; anchors.top: figure.bottom
        text: gauge.regenerating ? "regenerando" : gauge.v > 0.5 ? "tração" : "em repouso"
        color: gauge.regenerating ? gauge.theme.regen : gauge.theme.textDim
        font { family: gauge.theme.font; pixelSize: 28 }
    }

    // The column.
    Item {
        id: column
        x: parent.width - 64; y: 40
        width: 26; height: 290
        readonly property real zeroY: height * gauge.maxPower / (gauge.maxPower + gauge.maxRegen)
        readonly property real tipY: gauge.v >= 0 ? zeroY - gauge.v / gauge.maxPower * zeroY
                                                  : zeroY + (-gauge.v / gauge.maxRegen) * (height - zeroY)
        Rectangle {
            anchors.fill: parent
            radius: width / 2
            color: gauge.theme.track
        }
        Rectangle {
            x: 0; width: parent.width; radius: width / 2
            y: Math.min(column.tipY, column.zeroY) - (gauge.v >= 0 ? 0 : width / 2)
            height: Math.abs(column.zeroY - column.tipY) + width / 2
            visible: Math.abs(gauge.v) > 0.5
            gradient: Gradient {
                GradientStop { position: 0; color: gauge.regenerating ? Qt.darker(gauge.theme.regen, 1.6) : gauge.theme.power }
                GradientStop { position: 1; color: gauge.regenerating ? gauge.theme.regen : Qt.darker(gauge.theme.power, 1.7) }
            }
        }
        // Zero.
        Rectangle {
            x: -10; width: parent.width + 20; height: 3; radius: 1.5
            y: column.zeroY - 1.5
            color: gauge.theme.textDim
        }
        // A halo where the value ends.
        Rectangle {
            width: 46; height: 46; radius: 23
            x: (parent.width - width) / 2; y: column.tipY - height / 2
            color: gauge.tint
            opacity: Math.abs(gauge.v) > 0.5 ? 0.28 : 0
        }
        Text {
            anchors { right: parent.left; rightMargin: 14; verticalCenter: parent.top }
            text: gauge.maxPower
            color: gauge.theme.textFaint
            font { family: gauge.theme.font; pixelSize: 21 }
        }
        Text {
            anchors { right: parent.left; rightMargin: 14; verticalCenter: parent.bottom }
            text: "-" + gauge.maxRegen
            color: gauge.theme.textFaint
            font { family: gauge.theme.font; pixelSize: 21 }
        }
    }

}
