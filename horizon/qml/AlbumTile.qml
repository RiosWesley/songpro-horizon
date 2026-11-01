import QtQuick 2.15
import QtGraphicalEffects 1.0

// The cover from the phone, rounded; a quiet tile with a note when there is none.
Item {
    id: tile
    property HorizonTheme theme
    property url artUrl: ""
    property real radius: 14
    readonly property bool hasArt: art.status === Image.Ready

    Rectangle {
        anchors.fill: parent
        radius: tile.radius
        color: tile.theme.track
        visible: !tile.hasArt
        Text {
            anchors.centerIn: parent
            text: "♪"
            color: tile.theme.textFaint
            font.pixelSize: tile.height * 0.36
        }
    }
    Image {
        id: art
        anchors.fill: parent
        source: tile.artUrl
        fillMode: Image.PreserveAspectCrop
        smooth: true
        visible: false
        cache: false
    }
    OpacityMask {
        anchors.fill: parent
        visible: tile.hasArt
        source: art
        maskSource: Rectangle { width: tile.width; height: tile.height; radius: tile.radius }
    }
}
