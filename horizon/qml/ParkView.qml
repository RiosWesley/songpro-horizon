import QtQuick 2.15

// The parking sensors around the car, seen from above with the nose up, while some zone sees an
// obstacle: three bands per zone (the farthest lights green, then amber, the nearest red), four
// zones across the front bumper and five across the rear (ParkFeed's order), and the closest
// distance in cm beside them. It takes the ADAS road's place; the road dims behind it.
Item {
    id: view
    property HorizonTheme theme
    property ParkFeed feed

    // The car, sized from the space it has (the full road or a small window).
    readonly property real carHeight: Math.min(height * 0.44, width * 0.55)
    readonly property real carWidth: carHeight * 0.46
    readonly property real cx: width / 2
    readonly property real cy: height * 0.45

    // Zone index -> where its band sits, as an angle on the bumper's arc (degrees, 0 = right,
    // 90 = down). Front: left to right above the car; rear: left to right below it.
    readonly property var front: [[0, 234], [1, 258], [2, 282], [3, 306]]
    readonly property var rear: [[4, 138], [5, 114], [8, 90], [6, 66], [7, 42]]

    function levelColor(level) {
        return level >= 4 ? theme.alert : level === 3 ? theme.power : theme.regen
    }

    Canvas {
        id: bands
        anchors.fill: parent
        onPaint: {
            var ctx = getContext("2d")
            ctx.reset()
            var levels = view.feed.levels
            var unit = view.carHeight
            // The bands, nearest first, around each bumper's middle.
            var radii = [unit * 0.36, unit * 0.47, unit * 0.58]
            var thick = unit * 0.075
            var half = 10 * Math.PI / 180
            function draw(zones, oy) {
                for (var z = 0; z < zones.length; z++) {
                    var level = levels.length > zones[z][0] ? levels[zones[z][0]] : 1
                    var a = zones[z][1] * Math.PI / 180
                    for (var b = 0; b < 3; b++) {
                        // Band 0 is the nearest: lit at level 4, band 1 at 3, band 2 at 2.
                        var lit = level >= 2 && b === 4 - level
                        ctx.strokeStyle = lit ? view.levelColor(level) : view.theme.track
                        ctx.lineWidth = thick
                        ctx.beginPath()
                        ctx.arc(view.cx, oy, radii[b], a - half, a + half, false)
                        ctx.stroke()
                    }
                }
            }
            draw(view.front, view.cy - view.carHeight * 0.3)
            draw(view.rear, view.cy + view.carHeight * 0.3)
        }
        Connections {
            target: view.feed
            function onLevelsChanged() { bands.requestPaint() }
        }
        Connections {
            target: view.theme
            function onNightChanged() { bands.requestPaint() }
        }
        onWidthChanged: requestPaint()
        onHeightChanged: requestPaint()
    }

    // The car from above: body, glass, and the nose up.
    Rectangle {
        id: body
        width: view.carWidth; height: view.carHeight
        x: view.cx - width / 2; y: view.cy - height / 2
        radius: width * 0.32
        color: view.theme.text
        opacity: 0.9
        Rectangle {
            anchors { horizontalCenter: parent.horizontalCenter; top: parent.top; topMargin: parent.height * 0.22 }
            width: parent.width * 0.78; height: parent.height * 0.16
            radius: height * 0.35
            color: view.theme.skyTop
            opacity: 0.85
        }
        Rectangle {
            anchors { horizontalCenter: parent.horizontalCenter; bottom: parent.bottom; bottomMargin: parent.height * 0.12 }
            width: parent.width * 0.72; height: parent.height * 0.1
            radius: height * 0.35
            color: view.theme.skyTop
            opacity: 0.85
        }
    }

    // Beside the rear bands, clear of the READY lamp below the road.
    Text {
        x: view.cx + view.carHeight * 0.7
        y: view.cy + view.carHeight * 0.32
        visible: view.feed.nearest > 0
        text: view.feed.nearest + " cm"
        color: view.theme.text
        font { family: view.theme.font; pixelSize: Math.max(22, view.carHeight * 0.2); weight: Font.Light }
    }
}
