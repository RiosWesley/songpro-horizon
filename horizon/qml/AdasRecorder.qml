import QtQuick 2.15
import com.byd.DataSource 1.0

// Drive recorder for the ADAS study. The head unit never receives the ADAS frames (they go to the
// cluster only), so the cluster records what it gets itself: 4 times a second, the values
// DiagOverlay shows plus speed, gear and the ACC/AEB lights.
//
// Off unless adas-rec.json, written over adb next to menu.json, says so:
//   { "on": true, "id": "run name", "urls": ["http://127.0.0.1:8765/lo", ...] }
// Every 5 s the lines go out of the cluster each way that may work here (none is known to): a PUT
// of a chunk file next to the command file, and a POST to each listed URL (adas-probe `listen` on
// the head unit). Removing the file or "on": false stops it. Invisible; nothing else depends on it.
Item {
    id: rec
    property url directory
    property int pollMs: 1000
    property int sampleMs: 250
    property int flushMs: 5000

    property bool on: false
    property string runId: "run"
    property var urls: []

    property string buffer: ""
    property int chunk: 0
    property real startedAt: 0

    readonly property var targets: [
        ["F1", "frontTargetOne"], ["F2", "frontTargetTwo"], ["LF", "leftFrontTarget"],
        ["RF", "rightFrontTarget"], ["LR", "leftRearTarget"], ["RR", "rightRearTarget"], ["R", "rearTarget"]
    ]
    readonly property var fields: [
        ["v", "speed"], ["gear", "gearMode"], ["accL", "accIndicatorLight"], ["accS", "accSpeedStatus"],
        ["accTxt", "accRemoteText"], ["aebL", "aebIndicatorLight"], ["main", "mainTargetIndex"],
        ["ahead", "aheadDistance"], ["lvl", "carDistanceLevel"], ["show", "adasShowMode"], ["type", "adasType"],
        ["laneD", "carToLeftLaneDistance"], ["Lt", "leftLineType"], ["Rt", "rightLineType"],
        ["w", "roadWidth"], ["rad", "laneRadius"]
    ]

    function num(v) {
        if (v === undefined || v === null) return "-"
        if (typeof v !== "number") return String(v).replace(/\s+/g, "_")
        return Math.abs(v - Math.round(v)) < 0.001 ? String(Math.round(v)) : v.toFixed(2)
    }

    function header() {
        var h = "# horizon adas-rec " + runId + " started " + new Date(startedAt).toISOString() + "\n# t_ms"
        for (var i = 0; i < fields.length; i++) h += " " + fields[i][0]
        for (var j = 0; j < targets.length; j++) h += " " + targets[j][0] + "st " + targets[j][0] + "x " + targets[j][0] + "y"
        return h + "\n"
    }

    function sample() {
        var line = String(Date.now() - startedAt)
        for (var i = 0; i < fields.length; i++) line += " " + num(DataSource[fields[i][1]])
        for (var j = 0; j < targets.length; j++) {
            var t = targets[j][1]
            line += " " + num(DataSource[t + "State"]) + " " + num(DataSource[t + "PosX"]) + " " + num(DataSource[t + "PosY"])
        }
        buffer += line + "\n"
    }

    function send(method, url, body) {
        try {
            var xhr = new XMLHttpRequest()
            xhr.open(method, url)
            xhr.send(body)
        } catch (e) { /* this way does not work here; the others may */ }
    }

    function flush() {
        if (buffer.length === 0) return
        var body = buffer
        buffer = ""
        var name = "adas-rec-" + runId + "-" + ("0000" + chunk++).slice(-4) + ".txt"
        send("PUT", directory + name, body)
        for (var i = 0; i < urls.length; i++) send("POST", urls[i] + "/" + name, body)
    }

    function apply(json) {
        var d = JSON.parse(json)
        var wanted = d.on === true
        if (wanted === on) return
        if (wanted) {
            runId = String(d.id || "run").replace(/[^A-Za-z0-9_-]/g, "")
            urls = d.urls || []
            chunk = 0
            startedAt = Date.now()
            buffer = header()
            on = true
        } else {
            stop()
        }
    }

    function stop() {
        if (!on) return
        on = false
        flush()
    }

    function poll() {
        var xhr = new XMLHttpRequest()
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== XMLHttpRequest.DONE)
                return
            if (xhr.responseText.length === 0) {
                rec.stop()
                return
            }
            try { rec.apply(xhr.responseText) } catch (e) { /* half-written file: next poll */ }
        }
        xhr.open("GET", directory + "adas-rec.json")
        xhr.send()
    }

    Timer { interval: rec.pollMs; running: true; repeat: true; triggeredOnStart: true; onTriggered: rec.poll() }
    Timer { interval: rec.sampleMs; running: rec.on; repeat: true; onTriggered: rec.sample() }
    Timer { interval: rec.flushMs; running: rec.on; repeat: true; onTriggered: rec.flush() }
}
