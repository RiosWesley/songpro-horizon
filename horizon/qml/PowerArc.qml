import QtQuick 2.15

// A 240° power gauge with zero at the top: regeneration fills to the left (green), power to the
// right (amber), each over its own scale. Numbered ticks, and a lit cap where the value ends.
Canvas {
    id: arc
    property HorizonTheme theme
    property real value: 0        // kW, negative when regenerating
    property real maxPower: 150
    property real maxRegen: 60
    property real lineWidth: 10
    property string font: theme.font

    readonly property real sweep: Math.PI * 4 / 3
    readonly property real start: Math.PI * 5 / 6             // bottom-left end
    readonly property real zero: start + sweep / 2            // top

    // Each scale mark: the angle and its label.
    readonly property var marks: [
        { f: -1.0, label: "-" + maxRegen }, { f: -0.5, label: "" }, { f: 0, label: "0" },
        { f: 1 / 3, label: String(Math.round(maxPower / 3)) }, { f: 2 / 3, label: String(Math.round(maxPower * 2 / 3)) },
        { f: 1.0, label: String(maxPower) }
    ]

    function angleOf(f) { return zero + f * sweep / 2 }

    onValueChanged: requestPaint()
    Connections { target: arc.theme; function onNightChanged() { arc.requestPaint() } }

    onPaint: {
        var ctx = getContext("2d")
        ctx.reset()
        var cx = width / 2, cy = height / 2, r = Math.min(width, height) / 2 - 56

        ctx.lineWidth = lineWidth
        ctx.lineCap = "round"
        ctx.strokeStyle = theme.track
        ctx.beginPath(); ctx.arc(cx, cy, r, start, start + sweep, false); ctx.stroke()
        ctx.lineCap = "butt"

        ctx.font = "18px '" + font + "'"
        ctx.textAlign = "center"
        ctx.textBaseline = "middle"
        for (var i = 0; i < marks.length; ++i) {
            var a = angleOf(marks[i].f)
            var major = marks[i].label.length > 0
            ctx.lineWidth = major ? 3.5 : 2.5
            ctx.strokeStyle = marks[i].f === 0 ? theme.textDim : theme.textFaint
            ctx.beginPath()
            ctx.moveTo(cx + (r + 10) * Math.cos(a), cy + (r + 10) * Math.sin(a))
            ctx.lineTo(cx + (r + (major ? 20 : 16)) * Math.cos(a), cy + (r + (major ? 20 : 16)) * Math.sin(a))
            ctx.stroke()
            if (major) {
                ctx.fillStyle = theme.textFaint
                ctx.fillText(marks[i].label, cx + (r + 34) * Math.cos(a), cy + (r + 34) * Math.sin(a))
            }
        }

        var v = Math.max(-maxRegen, Math.min(maxPower, value))
        var end = angleOf(v > 0 ? v / maxPower : v / maxRegen)
        var c = v > 0 ? theme.power : theme.regen
        if (Math.abs(v) > 0.5) {
            ctx.lineWidth = lineWidth
            ctx.lineCap = "round"
            ctx.strokeStyle = c
            ctx.beginPath(); ctx.arc(cx, cy, r, Math.min(zero, end), Math.max(zero, end), false); ctx.stroke()
        }
        // The cap: a soft halo and a bright dot at the value.
        var px = cx + r * Math.cos(end), py = cy + r * Math.sin(end)
        var halo = ctx.createRadialGradient(px, py, 0, px, py, 22)
        halo.addColorStop(0, Qt.rgba(c.r, c.g, c.b, Math.abs(v) > 0.5 ? 0.55 : 0.25))
        halo.addColorStop(1, Qt.rgba(c.r, c.g, c.b, 0))
        ctx.fillStyle = halo
        ctx.beginPath(); ctx.arc(px, py, 22, 0, Math.PI * 2); ctx.fill()
        ctx.fillStyle = Math.abs(v) > 0.5 ? c : theme.textDim
        ctx.beginPath(); ctx.arc(px, py, 8, 0, Math.PI * 2); ctx.fill()
    }
}
