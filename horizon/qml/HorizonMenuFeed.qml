import QtQuick 2.15

// The steering wheel menu, written by the app (byd/wheel/WheelMenuService) to menu.json next to
// now_playing.json:
//   { "seq": n, "open": bool, "selected": i, "items": [{ "label", "value" }],
//     "leftCard": "auto"|"music"|"trip", "powerStyle": "ring"|"segments"|"arc"|"column"|"wave" }
// The app closes the menu itself after 10 s without a key; a menu left open longer than that (the
// app stopped) is treated as closed. Without the file the menu stays closed, the card automatic
// and the power gauge the ring.
Item {
    id: feed
    property url directory
    property int pollMs: 150
    property int staleMs: 15000

    property bool open: false
    property int selected: 0
    property var items: []
    property string leftCard: "auto"
    property string powerStyle: "ring"

    property int _seq: -1
    property bool _open: false
    property real _changedAt: 0

    function apply(json) {
        var d = JSON.parse(json)
        if (d.seq === _seq)
            return
        _seq = d.seq
        _changedAt = Date.now()
        _open = d.open === true
        selected = d.selected || 0
        items = d.items || []
        leftCard = d.leftCard || "auto"
        powerStyle = d.powerStyle || "ring"
    }

    function poll() {
        var xhr = new XMLHttpRequest()
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== XMLHttpRequest.DONE)
                return
            if (xhr.responseText.length > 0) {
                try { feed.apply(xhr.responseText) } catch (e) { /* half-written file: next poll */ }
            }
            feed.open = feed._open && Date.now() - feed._changedAt < feed.staleMs
        }
        xhr.open("GET", directory + "menu.json")
        xhr.send()
    }

    Timer {
        interval: feed.pollMs; running: true; repeat: true; triggeredOnStart: true
        onTriggered: feed.poll()
    }
}
