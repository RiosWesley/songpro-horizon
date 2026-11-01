package com.andrerinas.openheadunit.aap.cluster.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LyricsClientTest {

    private val track = TrackKey("Song", "Artist", "Album", 200)

    private class Fake(override val name: String, val answer: () -> String?) : LyricsProvider {
        var calls = 0
        override fun find(track: TrackKey): String? { calls++; return answer() }
    }

    private fun withCache(block: (File) -> Unit) {
        val dir = createTempDirectory("lyrics").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    @Test
    fun aDownProviderFallsThroughAndTheHitIsCached() = withCache { dir ->
        val down = Fake("down") { error("HTTP 502") }
        val backup = Fake("backup") { "[00:01.00]hello" }
        val client = LyricsClient(dir, listOf(down, backup)) {}

        assertEquals(listOf(LyricLine(1_000, "hello")), client.find(track))
        // Second time from the cache, no network.
        assertEquals(listOf(LyricLine(1_000, "hello")), client.find(track))
        assertEquals(1, backup.calls)
    }

    @Test
    fun noLyricsIsRememberedOnlyWhenEveryProviderAnswered() = withCache { dir ->
        assertNull(LyricsClient(dir, listOf(Fake("down") { error("offline") }, Fake("none") { null })) {}.find(track))
        assertFalse(File(dir, "${track.id}.none").exists())

        assertNull(LyricsClient(dir, listOf(Fake("a") { null }, Fake("b") { null })) {}.find(track))
        assertTrue(File(dir, "${track.id}.none").exists())
    }

    @Test
    fun neteaseCreditLinesAreDropped() {
        val lrc = "[00:00.000] 作词 : Someone\n[00:00.500] 作曲 : Someone\n[00:12.00]first line"
        assertEquals("[00:12.00]first line", NeteaseProvider.withoutCredits(lrc))
    }
}
