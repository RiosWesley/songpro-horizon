import QtQuick 2.15

// What is playing on the phone, written by open-headunit to the Android cell's
// /sdcard/.cluster/horizon/ (the host reaches it under /data/cells/cell2-rw/…, the same way it
// reads the translate RCC). now_playing.json:
//   { "seq": n, "title", "artist", "playing", "positionMs", "durationMs",
//     "art": "art_<id>.png" | "", "lyrics": { "prev", "current", "next" } | null }
// `seq` advances at least once a second while the writer runs; a feed that stops advancing is
// treated as gone, and Horizon falls back to the native media text.
Item {
    id: feed
    property url directory: "file:///data/cells/cell2-rw/data/media/0/.cluster/horizon/"
    property int pollMs: 500
    property int staleMs: 6000

    property bool alive: false
    property string title: ""
    property string artist: ""
    property bool playing: false
    property real positionMs: 0
    property real durationMs: 0
    property url artUrl: ""
    property bool lyricsOn: false
    property string prevLyric: ""
    property string lyric: ""
    property string nextLyric: ""

    property int _seq: -1
    property real _lastAdvance: 0

    function apply(json) {
        var d = JSON.parse(json)
        if (d.seq !== _seq) {
            _seq = d.seq
            _lastAdvance = Date.now()
        }
        title = d.title || ""
        artist = d.artist || ""
        playing = d.playing === true
        positionMs = d.positionMs || 0
        durationMs = d.durationMs || 0
        artUrl = d.art ? directory + d.art : ""
        lyricsOn = d.lyrics !== null && d.lyrics !== undefined
        prevLyric = lyricsOn ? (d.lyrics.prev || "") : ""
        lyric = lyricsOn ? (d.lyrics.current || "") : ""
        nextLyric = lyricsOn ? (d.lyrics.next || "") : ""
    }

    function poll() {
        var xhr = new XMLHttpRequest()
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== XMLHttpRequest.DONE)
                return
            if (xhr.responseText.length > 0) {
                try { feed.apply(xhr.responseText) } catch (e) { /* half-written file: next poll */ }
            }
            feed.alive = feed._seq >= 0 && Date.now() - feed._lastAdvance < feed.staleMs
        }
        xhr.open("GET", directory + "now_playing.json")
        xhr.send()
    }

    Timer {
        interval: feed.pollMs; running: true; repeat: true; triggeredOnStart: true
        onTriggered: feed.poll()
    }
}
