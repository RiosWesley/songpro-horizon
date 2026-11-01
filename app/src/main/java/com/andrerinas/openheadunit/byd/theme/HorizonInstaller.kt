package com.andrerinas.openheadunit.byd.theme

import android.content.Context
import com.andrerinas.openheadunit.byd.BydClusterMap
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings
import com.andrerinas.openheadunit.utils.adb.AdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs and removes the Horizon cluster theme, the way it was proven on the car
 * (horizon/RECUPERACAO.md): the carrier goes into /sdcard/.cluster/translate/, the theme tool
 * selects it, and the theme shows after one "reboot fission-all". Removing it means moving the file
 * to retired/ and restarting: with the selected file gone, the cluster falls back to factory.
 *
 * Shell steps run through Self-ADB (uid shell): the theme tool must run as shell, and the reboot
 * needs adbd. Files are moved, never deleted.
 */
class HorizonInstaller(private val context: Context, private val log: (String) -> Unit) {

    sealed class Outcome(val message: String) {
        /** Selected or removed; shows after the panel restarts. */
        class RestartNeeded(message: String) : Outcome(message)
        class NothingToDo(message: String) : Outcome(message)
        class Failed(message: String) : Outcome(message)
    }

    private class Stop(message: String) : Exception(message)

    /** Builds the carrier from this car's factory files and selects it. */
    suspend fun install(): Outcome = guarded {
        val carrier = HorizonCarrier.build(File(STOCK_DIR), horizonFiles(), log)
        log("Tema montado: ${carrier.bytes.size / 1024} KB, SHA-256 ${carrier.sha256.take(16)}…")

        val staging = context.getExternalFilesDir("horizon") ?: throw Stop("armazenamento do app indisponível")
        File(staging, carrier.name).writeBytes(carrier.bytes)
        val tool = File(staging, TOOL_NAME).apply { writeBytes(context.assets.open("horizon/$TOOL_NAME").use { it.readBytes() }) }
        expectSha(shell("mkdir -p $TRANSLATE $RETIRED $INCOMING_DIR && cp '${tool.path}' $TOOL && chmod 644 $TOOL && sha256sum $TOOL"),
            HorizonCarrier.sha256(tool.readBytes()), "ferramenta do tema")

        log("Consultando o tema selecionado")
        val query = runTool("query", 90_000)
        if (ThemeToolOutput.exitCode(query) != 0) throw Stop("a consulta foi recusada: ${ThemeToolOutput.reason(query)}")
        val selection = ThemeToolOutput.selection(query)
        if (selection == ThemeToolOutput.Selection.OTHER || selection == ThemeToolOutput.Selection.UNKNOWN)
            throw Stop("há outro tema selecionado no painel (não é o Horizon nem o de fábrica); remova-o antes")

        val present = rccFiles()
        val foreign = present.filterNot { it.startsWith(HorizonCarrier.NAME_PREFIX) }
        if (foreign.isNotEmpty()) throw Stop("há outro tema em $TRANSLATE: ${foreign.joinToString()}; remova-o antes")
        if (present == listOf(carrier.name) && selection == ThemeToolOutput.Selection.HORIZON && query.contains(carrier.name))
            return@guarded Outcome.NothingToDo("Esta versão do Horizon já está instalada e selecionada.")

        retire(present)
        expectSha(shell("cp '${staging.path}/${carrier.name}' $INCOMING && sha256sum $INCOMING"), carrier.sha256, "tema copiado")
        shell("mv $INCOMING $TRANSLATE${carrier.name}")

        log("Selecionando o Horizon")
        val replace = if (selection == ThemeToolOutput.Selection.HORIZON) " --replace-own" else ""
        val load = runTool("load ${carrier.name} --sha256 ${carrier.sha256} --assert-parked --ack-ambiguous$replace", 200_000)
        if (!ThemeToolOutput.loadSaved(load)) {
            retire(listOf(carrier.name))
            throw Stop("o painel não aceitou o tema: ${ThemeToolOutput.reason(load)}")
        }
        BydClusterMap.fitTo(Settings(context), horizon = true)
        Outcome.RestartNeeded("Horizon selecionado. Ele aparece depois de reiniciar o painel.")
    }

    /** Takes every Horizon carrier out of the lookup directory. */
    suspend fun remove(): Outcome = guarded {
        val horizon = rccFiles().filter { it.startsWith(HorizonCarrier.NAME_PREFIX) }
        if (horizon.isEmpty()) return@guarded Outcome.NothingToDo("Nenhum Horizon instalado: o painel já usa o tema de fábrica.")
        retire(horizon)
        BydClusterMap.fitTo(Settings(context), horizon = false)
        Outcome.RestartNeeded("Horizon removido. O painel de fábrica volta depois de reiniciar.")
    }

    /** The installed Horizon carrier, or null; throws when Self-ADB is not reachable. */
    suspend fun installed(): String? = rccFiles().firstOrNull { it.startsWith(HorizonCarrier.NAME_PREFIX) }

    /** Restarts the cluster host and the head unit; only with the car parked. */
    suspend fun restartPanel(): Boolean = AdbManager.openService(context, "reboot:fission-all")

    /**
     * Runs off the main thread (hashing 256 MB, Self-ADB), to the end even if the screen goes away
     * (a half-done move would only confuse), and turns every error into a message.
     */
    private suspend fun guarded(block: suspend () -> Outcome): Outcome = withContext(Dispatchers.IO + NonCancellable) {
        try {
            block()
        } catch (e: Stop) {
            Outcome.Failed(e.message ?: "falhou")
        } catch (e: HorizonCarrier.Refused) {
            Outcome.Failed(e.message ?: "recusado")
        } catch (e: Exception) {
            AppLog.e("HorizonInstaller: ${e.message}", e)
            Outcome.Failed("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun horizonFiles(): Map<String, ByteArray> {
        val names = context.assets.list(QML_ASSETS).orEmpty().filter { it.endsWith(".qml") || it.endsWith(".js") }
        return names.associateWith { name -> context.assets.open("$QML_ASSETS/$name").use { it.readBytes() } }
    }

    private suspend fun rccFiles(): List<String> =
        shell("ls $TRANSLATE 2>/dev/null; true").lines().map { it.trim() }.filter { it.endsWith(".rcc") }

    private suspend fun retire(names: List<String>) {
        for (name in names) {
            log("Movendo $name para retired/")
            shell("mv $TRANSLATE$name $RETIRED$name")
        }
    }

    private suspend fun runTool(args: String, timeoutMs: Long): String =
        shell("CLASSPATH=$TOOL app_process / HorizonThemeTool $args; echo EXIT=$?", timeoutMs)

    private suspend fun shell(command: String, timeoutMs: Long = 30_000): String {
        val (code, out) = AdbManager.exec(context, command, maxReadMs = timeoutMs)
        if (code != 0) throw Stop("ADB local indisponível ($out). Ative a depuração ADB sem fio da multimídia.")
        return out
    }

    private fun expectSha(sha256sumOutput: String, expected: String, what: String) {
        if (!sha256sumOutput.contains(expected)) throw Stop("$what não conferiu (SHA-256)")
    }

    companion object {
        const val STOCK_DIR = "/system/lib64"
        const val TRANSLATE = "/sdcard/.cluster/translate/"
        const val RETIRED = "/sdcard/.cluster/retired/"
        private const val INCOMING_DIR = "/sdcard/.cluster/horizon"
        private const val INCOMING = "$INCOMING_DIR/.incoming.rcc"
        private const val TOOL_NAME = "horizon-theme-tool.dex"
        private const val TOOL = "/data/local/tmp/$TOOL_NAME"
        private const val QML_ASSETS = "horizon/qml"
    }
}
