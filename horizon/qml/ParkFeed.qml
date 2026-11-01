import QtQuick 2.15

// The car's parking sensors, written by the Song Pro Horizon app's reader (horizon/tools/park-feed)
// to park.json next to now_playing.json:
//   { "seq": n, "dist": [9 x 0..155 cm, 150 = nothing], "state": [9 x 1 clear .. 4 closest] }
// Zones: front left, front middle left, front middle right, front right, rear left corner, rear
// middle left, rear middle right, rear right corner, rear middle. The reader writes at least once a
// second while a zone sees something, so a file that stops advancing is a reader that stopped.
Item {
    id: feed
    property url directory
    property int pollMs: 300
    property int staleMs: 2500

    property var distances: []
    property var levels: []
    // Some zone sees an obstacle, and the reader is alive.
    property bool active: false
    // The closest distance a zone reports, in cm (0 when none).
    property int nearest: 0

    property int _seq: -1
    property real _lastAdvance: 0

    function apply(json) {
        var d = JSON.parse(json)
        if (d.seq !== _seq) {
            _seq = d.seq
            _lastAdvance = Date.now()
        }
        distances = d.dist || []
        levels = d.state || []
        var near = 0
        for (var i = 0; i < levels.length; i++)
            if (levels[i] >= 2 && (near === 0 || distances[i] < near)) near = distances[i]
        nearest = near
    }

    function poll() {
        var xhr = new XMLHttpRequest()
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== XMLHttpRequest.DONE)
                return
            if (xhr.responseText.length > 0) {
                try { feed.apply(xhr.responseText) } catch (e) { /* half-written file: next poll */ }
            }
            feed.active = feed.nearest > 0 && Date.now() - feed._lastAdvance < feed.staleMs
        }
        xhr.open("GET", directory + "park.json")
        xhr.send()
    }

    Timer {
        interval: feed.pollMs; running: true; repeat: true; triggeredOnStart: true
        onTriggered: feed.poll()
    }
}
