package com.andrerinas.openheadunit.aap.cluster.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    @Test
    fun readsStampsFractionsMetadataAndRepeatedLines() {
        val lines = LrcParser.parse(
            """
            [ar:Someone]
            [ti:Something]
            [00:12.30]first line
            [00:15.5]second line
            [01:02.345][02:10.00] chorus
            [00:20.00]
            """.trimIndent()
        )
        assertEquals(
            listOf(
                LyricLine(12_300, "first line"),
                LyricLine(15_500, "second line"),
                LyricLine(20_000, ""),
                LyricLine(62_345, "chorus"),
                LyricLine(130_000, "chorus"),
            ),
            lines
        )
    }

    @Test
    fun positiveOffsetShowsLinesSooner() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00]line")
        assertEquals(listOf(LyricLine(9_500, "line")), lines)
    }

    @Test
    fun windowAroundThePosition() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(2_000, ""), LyricLine(3_000, "c"))
        assertEquals(LyricWindow("", "", "a"), LyricWindow.at(lines, 500))
        assertEquals(LyricWindow("", "a", "♪"), LyricWindow.at(lines, 1_000))
        assertEquals(LyricWindow("a", "♪", "c"), LyricWindow.at(lines, 2_500))
        assertEquals(LyricWindow("♪", "c", ""), LyricWindow.at(lines, 9_000))
    }
}
