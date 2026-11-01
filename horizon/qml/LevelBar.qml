import QtQuick 2.15

// Battery or fuel: icon, a hairline gauge, the percentage and the range.
Item {
    id: bar
    property HorizonTheme theme
    property string kind: "battery"
    property int percent: -1
    property int rangeKm: -1
    property color fill: theme.text
    readonly property bool low: percent >= 0 && percent <= 15
    height: 40

    LevelGlyph {
        id: glyph
        anchors.verticalCenter: parent.verticalCenter
        kind: bar.kind
        color: bar.low ? bar.theme.alert : bar.theme.textDim
    }
    Rectangle {
        anchors { left: glyph.right; leftMargin: 14; right: pct.left; rightMargin: 16; verticalCenter: parent.verticalCenter }
        height: 8; radius: 4
        color: bar.theme.track
        Rectangle {
            width: bar.percent < 0 ? 0 : parent.width * bar.percent / 100
            height: parent.height; radius: 4
            color: bar.low ? bar.theme.alert : bar.fill
        }
    }
    Text {
        id: pct
        anchors { right: range.left; rightMargin: 14; verticalCenter: parent.verticalCenter }
        text: bar.percent < 0 ? "--" : bar.percent + "%"
        color: bar.theme.text
        font { family: bar.theme.font; pixelSize: 33 }
    }
    Text {
        id: range
        anchors { right: parent.right; verticalCenter: parent.verticalCenter }
        text: (bar.rangeKm < 0 ? "--" : bar.rangeKm) + " km"
        color: bar.theme.textDim
        font { family: bar.theme.font; pixelSize: 30 }
    }
}
