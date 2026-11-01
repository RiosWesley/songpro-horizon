import QtQuick 2.15

// Small line icons for the bottom row: a battery or a fuel pump, in the given colour.
Canvas {
    id: glyph
    property string kind: "battery"
    property color color: "white"
    width: 30; height: 22

    onColorChanged: requestPaint()
    onPaint: {
        var ctx = getContext("2d")
        ctx.reset()
        ctx.strokeStyle = color
        ctx.fillStyle = color
        ctx.lineWidth = 2.5
        if (kind === "battery") {
            ctx.beginPath(); ctx.roundedRect(1, 3, 24, 16, 3, 3); ctx.stroke()
            ctx.fillRect(26, 8, 3, 6)
        } else {
            // Pump body, window, hose.
            ctx.beginPath(); ctx.roundedRect(3, 2, 13, 19, 2, 2); ctx.stroke()
            ctx.fillRect(6, 5, 7, 5)
            ctx.beginPath()
            ctx.moveTo(16, 8); ctx.lineTo(21, 8); ctx.lineTo(23, 11); ctx.lineTo(23, 18)
            ctx.quadraticCurveTo(23, 21, 20, 20)
            ctx.stroke()
        }
    }
}
