package com.andrerinas.openheadunit.aap.cluster.nowplaying

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class NowPlayingSnapshot(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    /** null when lyrics are off or unknown for this track. */
    val lyrics: LyricWindow?,
)

/**
 * Writes the feed the cluster theme polls: now_playing.json and the cover, in a directory the
 * cluster host can read (the Android cell's /sdcard/.cluster/horizon/). Each write replaces the
 * file atomically, so the theme never reads half a file, and advances `seq`, which the theme uses
 * to tell a live writer from a stale file.
 */
internal class NowPlayingFeedWriter(private val dir: File) {

    private var seq = 0L
    private var artFile: String = ""
    private var artDigest: String = ""

    /** Stores the cover if it changed; the next write names it. */
    fun putArt(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) {
            artFile = ""
            artDigest = ""
            return
        }
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes).take(8).joinToString("") { "%02x".format(it) }
        if (digest == artDigest) return
        dir.mkdirs()
        // Qt picks the decoder from the extension first, so name it after the real format.
        val name = "art_$digest.${if (isPng(bytes)) "png" else "jpg"}"
        atomicWrite(File(dir, name), bytes)
        dir.listFiles { f -> f.name.startsWith("art_") && f.name != name }?.forEach { it.delete() }
        artFile = name
        artDigest = digest
    }

    fun write(s: NowPlayingSnapshot) {
        val json = JSONObject()
            .put("seq", ++seq)
            .put("title", s.title)
            .put("artist", s.artist)
            .put("playing", s.playing)
            .put("positionMs", s.positionMs)
            .put("durationMs", s.durationMs)
            .put("art", artFile)
            .put("lyrics", s.lyrics?.let {
                JSONObject().put("prev", it.previous).put("current", it.current).put("next", it.next)
            } ?: JSONObject.NULL)
        dir.mkdirs()
        atomicWrite(File(dir, FEED), json.toString().toByteArray(Charsets.UTF_8))
    }

    /** No feed: the theme falls back to the native media text right away. */
    fun clear() {
        File(dir, FEED).delete()
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val tmp = File(dir, ".${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }

    private fun isPng(b: ByteArray) =
        b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte()

    private companion object {
        const val FEED = "now_playing.json"
    }
}
