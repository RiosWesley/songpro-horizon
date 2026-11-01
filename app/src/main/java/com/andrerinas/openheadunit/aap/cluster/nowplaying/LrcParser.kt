package com.andrerinas.openheadunit.aap.cluster.nowplaying

internal data class LyricLine(val timeMs: Long, val text: String)

/**
 * Reads synced lyrics in LRC form: `[mm:ss.xx] text`, several stamps per line allowed, metadata
 * tags (`[ar:…]`) ignored and `[offset:±ms]` applied (positive shows the lines sooner).
 */
internal object LrcParser {

    private val STAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): List<LyricLine> {
        val offset = OFFSET.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            // Stamps lead the line; the text is whatever follows the last of them.
            var rest = raw.trimStart()
            val times = mutableListOf<Long>()
            while (true) {
                val m = STAMP.matchAt(rest, 0) ?: break
                val (min, sec, frac) = m.destructured
                times += min.toLong() * 60_000 + sec.toLong() * 1_000 + fractionMs(frac)
                rest = rest.substring(m.range.last + 1)
            }
            val text = rest.trim()
            times.forEach { lines += LyricLine(maxOf(0L, it - offset), text) }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun fractionMs(frac: String): Long = when (frac.length) {
        0 -> 0L
        1 -> frac.toLong() * 100
        2 -> frac.toLong() * 10
        else -> frac.take(3).toLong()
    }
}

/** The line just sung, the one being sung and the next, at a playback position. */
internal data class LyricWindow(val previous: String, val current: String, val next: String) {
    companion object {
        /** Empty lines in LRC mark instrumental breaks. */
        private const val BREAK = "♪"

        fun at(lines: List<LyricLine>, positionMs: Long): LyricWindow {
            val i = lines.indexOfLast { it.timeMs <= positionMs }
            fun text(k: Int) = lines.getOrNull(k)?.text?.ifBlank { BREAK }.orEmpty()
            return if (i < 0) LyricWindow("", "", text(0)) else LyricWindow(text(i - 1), text(i), text(i + 1))
        }
    }
}
