package com.andrerinas.openheadunit.byd.theme

/**
 * Reads what horizon/tools/horizon-theme-tool prints, run with "; echo EXIT=$?" appended.
 *
 * Exit codes: 0 settled, 2 ambiguous, 3 refused (nothing sent), 4 failed. On SX309 a load never
 * settles: the cluster saves the choice (ACK4) without the registration ACK2, so the tool ends
 * AMBIGUOUS with "selected=true", and the theme shows after the next fission-all restart.
 */
object ThemeToolOutput {

    enum class Selection { FACTORY, HORIZON, OTHER, UNKNOWN }

    fun exitCode(out: String): Int? =
        Regex("""^EXIT=(\d+)\s*$""", RegexOption.MULTILINE).findAll(out).lastOrNull()?.groupValues?.get(1)?.toInt()

    /** The OEM selection the tool last reported. */
    fun selection(out: String): Selection {
        val line = out.lineSequence().lastOrNull { it.contains("HORIZON: selection: ") } ?: return Selection.UNKNOWN
        return when {
            line.contains("[FACTORY DEFAULT]") -> Selection.FACTORY
            line.contains("[HORIZON CARRIER]") -> Selection.HORIZON
            line.contains("[OTHER]") -> Selection.OTHER
            else -> Selection.UNKNOWN
        }
    }

    /** True when the cluster took the choice: settled, or saved without the registration ACK. */
    fun loadSaved(out: String): Boolean = when (exitCode(out)) {
        0 -> true
        2 -> out.contains("AMBIGUOUS: selected=true")
        else -> false
    }

    /** The tool's own one-line reason (its HORIZON_RESULT detail, or the refusal), for the user. */
    fun reason(out: String): String {
        Regex(""""detail":"([^"]*)"""").findAll(out).lastOrNull()?.let { return it.groupValues[1] }
        return out.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() && !it.startsWith("EXIT=") } ?: "sem resposta"
    }
}
