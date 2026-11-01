package com.andrerinas.openheadunit.byd.theme

import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The car's panel as it was before Song Pro Horizon first changed it, kept on the head unit in
 * /sdcard/SongProHorizon/original/ (Song Pro variants differ, so each car keeps its own):
 * - files/: the factory cluster files Horizon is built from, copied from /system/lib64;
 * - translate/: whatever was in the cluster's theme folder;
 * - estado.txt: the firmware, the theme the panel had selected and the settings the app changes
 *   (BYD's Android Auto, the accessibility services);
 * - SHA256SUMS: every copy's hash, written last, once each copy matched its original.
 *
 * Made once, before the first install. The restore does not need it: the factory files stay
 * read-only on /system, so taking Horizon off brings the original panel back. It is what to compare
 * with, or rebuild from, if a car ever differs.
 *
 * @param shell runs a command as the shell user and returns its output
 * @param staging a folder this app can write, for estado.txt on its way to the backup
 */
internal class OriginalBackup(
    private val shell: suspend (command: String, timeoutMs: Long) -> String,
    private val staging: File,
    private val log: (String) -> Unit,
) {

    class Failed(message: String) : Exception(message)

    /** Makes the backup unless it is already complete. @param query the theme tool's query output */
    suspend fun ensure(query: String) {
        if (shell("test -f $DIR/$SUMS && echo DONE; true", 10_000).contains("DONE")) return
        log("Fazendo o backup do painel original em $DIR")
        shell("rm -rf $TMP && mkdir -p $TMP/files $TMP/translate", 30_000)

        val sources = FILES.joinToString(" ") { "$STOCK/$it" }
        shell("cp $sources $TMP/files/; for f in ${HorizonInstaller.TRANSLATE}*.rcc; do [ -f \"\$f\" ] && cp \"\$f\" $TMP/translate/; done; true", COPY_MS)

        val state = File(staging, STATE).apply { writeText(describe(query)) }
        shell("cp '${state.path}' $TMP/$STATE", 30_000)

        log("Conferindo o backup")
        val originals = hashes(shell("sha256sum $sources", COPY_MS))
        val copies = hashes(shell("cd $TMP && sha256sum files/*", COPY_MS))
        for (name in FILES) {
            val original = originals[name] ?: throw Failed("não consegui ler $STOCK/$name")
            if (copies[name] != original) throw Failed("a cópia de $name não conferiu (espaço livre?)")
        }
        shell("cd $TMP && sha256sum files/* translate/* $STATE > $SUMS 2>/dev/null; rm -rf $DIR && mv $TMP $DIR", COPY_MS)
        if (!shell("test -f $DIR/$SUMS && echo DONE; true", 10_000).contains("DONE")) throw Failed("não consegui fechar o backup")
        log("Backup do painel original salvo em $DIR")
    }

    private suspend fun describe(query: String): String {
        val translate = shell("ls -la ${HorizonInstaller.TRANSLATE} 2>&1; true", 10_000).trim()
        val nativeAa = shell("pm list packages -d 2>/dev/null | grep -c com.byd.androidauto; true", 10_000).trim()
        val accessibility = shell("settings get secure enabled_accessibility_services; true", 10_000).trim()
        val taken = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return """
            |Backup do painel original (Song Pro Horizon)
            |Feito em: $taken
            |Firmware: ${Build.FINGERPRINT}
            |Horizon já estava no painel: ${if (translate.contains(HorizonCarrier.NAME_PREFIX)) "sim" else "não"}
            |
            |== Tema selecionado no painel (HorizonThemeTool query)
            |${query.trim()}
            |
            |== Pasta de temas do painel (${HorizonInstaller.TRANSLATE})
            |$translate
            |
            |== Android Auto da BYD
            |${if (nativeAa == "0") "ligado" else "desligado"}
            |
            |== Serviços de acessibilidade ligados
            |$accessibility
            |""".trimMargin()
    }

    /** sha256sum output -> hash by file name. */
    private fun hashes(output: String): Map<String, String> = output.lines().mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size == 2 && parts[0].length == 64) parts[1].substringAfterLast('/') to parts[0] else null
    }.toMap()

    companion object {
        const val DIR = "/sdcard/SongProHorizon/original"
        private const val TMP = "$DIR.tmp"
        private const val STOCK = HorizonInstaller.STOCK_DIR
        private const val STATE = "estado.txt"
        private const val SUMS = "SHA256SUMS"
        // About 290 MB on SX309: copying and hashing it takes the head unit a while.
        private const val COPY_MS = 300_000L
        private val FILES = listOf("cluster_theme1.rcc", "cluster_theme2.rcc", "commonTranslate.rcc",
            "libBydCluster.so", "libBydDataSource.so")
    }
}
