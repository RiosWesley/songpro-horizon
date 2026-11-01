package com.andrerinas.openheadunit.aap.cluster.nowplaying

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** One source of synced lyrics. Blocking; throws when the source cannot answer right now. */
internal interface LyricsProvider {
    val name: String

    /** The LRC text, or null when this source has no synced lyrics for the track. */
    fun find(track: TrackKey): String?
}

private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

/** GET [url]; null on 404, the body on 200, an exception otherwise. */
private fun httpGet(url: String, userAgent: String): String? {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.setRequestProperty("User-Agent", userAgent)
        if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
        if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

/** LRCLIB (lrclib.net): open, keyless, matched on title, artist, album and duration. */
internal class LrclibProvider : LyricsProvider {
    override val name = "lrclib"

    override fun find(track: TrackKey): String? {
        val url = "https://lrclib.net/api/get?track_name=${q(track.title)}&artist_name=${q(track.artist)}" +
            (if (track.album.isNotBlank()) "&album_name=${q(track.album)}" else "") +
            (if (track.durationSec > 0) "&duration=${track.durationSec}" else "")
        val body = httpGet(url, USER_AGENT) ?: return null
        return JSONObject(body).optString("syncedLyrics", "").ifBlank { null }
    }

    private companion object {
        // LRCLIB asks clients to identify themselves.
        const val USER_AGENT = "open-headunit cluster lyrics (https://github.com/RiosWesley/open-headunit)"
    }
}

/**
 * NetEase Cloud Music's public web API, as a fallback: search, keep the hit whose artist matches
 * and whose length is within a few seconds of the track, then fetch its LRC. Its lyrics open with
 * credit lines (lyricist, composer…) stamped like lyrics; those are dropped.
 */
internal class NeteaseProvider : LyricsProvider {
    override val name = "netease"

    override fun find(track: TrackKey): String? {
        val search = httpGet(
            "https://music.163.com/api/search/get?s=${q("${track.title} ${track.artist}")}&type=1&limit=10",
            USER_AGENT
        ) ?: return null
        val songs = JSONObject(search).optJSONObject("result")?.optJSONArray("songs") ?: return null
        val id = (0 until songs.length()).map { songs.getJSONObject(it) }.firstOrNull { song ->
            val artists = song.optJSONArray("artists")
            val artistMatch = artists != null && (0 until artists.length()).any {
                artists.getJSONObject(it).optString("name").equals(track.artist, ignoreCase = true)
            }
            val lengthMatch = track.durationSec <= 0 || abs(song.optLong("duration") / 1000 - track.durationSec) <= 3
            artistMatch && lengthMatch
        }?.optLong("id") ?: return null

        val body = httpGet("https://music.163.com/api/song/lyric?id=$id&lv=1", USER_AGENT) ?: return null
        val lrc = JSONObject(body).optJSONObject("lrc")?.optString("lyric").orEmpty()
        return withoutCredits(lrc).ifBlank { null }
    }

    internal companion object {
        private const val USER_AGENT = "Mozilla/5.0"
        private val CREDIT = Regex("""^\s*(\[[^]]*])+\s*(作词|作曲|编曲|制作人|监制|混音|母带|和声|配唱|吉他|贝斯|鼓|弦乐|录音|出品)[^:：]*[:：]""")

        fun withoutCredits(lrc: String) = lrc.lineSequence().filterNot { CREDIT.containsMatchIn(it) }.joinToString("\n")
    }
}
