package com.andrerinas.openheadunit.aap.cluster.nowplaying

import com.andrerinas.openheadunit.utils.AppLog
import java.io.File
import java.security.MessageDigest

internal data class TrackKey(val title: String, val artist: String, val album: String, val durationSec: Int) {
    /** Stable file name for the cache. */
    val id: String by lazy {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$title\u0000$artist\u0000$album\u0000$durationSec".toByteArray())
        digest.take(10).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Synced lyrics for a track: the disk cache first, then each provider in order. A track is
 * remembered as having no lyrics only when every provider answered that it has none; a provider
 * that is down or offline makes it a retry for the next time the track plays. Blocking.
 */
internal class LyricsClient(
    private val cacheDir: File,
    private val providers: List<LyricsProvider> = listOf(LrclibProvider(), NeteaseProvider()),
    private val log: (String) -> Unit = { AppLog.i(it) },
) {

    fun find(track: TrackKey): List<LyricLine>? {
        val cached = File(cacheDir, "${track.id}.lrc")
        val miss = File(cacheDir, "${track.id}.none")
        if (cached.exists()) return LrcParser.parse(cached.readText())
        if (miss.exists()) return null

        var anyFailed = false
        for (provider in providers) {
            val lrc = try {
                provider.find(track)
            } catch (e: Exception) {
                log("LyricsClient: ${provider.name} unavailable: ${e.message}")
                anyFailed = true
                continue
            }
            val lines = lrc?.let { LrcParser.parse(it) }?.takeIf { it.isNotEmpty() } ?: continue
            cacheDir.mkdirs()
            cached.writeText(lrc)
            log("LyricsClient: lyrics for '${track.title}' from ${provider.name}")
            return lines
        }
        if (!anyFailed) {
            cacheDir.mkdirs()
            miss.writeText("")
        }
        return null
    }
}
