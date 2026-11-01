package com.andrerinas.openheadunit.aap.cluster.nowplaying

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import com.andrerinas.openheadunit.aap.protocol.proto.MediaPlayback
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.protoUint32ToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Feeds the cluster theme what the phone is playing over Android Auto: title, artist, cover,
 * progress and, when enabled, the synced lyric lines around the current position.
 *
 * The phone sends metadata on track changes and a status with whole seconds now and then; the
 * position in between is extrapolated from the last status while playing.
 */
internal object ClusterNowPlaying {

    private const val TAG = "ClusterNowPlaying"
    private const val TICK_MS = 500L

    private val lock = Any()
    private var scope: CoroutineScope? = null
    private var writer: NowPlayingFeedWriter? = null
    private var lyricsClient: LyricsClient? = null
    private var lyricsEnabled = true

    private var track: TrackKey? = null
    private var lines: List<LyricLine>? = null
    private var lyricsJob: Job? = null
    private var playing = false
    private var positionMs = 0L
    private var positionAt = 0L

    fun start(context: Context, lyrics: Boolean) {
        synchronized(lock) {
            if (scope != null) return
            val dir = File(Environment.getExternalStorageDirectory(), ".cluster/horizon")
            writer = NowPlayingFeedWriter(dir)
            lyricsClient = LyricsClient(File(context.cacheDir, "lyrics"))
            lyricsEnabled = lyrics
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { s ->
                s.launch {
                    while (isActive) {
                        tick()
                        delay(TICK_MS)
                    }
                }
            }
            AppLog.i("$TAG: feeding $dir (lyrics ${if (lyrics) "on" else "off"})")
        }
    }

    fun stop() {
        synchronized(lock) {
            scope?.cancel()
            scope = null
            runCatching { writer?.clear() }
            writer = null
            track = null
            lines = null
        }
    }

    fun onMetadata(meta: MediaPlayback.MediaMetaData) {
        synchronized(lock) {
            val w = writer ?: return
            val key = TrackKey(meta.song.orEmpty(), meta.artist.orEmpty(), meta.album.orEmpty(),
                if (meta.hasDurationSeconds()) meta.durationSeconds else 0)
            runCatching { w.putArt(if (meta.hasAlbumArt()) meta.albumArt.toByteArray() else null) }
                .onFailure { AppLog.w("$TAG: cover not written: ${it.message}") }
            if (key == track) return
            track = key
            lines = null
            positionMs = 0
            positionAt = SystemClock.elapsedRealtime()
            if (lyricsEnabled && key.title.isNotBlank()) fetchLyrics(key)
        }
    }

    fun onStatus(status: MediaPlayback.MediaPlaybackStatus) {
        synchronized(lock) {
            if (status.hasState()) playing = status.state == MediaPlayback.MediaPlaybackStatus.State.PLAYING
            if (status.hasPlaybackSeconds()) {
                positionMs = status.playbackSeconds.protoUint32ToLong() * 1000L
                positionAt = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun fetchLyrics(key: TrackKey) {
        lyricsJob?.cancel()
        val client = lyricsClient ?: return
        lyricsJob = scope?.launch {
            val found = client.find(key)
            synchronized(lock) { if (track == key) lines = found }
            AppLog.i("$TAG: lyrics ${if (found == null) "not found" else "${found.size} lines"} for '${key.title}'")
        }
    }

    private fun tick() {
        val (w, snapshot) = synchronized(lock) {
            val t = track ?: return
            val w = writer ?: return
            val duration = t.durationSec * 1000L
            val now = positionMs + if (playing) SystemClock.elapsedRealtime() - positionAt else 0L
            val position = if (duration > 0) now.coerceAtMost(duration) else now
            w to NowPlayingSnapshot(
                title = t.title,
                artist = t.artist,
                playing = playing,
                positionMs = position,
                durationMs = duration,
                lyrics = lines?.let { LyricWindow.at(it, position) },
            )
        }
        runCatching { w.write(snapshot) }.onFailure { AppLog.w("$TAG: feed not written: ${it.message}") }
    }
}
