import QtQuick 2.15

// Power gauge "Arco lateral" (style "arc"): one long arc down the right side, curved like the
// horizon's dome. Zero sits where the arc is closest to the figure; power climbs it amber,
// regeneration runs down it green.
Item {
    id: gauge
    property HorizonTheme theme
    property HorizonData car

    readonly property real maxPower: 150
    readonly property real maxRegen: 60
    readonly property real v: Math.max(-maxRegen, Math.min(maxPower, car.powerKw))
    readonly property bool regenerating: v < -0.5

    Canvas {
        id: arc
        anchors.fill: parent
        // The circle's centre is far to the left, so only its right edge shows: a ")" down the side.
        readonly property real cx: -250
        readonly property real cy: 205
        readonly property real r: 590
        readonly property real aTop: -0.29      // radians: 150 kW
        readonly property real aZero: 0.0
        readonly property real aBottom: 0.2      // -60 kW
        function angleOf(kw) { return kw >= 0 ? aZero + (aTop - aZero) * kw / gauge.maxPower
                                              : aZero + (aBottom - aZero) * -kw / gauge.maxRegen }
        onPaint: {
            var ctx = getContext("2d")
            ctx.reset()
            ctx.lineCap = "round"
            ctx.lineWidth = 16
            ctx.strokeStyle = gauge.theme.track
            ctx.beginPath(); ctx.arc(cx, cy, r, aTop, aBottom, false); ctx.stroke()
            var end = angleOf(gauge.v)
            if (Math.abs(gauge.v) > 0.5) {
                var c = gauge.regenerating ? gauge.theme.regen : gauge.theme.power
                ctx.strokeStyle = c
                ctx.shadowColor = c
                ctx.shadowBlur = 18
                ctx.beginPath(); ctx.arc(cx, cy, r, Math.min(aZero, end), Math.max(aZero, end), false); ctx.stroke()
                ctx.shadowBlur = 0
            }
            // Zero, and the ends of the scale.
            ctx.fillStyle = gauge.theme.textFaint
            ctx.font = "21px '" + gauge.theme.font + "'"
            ctx.textAlign = "right"
            ctx.textBaseline = "middle"
            var marks = [{ kw: gauge.maxPower, t: String(gauge.maxPower) }, { kw: 0, t: "0" }, { kw: -gauge.maxRegen, t: "-" + gauge.maxRegen }]
            for (var i = 0; i < marks.length; ++i) {
                var a = angleOf(marks[i].kw)
                ctx.fillText(marks[i].t, cx + (r - 26) * Math.cos(a), cy + (r - 26) * Math.sin(a))
            }
            ctx.strokeStyle = gauge.theme.textDim
            ctx.lineWidth = 3
            ctx.beginPath()
            ctx.moveTo(cx + (r - 14) * Math.cos(aZero), cy + (r - 14) * Math.sin(aZero))
            ctx.lineTo(cx + (r + 14) * Math.cos(aZero), cy + (r + 14) * Math.sin(aZero))
            ctx.stroke()
        }
        Connections { target: gauge; function onVChanged() { arc.requestPaint() } }
        Connections { target: gauge.theme; function onNightChanged() { arc.requestPaint() } }
    }

    Text {
        id: figure
        x: 10; y: 96
        text: Math.round(Math.abs(gauge.v))
        color: gauge.regenerating ? gauge.theme.regen : gauge.theme.text
        font { family: gauge.theme.font; pixelSize: 130; weight: Font.Light }
    }
    Text {
        anchors { left: figure.left; leftMargin: 4; top: figure.bottom; topMargin: -6 }
        text: gauge.regenerating ? "kW  regenerando" : "kW"
        color: gauge.regenerating ? gauge.theme.regen : gauge.theme.textDim
        font { family: gauge.theme.font; pixelSize: 30 }
    }

}
