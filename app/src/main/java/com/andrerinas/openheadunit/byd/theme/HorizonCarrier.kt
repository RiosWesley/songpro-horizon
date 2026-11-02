package com.andrerinas.openheadunit.byd.theme

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Builds the Horizon translate carrier from the car's own factory resources, on the car.
 *
 * Nothing from BYD is shipped with the app: the factory QML is read from /system/lib64 and patched
 * here. The theme was made on the Song Pro 8.8" SX309 firmware ([TESTED_FINGERPRINT]), but other
 * versions are checked, not refused: every patch must find each of its anchors exactly once (the
 * 3D crash fix, its six loaders) and come off again byte for byte, else the build refuses and
 * names the file that differs.
 */
object HorizonCarrier {

    const val NAME_PREFIX = "Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_"
    /** The firmware the theme was made and tested on; others are checked, not refused. */
    const val TESTED_FINGERPRINT = "BYD-AUTO/DiLink3.0/DiLink3.0:10/QKQ1.210910.001/eng.build.20251108.051320:user/release-keys"
    private const val MAX_CARRIER = 4 * 1024 * 1024 // the translate carrier bound the recovery was planned for

    private const val THEME1 = "cluster_theme1.rcc"
    private const val THEME2 = "cluster_theme2.rcc"
    private const val TRANSLATE = "commonTranslate.rcc"
    /** The factory files the carrier is built from, in /system/lib64. */
    val STOCK_FILES = listOf(THEME1, THEME2, TRANSLATE)

    private class Source(val rcc: String, val alias: String)

    private val ROOT1 = Source(THEME1, "qml/cluster.qml")
    private val ROOT2 = Source(THEME2, "qml10_25/cluster.qml")
    private val LAMPS = Source(THEME2, "qml10_25/WarinLightLayout.qml")
    private val CENTER = Source(THEME2, "qml10_25/Center.qml")
    private val MODEL3D = Source(THEME2, "qml10_25/adas3d/components/Model3D.qml")
    private val ADAS3D = Source(THEME2, "qml10_25/adas3d/Adas3dContainer.qml")
    private val THEMES = Source(THEME2, "qml10_25/menu/ThemeSwitch.qml")

    class Carrier(val bytes: ByteArray, val sha256: String) {
        /** The name the cluster's matcher accepts, keyed by the carrier's own hash. */
        val name: String get() = NAME_PREFIX + sha256.substring(0, 32) + ".rcc"
    }

    class Refused(message: String) : Exception(message)

    /**
     * @param stockDir where the factory RCCs are (/system/lib64 on the car)
     * @param horizon the Horizon QML/JS files by file name
     * @param progress one line per step, for the user
     */
    fun build(stockDir: File, horizon: Map<String, ByteArray>, progress: (String) -> Unit = {}): Carrier {
        for (name in STOCK_FILES) {
            if (!File(stockDir, name).canRead()) throw Refused("não encontrei $name de fábrica em $stockDir")
        }

        progress("Lendo o QML de fábrica")
        val factory = listOf(ROOT1, ROOT2, LAMPS, CENTER, MODEL3D, ADAS3D, THEMES).associate { source ->
            val data = try {
                Rcc.read(File(stockDir, source.rcc), source.alias)
            } catch (e: Exception) {
                throw Refused("${source.alias} não está em ${source.rcc} deste carro")
            }
            source.alias to data
        }

        val translate = Rcc.decode(File(stockDir, TRANSLATE).readBytes())
        if (translate.isEmpty() || translate.any { !it.path.startsWith("common/language/") })
            throw Refused("$TRANSLATE com conteúdo inesperado; nada foi feito")

        progress("Aplicando o Horizon")
        val patched = linkedMapOf(
            ROOT1.alias to factory.getValue(ROOT1.alias),
            ROOT2.alias to patch(factory, ROOT2, HorizonPatches::patchRoot, HorizonPatches::stripMarkers),
            LAMPS.alias to patch(factory, LAMPS, HorizonPatches::patchLamps, HorizonPatches::unpatchLamps),
            CENTER.alias to patch(factory, CENTER, HorizonPatches::patchCenter, HorizonPatches::stripMarkers),
            MODEL3D.alias to patch(factory, MODEL3D, HorizonPatches::patchModel3d, HorizonPatches::unpatchModel3d),
            ADAS3D.alias to patch(factory, ADAS3D, HorizonPatches::patchAdas3d, HorizonPatches::stripMarkers),
            THEMES.alias to patch(factory, THEMES, HorizonPatches::patchThemes, HorizonPatches::unpatchThemes))

        for (required in listOf("HorizonCluster.qml", "HorizonGeometry.js")) {
            if (required !in horizon) throw Refused("faltou $required no app")
        }
        val owned = patched.map { (alias, data) -> Rcc.entry(alias, data) } +
            horizon.toSortedMap().map { (name, data) -> Rcc.entry("horizon/$name", data) }

        progress("Montando o carrier")
        val bytes = Rcc.build(translate + owned)
        if (bytes.size !in 1 until MAX_CARRIER) throw Refused("carrier com ${bytes.size} bytes, fora do limite")
        verify(bytes, translate, owned)
        return Carrier(bytes, sha256(bytes))
    }

    /** The factory locales must come back byte for byte, and every owned entry exactly. */
    private fun verify(carrier: ByteArray, translate: List<Rcc.Entry>, owned: List<Rcc.Entry>) {
        val decoded = Rcc.decode(carrier).associateBy { it.path }
        check(decoded.size == translate.size + owned.size) { "duplicate aliases in the carrier" }
        for (e in translate) {
            val d = decoded.getValue(e.path)
            check(d.country == e.country && d.language == e.language && d.flags == e.flags && d.stored.contentEquals(e.stored)) {
                "stock locale entry changed: ${e.path}"
            }
        }
        for (e in owned) check(decoded.getValue(e.path).data.contentEquals(e.data)) { "owned entry does not round-trip: ${e.path}" }
    }

    private fun patch(factory: Map<String, ByteArray>, source: Source, apply: (String) -> String, undo: (String) -> String): ByteArray {
        val original = HorizonPatches.text(factory.getValue(source.alias))
        val patched = try {
            apply(original)
        } catch (e: IllegalStateException) {
            throw Refused("${source.alias} deste carro é diferente do que o Horizon conhece (${e.message})")
        }
        if (undo(patched) != original) throw Refused("o Horizon não sai de ${source.alias} sem deixar rastro; nada foi feito")
        return HorizonPatches.bytes(patched)
    }

    fun sha256(data: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(data))

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest())
    }

    private fun hex(digest: ByteArray) = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
