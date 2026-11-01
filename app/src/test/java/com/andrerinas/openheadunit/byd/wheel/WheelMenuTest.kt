package com.andrerinas.openheadunit.byd.wheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The menu must only ever take the wheel keys it uses, so music and volume keep working. */
class WheelMenuTest {

    private val menuKey = 300
    private val previous = 88
    private val next = 87
    private val ok = 164
    private val volumeUp = 24

    private var card = "auto"
    private val menu = WheelMenu(
        WheelMenu.Keys(menuKey, previous, next, setOf(ok)),
        { listOf(WheelMenu.Item("Card", card) { card = "music" }, WheelMenu.Item("Map", "off") {}) },
        {},
    )

    private fun press(code: Int): Boolean = menu.onKey(code, down = true, repeat = 0).also { taken ->
        assertEquals("release follows press for $code", taken, menu.onKey(code, down = false, repeat = 0))
    }

    @Test
    fun aClosedMenuLeavesTrackAndOkKeysAlone() {
        assertFalse(press(next))
        assertFalse(press(previous))
        assertFalse(press(ok))
        assertFalse(menu.open)
    }

    @Test
    fun theMenuKeyOpensAndClosesAndIsAlwaysTaken() {
        assertTrue(press(menuKey))
        assertTrue(menu.open)
        assertTrue(press(menuKey))
        assertFalse(menu.open)
    }

    @Test
    fun anOpenMenuTakesItsKeysButNotVolume() {
        press(menuKey)
        assertTrue(press(next))
        assertTrue(press(ok))
        assertEquals("OK changed the second item, not the first", "auto", card)
        assertTrue(press(previous))
        assertTrue(press(ok))
        assertEquals("music", card)
        assertFalse(press(volumeUp))
    }

    @Test
    fun aKeyHeldWhenTheMenuClosesIsStillTakenOnRelease() {
        press(menuKey)
        assertTrue(menu.onKey(next, down = true, repeat = 0))
        menu.close()
        assertTrue(menu.onKey(next, down = false, repeat = 0))
        assertFalse(menu.onKey(next, down = true, repeat = 0))
    }
}
