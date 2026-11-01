import QtQuick 2.15

// The steering wheel menu, in the left card's place while it is open: one row per setting, the
// selected one lit. ◁ ▷ move the light and the roller press changes the value (the app does both;
// this only draws what menu.json says).
Item {
    id: menu
    property HorizonTheme theme
    property HorizonMenuFeed feed
    readonly property real rowHeight: Math.min(84, (height - header.height) / Math.max(1, feed.items.length))

    Text {
        id: header
        height: 44
        text: "HORIZON"
        color: menu.theme.textDim
        font { family: menu.theme.font; pixelSize: 22; letterSpacing: 4 }
    }

    Column {
        anchors { left: parent.left; right: parent.right; top: header.bottom }

        Repeater {
            model: menu.feed.items
            Item {
                readonly property bool current: index === menu.feed.selected
                width: menu.width
                height: menu.rowHeight

                Rectangle {
                    anchors { fill: parent; topMargin: 4; bottomMargin: 4 }
                    radius: 14
                    color: menu.theme.accent
                    opacity: parent.current ? 0.16 : 0
                    Behavior on opacity { NumberAnimation { duration: 120 } }
                }
                Rectangle {
                    visible: parent.current
                    anchors { left: parent.left; leftMargin: 12; verticalCenter: parent.verticalCenter }
                    width: 4; height: parent.height * 0.45; radius: 2
                    color: menu.theme.accent
                }
                Text {
                    anchors {
                        left: parent.left; leftMargin: 30
                        right: value.left; rightMargin: 20
                        verticalCenter: parent.verticalCenter
                    }
                    elide: Text.ElideRight
                    text: modelData.label
                    color: parent.current ? menu.theme.text : menu.theme.textDim
                    font { family: menu.theme.font; pixelSize: 24 }
                }
                Text {
                    id: value
                    anchors { right: parent.right; rightMargin: 18; verticalCenter: parent.verticalCenter }
                    text: modelData.value
                    color: parent.current ? menu.theme.accent : menu.theme.text
                    font { family: menu.theme.fontBold; pixelSize: 28 }
                }
            }
        }
    }
}
