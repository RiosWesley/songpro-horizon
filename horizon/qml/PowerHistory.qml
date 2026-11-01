import QtQuick 2.15

// The last minute of power, sampled once a second: amber area above zero (drawing), green below
// (regenerating), newest on the right.
Canvas {
    id: chart
    property HorizonTheme theme
    property real value: 0
    property int samples: 60
    property real maxPower: 150
    property real maxRegen: 60
    // Seeds the chart (the offline mock passes a minute of made-up driving).
    property var seed: []
    property var _history: []

    Component.onCompleted: {
        _history = seed.length > 0 ? seed.slice(-samples) : []
        requestPaint()
    }
    Timer {
        interval: 1000; running: true; repeat: true
        onTriggered: {
            var h = chart._history
            h.push(chart.value)
            if (h.length > chart.samples) h.shift()
            chart._history = h
            chart.requestPaint()
        }
    }
    Connections { target: chart.theme; function onNightChanged() { chart.requestPaint() } }

    onPaint: {
        var ctx = getContext("2d")
        ctx.reset()
        var h = _history
        // The scale follows the last minute (with a floor), so city driving still has a shape.
        var top = 40, bottom = 15
        for (var k = 0; k < h.length; ++k) {
            top = Math.max(top, h[k] * 1.15)
            bottom = Math.max(bottom, -h[k] * 1.15)
        }
        var zeroY = height * top / (top + bottom)
        var step = width / (samples - 1)
        var x0 = width - (h.length - 1) * step

        ctx.lineWidth = 2
        ctx.strokeStyle = theme.track
        ctx.beginPath(); ctx.moveTo(0, zeroY); ctx.lineTo(width, zeroY); ctx.stroke()
        if (h.length < 2) return

        function yOf(v) {
            return v >= 0 ? zeroY - v / top * zeroY : zeroY + (-v / bottom) * (height - zeroY)
        }
        function fill(colour, above) {
            ctx.save()
            ctx.beginPath()
            ctx.rect(0, above ? 0 : zeroY, width, above ? zeroY : height - zeroY)
            ctx.clip()
            var g = ctx.createLinearGradient(0, above ? 0 : height, 0, zeroY)
            g.addColorStop(0, Qt.rgba(colour.r, colour.g, colour.b, 0.45))
            g.addColorStop(1, Qt.rgba(colour.r, colour.g, colour.b, 0.04))
            ctx.fillStyle = g
            ctx.beginPath()
            ctx.moveTo(x0, zeroY)
            for (var i = 0; i < h.length; ++i) ctx.lineTo(x0 + i * step, yOf(h[i]))
            ctx.lineTo(x0 + (h.length - 1) * step, zeroY)
            ctx.closePath()
            ctx.fill()
            ctx.strokeStyle = colour
            ctx.lineWidth = 3.5
            ctx.lineJoin = "round"
            ctx.beginPath()
            for (var j = 0; j < h.length; ++j) {
                if (j === 0) ctx.moveTo(x0, yOf(h[0])); else ctx.lineTo(x0 + j * step, yOf(h[j]))
            }
            ctx.stroke()
            ctx.restore()
        }
        fill(theme.power, true)
        fill(theme.regen, false)
    }
}
