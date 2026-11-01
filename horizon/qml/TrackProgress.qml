import QtQuick 2.15

// A hairline of how far into the track, with the times under it when `showTimes`.
Item {
    id: progress
    property HorizonTheme theme
    property real positionMs: 0
    property real durationMs: 0
    property bool showTimes: false
    height: showTimes ? 34 : 5
    visible: durationMs > 0

    function clock(ms) {
        var s = Math.max(0, Math.floor(ms / 1000))
        var m = Math.floor(s / 60)
        s = s % 60
        return m + ":" + (s < 10 ? "0" : "") + s
    }

    Rectangle {
        anchors { left: parent.left; right: parent.right; top: parent.top }
        height: 5; radius: 2.5
        color: progress.theme.track
        Rectangle {
            width: progress.durationMs > 0 ? parent.width * Math.min(1, progress.positionMs / progress.durationMs) : 0
            height: parent.height; radius: 2.5
            color: progress.theme.textDim
        }
    }
    Text {
        visible: progress.showTimes
        anchors { left: parent.left; bottom: parent.bottom }
        text: progress.clock(progress.positionMs)
        color: progress.theme.textFaint
        font { family: progress.theme.font; pixelSize: 23 }
    }
    Text {
        visible: progress.showTimes
        anchors { right: parent.right; bottom: parent.bottom }
        text: progress.clock(progress.durationMs)
        color: progress.theme.textFaint
        font { family: progress.theme.font; pixelSize: 23 }
    }
}
