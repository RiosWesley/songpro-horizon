package com.andrerinas.openheadunit.byd.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Outputs as the theme tool printed them on the car (SX309), where a load never fully settles. */
class ThemeToolOutputTest {

    private val name = "Di3F_DynastyOceanTranslate_V0001_TranslateHorizon_3ed9157e5c3cc21a172c4669da3c4989.rcc"

    @Test
    fun aLoadSavedWithoutTheRegistrationAckCountsAsSelected() {
        val out = """
            HORIZON: LOAD sent (one request) native=0 name=$name
            HORIZON: ACK4 config ints=[4, 0, 0, 0, 0]
            HORIZON: selection: UI=Di3F_DynastyOceanTranslate_V0001 source=0 path=/data/cells/cell2-rw/data/media/0/.cluster/translate/$name resource=Horizon_3e  [HORIZON CARRIER]
            HORIZON: AMBIGUOUS: selected=true ack2=false. Do NOT repeat. Look at the panel, then run query.
            HORIZON_RESULT={"status":"AMBIGUOUS","detail":"selected=true ack2=false"}
            EXIT=2
        """.trimIndent()

        assertTrue(ThemeToolOutput.loadSaved(out))
        assertEquals(ThemeToolOutput.Selection.HORIZON, ThemeToolOutput.selection(out))
    }

    @Test
    fun anAmbiguousLoadThatDidNotSelectIsNotSaved() {
        val out = """
            HORIZON: selection: UI=Di3F_DynastyOceanTranslate_V0001 source=1 path=default resource=default  [FACTORY DEFAULT]
            HORIZON: AMBIGUOUS: selected=false ack2=false. Do NOT repeat. Look at the panel, then run query.
            HORIZON_RESULT={"status":"AMBIGUOUS","detail":"selected=false ack2=false"}
            EXIT=2
        """.trimIndent()

        assertFalse(ThemeToolOutput.loadSaved(out))
        assertEquals(ThemeToolOutput.Selection.FACTORY, ThemeToolOutput.selection(out))
    }

    @Test
    fun aRefusalIsNotSavedAndExplainsWhy() {
        val out = """
            HORIZON: REFUSED: last mutation was 9 s ago; wait 16 s (native throttle)
            HORIZON_RESULT={"status":"REFUSED","detail":"last mutation was 9 s ago; wait 16 s (native throttle)"}
            EXIT=3
        """.trimIndent()

        assertFalse(ThemeToolOutput.loadSaved(out))
        assertEquals(3, ThemeToolOutput.exitCode(out))
        assertEquals("last mutation was 9 s ago; wait 16 s (native throttle)", ThemeToolOutput.reason(out))
        assertEquals(ThemeToolOutput.Selection.UNKNOWN, ThemeToolOutput.selection(out))
    }
}
