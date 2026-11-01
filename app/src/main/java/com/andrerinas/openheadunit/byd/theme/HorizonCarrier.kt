package com.andrerinas.openheadunit.byd.theme

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Builds the Horizon translate carrier from the car's own factory resources, on the car.
 *
 * Nothing from BYD is shipped with the app: the factory QML is read from /system/lib64 and patched
 * here. Every input must match the Song Pro 8.8" SX309 cohort the theme was made and tested on,
 * else the build refuses (the patch anchors and the 3D crash fix are specific to these sources).
 */
object HorizonCarrier {

    const val NAME_PREFIX = "Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_"
    /** The only build the theme tool acts on (horizon/tools/horizon-theme-tool/src/Policy.java). */
    const val SUPPORTED_FINGERPRINT = "BYD-AUTO/DiLink3.0/DiLink3.0:10/QKQ1.210910.001/eng.build.20251108.051320:user/release-keys"
    private const val MAX_CARRIER = 4 * 1024 * 1024 // the translate carrier bound the recovery was planned for
    private const val LOCALES = 80

    private const val THEME1 = "cluster_theme1.rcc"
    private const val THEME2 = "cluster_theme2.rcc"
    private const val TRANSLATE = "commonTranslate.rcc"

    private val STOCK_FILES = linkedMapOf(
        THEME1 to "7ff2bde70effb76ce06b701c161d7600cd8bd4918c7705a58e30507de67ab009",
        THEME2 to "64cda4218526d815036f5f8dce95e6152033e653e58ce061efaf037265bf1795",
        TRANSLATE to "b41bd89a9e3a881c5514992af7033a062cb6207355f809384f0469205d27fcc3")

    private class Source(val rcc: String, val alias: String, val sha256: String)

    private val ROOT1 = Source(THEME1, "qml/cluster.qml", "f74082cafeed18274942e74dd2890909b24085d0fc6e2fa8113d6069fd595df2")
    private val ROOT2 = Source(THEME2, "qml10_25/cluster.qml", "046749d4743eb04de4d9454f548ba970c487610b0c25bbe56c9baa2238750fd8")
    private val LAMPS = Source(THEME2, "qml10_25/WarinLightLayout.qml", "4c3651a6202deeedd8da80c03efd7f17c34ccbb063c0f88edee4545848247092")
    private val CENTER = Source(THEME2, "qml10_25/Center.qml", "b284c991e474a8b2a0369a4151d15b2ee5ecfeaa19fa01a5f617b4eb1293b4d1")
    private val MODEL3D = Source(THEME2, "qml10_25/adas3d/components/Model3D.qml", "d8be8a34e500e61b77220da17253674f8f5831fa52a0e40375e0b8a9433367fe")
    private val ADAS3D = Source(THEME2, "qml10_25/adas3d/Adas3dContainer.qml", "9f4af828aff95dfd887fb087105f3acf2ff8f409385a42a2a6203051df5efd29")
    private val THEMES = Source(THEME2, "qml10_25/menu/ThemeSwitch.qml", "22fefad0a83b5a36ee4879f49df15e8fc425fee778419bf16a15ad9b3a5abe9c")

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
        for ((name, expected) in STOCK_FILES) {
            progress("Conferindo $name")
            val got = sha256(File(stockDir, name))
            if (got != expected) throw Refused("$name não é o do firmware SX309 do Song Pro 8,8\" (SHA-256 $got)")
        }

        progress("Lendo o QML de fábrica")
        val factory = listOf(ROOT1, ROOT2, LAMPS, CENTER, MODEL3D, ADAS3D, THEMES).associate { source ->
            val data = Rcc.read(File(stockDir, source.rcc), source.alias)
            if (sha256(data) != source.sha256) throw Refused("${source.alias} de fábrica inesperado; nada foi feito")
            source.alias to data
        }

        val translate = Rcc.decode(File(stockDir, TRANSLATE).readBytes())
        if (translate.size != LOCALES || translate.any { !it.path.startsWith("common/language/") })
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
        val patched = apply(original)
        check(undo(patched) == original) { "patch of ${source.alias} is not exactly reversible" }
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
