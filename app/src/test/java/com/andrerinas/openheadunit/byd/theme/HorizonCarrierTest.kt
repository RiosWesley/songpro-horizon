package com.andrerinas.openheadunit.byd.theme

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The carrier the app builds on the car must be the one the offline builder makes (and that ran on
 * the car). Needs the car's factory RCCs, which are not in the repository:
 *
 *   HORIZON_STOCK_DIR=<dir with cluster_theme1.rcc, cluster_theme2.rcc, commonTranslate.rcc>
 *   HORIZON_REFERENCE_CARRIER=<OFFLINE_ONLY_horizon_sx309_88.rcc from build_horizon_sx309_88.py --horizon horizon/qml>
 */
class HorizonCarrierTest {

    @Test
    fun buildsTheSameCarrierAsTheOfflineBuilder() {
        val stock = System.getenv("HORIZON_STOCK_DIR")?.let(::File)
        val reference = System.getenv("HORIZON_REFERENCE_CARRIER")?.let(::File)
        assumeTrue("set HORIZON_STOCK_DIR and HORIZON_REFERENCE_CARRIER", stock != null && reference != null)

        val qml = listOf(File("../horizon/qml"), File("horizon/qml")).first { it.isDirectory }
        val horizon = qml.listFiles()!!.filter { it.isFile }.associate { it.name to it.readBytes() }

        val carrier = HorizonCarrier.build(stock!!, horizon)

        // Entry by entry first, so a difference names the file (patch text) or the compression.
        val expected = Rcc.decode(reference!!.readBytes())
        val built = Rcc.decode(carrier.bytes).associateBy { it.path }
        for (e in expected) {
            val b = built[e.path]
            assertEquals("entries", true, b != null)
            assertEquals("data of ${e.path}", HorizonCarrier.sha256(e.data), HorizonCarrier.sha256(b!!.data))
            assertEquals("stored bytes of ${e.path}", HorizonCarrier.sha256(e.stored), HorizonCarrier.sha256(b.stored))
        }
        assertEquals(HorizonCarrier.sha256(reference.readBytes()), carrier.sha256)
    }
}
