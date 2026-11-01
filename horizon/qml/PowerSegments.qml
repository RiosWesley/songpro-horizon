import QtQuick 2.15

// Power gauge "Segmentos" (style "segments"): a dial of separate light segments, like an equaliser
// bent into an arc. Segments light up from zero towards the value: amber for power, green for
// regeneration, brighter at the tip.
Item {
    id: gauge
    property HorizonTheme theme
    property HorizonData car

    readonly property real maxPower: 150
    readonly property real maxRegen: 60
    readonly property real v: Math.max(-maxRegen, Math.min(maxPower, car.powerKw))
    readonly property bool regenerating: v < -0.5

    Item {
        id: dial
        width: 330; height: 300
        anchors.horizontalCenter: parent.horizontalCenter
        readonly property real cx: width / 2
        readonly property real cy: 168
        readonly property real r: 142
        readonly property int count: 29
        // 240° from bottom-left to bottom-right; segment `zeroIndex` is zero.
        readonly property real startDeg: 150
        readonly property real sweepDeg: 240
        readonly property int zeroIndex: Math.round((count - 1) * gauge.maxRegen / (gauge.maxPower + gauge.maxRegen))
        readonly property real tipIndex: gauge.v >= 0 ? zeroIndex + gauge.v / gauge.maxPower * (count - 1 - zeroIndex)
                                                      : zeroIndex - (-gauge.v / gauge.maxRegen) * zeroIndex

        Repeater {
            model: dial.count
            Rectangle {
                readonly property real deg: dial.startDeg + dial.sweepDeg * index / (dial.count - 1)
                readonly property real rad: deg * Math.PI / 180
                readonly property bool lit: Math.abs(gauge.v) > 0.5
                    && (gauge.v > 0 ? index > dial.zeroIndex && index <= dial.tipIndex + 0.5
                                     : index < dial.zeroIndex && index >= dial.tipIndex - 0.5)
                readonly property real nearTip: 1 - Math.min(1, Math.abs(index - dial.tipIndex) / 6)
                width: index === dial.zeroIndex ? 6 : 11
                height: index === dial.zeroIndex ? 40 : 30
                radius: 3
                x: dial.cx + dial.r * Math.cos(rad) - width / 2
                y: dial.cy + dial.r * Math.sin(rad) - height / 2
                rotation: deg + 90
                color: index === dial.zeroIndex ? gauge.theme.textDim
                     : lit ? (gauge.regenerating ? gauge.theme.regen : gauge.theme.power) : gauge.theme.track
                opacity: lit ? 0.45 + 0.55 * nearTip : 1
            }
        }
    }
    Text {
        id: figure
        anchors { horizontalCenter: dial.horizontalCenter; verticalCenter: dial.top; verticalCenterOffset: dial.cy - 14 }
        text: Math.round(Math.abs(gauge.v))
        color: gauge.regenerating ? gauge.theme.regen : gauge.theme.text
        font { family: gauge.theme.font; pixelSize: 104; weight: Font.Light }
    }
    Text {
        anchors { horizontalCenter: figure.horizontalCenter; top: figure.baseline; topMargin: 12 }
        text: gauge.regenerating ? "kW  regenerando" : "kW"
        color: gauge.regenerating ? gauge.theme.regen : gauge.theme.textDim
        font { family: gauge.theme.font; pixelSize: 28 }
    }

}
