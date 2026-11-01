import QtQuick 2.15

// Now playing, set straight on the backdrop: cover, title and artist, a hairline of progress and,
// when the feed carries them, the lyric lines. `compact` is the map-mode variant: one line under
// the title (the current lyric, or the artist).
Item {
    id: music
    property HorizonTheme theme
    property HorizonData car
    property bool compact: false

    readonly property bool hasLyrics: car.lyric.length > 0

    Text {
        visible: !music.car.mediaPresent
        anchors { left: parent.left; verticalCenter: parent.verticalCenter }
        text: "Sem mídia"
        color: music.theme.textFaint
        font { family: music.theme.font; pixelSize: 30 }
    }

    Column {
        visible: music.car.mediaPresent
        width: parent.width
        spacing: music.compact ? 0 : 34
        // Without lyrics the short block sits in the middle of the column instead of at the top.
        y: music.compact || music.hasLyrics ? 0 : (music.height - height) / 2

        Item {
            width: parent.width
            height: tile.height

            AlbumTile {
                id: tile
                theme: music.theme
                artUrl: music.car.artUrl
                width: music.compact ? 76 : (music.hasLyrics ? 112 : 148)
                height: width
            }
            Column {
                anchors { left: tile.right; leftMargin: 22; right: parent.right; verticalCenter: tile.verticalCenter }
                spacing: 6
                Text {
                    width: parent.width
                    text: music.car.title
                    color: music.theme.text
                    elide: Text.ElideRight
                    font { family: music.theme.font; pixelSize: music.compact ? 28 : 32; weight: Font.Medium }
                }
                Text {
                    width: parent.width
                    text: music.compact && music.hasLyrics ? music.car.lyric : music.car.artist
                    color: music.theme.textDim
                    elide: Text.ElideRight
                    font { family: music.theme.font; pixelSize: music.compact ? 24 : 25 }
                }
                Item { width: 1; height: music.compact ? 0 : 8 }
                TrackProgress {
                    visible: !music.compact && music.car.durationMs > 0
                    width: parent.width
                    theme: music.theme
                    positionMs: music.car.positionMs
                    durationMs: music.car.durationMs
                    showTimes: !music.hasLyrics
                }
            }
        }

        LyricsView {
            visible: !music.compact && music.hasLyrics
            width: parent.width
            theme: music.theme
            previous: music.car.prevLyric
            current: music.car.lyric
            next: music.car.nextLyric
        }
    }
}
