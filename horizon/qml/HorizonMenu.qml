import QtQuick 2.15

// The steering wheel menu, in the left card's place while it is open: each setting as its name
// over its value, the selected one lit. ◁ ▷ move the light and the roller press changes the value
// (the app does both; this only draws what menu.json says). Where every row does not fit (the
// short card of map mode) only the selected one shows, with where it is in the list.
Item {
    id: menu
    property HorizonTheme theme
    property HorizonMenuFeed feed
    readonly property int count: feed.items.length
    readonly property real space: height - header.height
    readonly property bool compact: space < count * 76
    readonly property real rowHeight: compact ? space : Math.min(104, space / Math.max(1, count))

    Text {
        id: header
        height: 44
        text: "HORIZON"
        color: menu.theme.textDim
        font { family: menu.theme.font; pixelSize: 25; letterSpacing: 4 }
    }
    Text {
        visible: menu.compact && menu.count > 0
        anchors { right: parent.right; rightMargin: 18; top: parent.top }
        text: (menu.feed.selected + 1) + " / " + menu.count
        color: menu.theme.textDim
        font { family: menu.theme.font; pixelSize: 25 }
    }

    Column {
        anchors { left: parent.left; right: parent.right; top: header.bottom }

        Repeater {
            model: menu.feed.items
            Item {
                readonly property bool current: index === menu.feed.selected
                visible: !menu.compact || current
                width: menu.width
                height: visible ? menu.rowHeight : 0

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
                    width: 4; height: parent.height * 0.5; radius: 2
                    color: menu.theme.accent
                }
                Column {
                    anchors { left: parent.left; leftMargin: 30; right: parent.right; rightMargin: 18; verticalCenter: parent.verticalCenter }
                    Text {
                        width: parent.width
                        elide: Text.ElideRight
                        text: modelData.label
                        color: menu.theme.textDim
                        font { family: menu.theme.font; pixelSize: 23 }
                    }
                    Text {
                        width: parent.width
                        elide: Text.ElideRight
                        text: modelData.value
                        color: parent.parent.current ? menu.theme.accent : menu.theme.text
                        font { family: menu.theme.fontBold; pixelSize: 32 }
                    }
                }
            }
        }
    }
}
