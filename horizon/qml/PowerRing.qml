import QtQuick 2.15

// Power gauge "Anel" (style "ring"): a clean ring with no numbers on it, zero at the top: power
// runs clockwise in amber, regeneration anticlockwise in green. The figure sits inside.
Item {
    id: gauge
    property HorizonTheme theme
    property HorizonData car

    readonly property real maxPower: 150
    readonly property real maxRegen: 60
    readonly property real v: Math.max(-maxRegen, Math.min(maxPower, car.powerKw))
    readonly property bool regenerating: v < -0.5

    Canvas {
        id: ring
        width: 300; height: 300
        anchors.horizontalCenter: parent.horizontalCenter
        y: 0
        readonly property real zero: -Math.PI / 2
        // 300° in all: the power side gets its share of the sweep by kW, regeneration the rest.
        readonly property real sweep: Math.PI * 5 / 3
        readonly property real powerSweep: sweep * gauge.maxPower / (gauge.maxPower + gauge.maxRegen)
        readonly property real regenSweep: sweep - powerSweep
        onPaint: {
            var ctx = getContext("2d")
            ctx.reset()
            var cx = width / 2, cy = height / 2, r = width / 2 - 24
            ctx.lineWidth = 16
            ctx.lineCap = "round"
            ctx.strokeStyle = gauge.theme.track
            ctx.beginPath(); ctx.arc(cx, cy, r, zero - regenSweep, zero + powerSweep, false); ctx.stroke()
            if (Math.abs(gauge.v) > 0.5) {
                var c = gauge.regenerating ? gauge.theme.regen : gauge.theme.power
                var end = gauge.v > 0 ? zero + powerSweep * gauge.v / gauge.maxPower
                                       : zero - regenSweep * -gauge.v / gauge.maxRegen
                ctx.strokeStyle = c
                ctx.shadowColor = c
                ctx.shadowBlur = 20
                ctx.beginPath(); ctx.arc(cx, cy, r, Math.min(zero, end), Math.max(zero, end), false); ctx.stroke()
                ctx.shadowBlur = 0
            }
            ctx.fillStyle = gauge.theme.textDim
            ctx.beginPath(); ctx.arc(cx, cy - r, 4, 0, Math.PI * 2); ctx.fill()
        }
        Connections { target: gauge; function onVChanged() { ring.requestPaint() } }
        Connections { target: gauge.theme; function onNightChanged() { ring.requestPaint() } }
    }
    Text {
        id: figure
        anchors { horizontalCenter: ring.horizontalCenter; verticalCenter: ring.verticalCenter; verticalCenterOffset: -10 }
        text: Math.round(Math.abs(gauge.v))
        color: gauge.regenerating ? gauge.theme.regen : gauge.theme.text
        font { family: gauge.theme.font; pixelSize: 99; weight: Font.Light }
    }
    Text {
        anchors { horizontalCenter: figure.horizontalCenter; top: figure.baseline; topMargin: 12 }
        text: gauge.regenerating ? "kW  regenerando" : "kW"
        color: gauge.regenerating ? gauge.theme.regen : gauge.theme.textDim
        font { family: gauge.theme.font; pixelSize: 28 }
    }

}
