import QtQuick 2.15

// Synced lyrics: the line just sung receding, the current one, the next one waiting.
// The feed sends all three; the native media text has no lyrics, so nothing shows then.
Column {
    id: lyrics
    property HorizonTheme theme
    property string previous: ""
    property string current: ""
    property string next: ""
    spacing: 12

    onCurrentChanged: swap.restart()

    Text {
        width: parent.width
        text: lyrics.previous
        visible: text.length > 0
        color: lyrics.theme.textFaint
        elide: Text.ElideRight
        font { family: lyrics.theme.font; pixelSize: 24 }
    }
    Text {
        id: currentLine
        width: parent.width
        text: lyrics.current
        color: lyrics.theme.text
        wrapMode: Text.WordWrap
        maximumLineCount: 2
        elide: Text.ElideRight
        lineHeight: 1.08
        font { family: lyrics.theme.font; pixelSize: 34; weight: Font.Medium }
    }
    Text {
        width: parent.width
        text: lyrics.next
        visible: text.length > 0
        color: lyrics.theme.textDim
        elide: Text.ElideRight
        font { family: lyrics.theme.font; pixelSize: 25 }
    }

    NumberAnimation {
        id: swap
        target: currentLine; property: "opacity"; from: 0.25; to: 1
        duration: 260; easing.type: Easing.OutCubic
    }
}
