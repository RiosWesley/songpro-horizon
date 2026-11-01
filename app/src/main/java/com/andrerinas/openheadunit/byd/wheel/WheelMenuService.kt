package com.andrerinas.openheadunit.byd.wheel

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.cluster.ClusterMapToggle
import com.andrerinas.openheadunit.byd.theme.HorizonOptions
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings

/**
 * Steering wheel keys for the Horizon cluster menu (see [WheelMenu]), the way Electro remaps the
 * wheel: an accessibility service that filters key events, so it can take a key before the system
 * acts on it. Off unless switched on in the BYD panel ([WheelMenuSwitch]).
 */
class WheelMenuService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val file = HorizonMenuFile()
    private lateinit var settings: Settings
    private lateinit var menu: WheelMenu
    private val closeIdle = Runnable { menu.close() }
    /** The key being learned, taken on release too. */
    private var learnedHeld: Int? = null

    override fun onServiceConnected() {
        settings = Settings(this)
        menu = WheelMenu(keys(), ::items, ::onChange)
        running = this
        menu.publish()
        AppLog.i("WheelMenu: on, menu key ${WheelKeys.name(menuKey(settings))}")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        if (down && event.repeatCount == 0) AppLog.i("WheelMenu: key ${WheelKeys.name(code)} ($code) scan ${event.scanCode}")

        if (!down && code == learnedHeld) {
            learnedHeld = null
            return true
        }
        val learn = learner
        if (learn != null && down) {
            learner = null
            learnedHeld = code
            settings.wheelMenuKey = code
            menu.close()
            menu.keys = keys()
            handler.post { learn(code) }
            return true
        }
        return menu.onKey(code, down, event.repeatCount)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun stop() {
        if (running !== this) return
        running = null
        learner = null
        handler.removeCallbacks(closeIdle)
        menu.close()
    }

    private fun keys() = WheelMenu.Keys(menuKey(settings), WheelKeys.PREVIOUS, WheelKeys.NEXT, WheelKeys.OK)

    private fun items(): List<WheelMenu.Item> {
        val card = settings.horizonLeftCard
        val power = settings.horizonPowerStyle
        val layout = settings.horizonMapLayout
        val list = mutableListOf(
            WheelMenu.Item(getString(R.string.wheel_menu_left_card), getString(HorizonOptions.find(HorizonOptions.LEFT_CARDS, card).label)) {
                settings.horizonLeftCard = HorizonOptions.next(HorizonOptions.LEFT_CARDS, card)
            },
            WheelMenu.Item(getString(R.string.wheel_menu_power), getString(HorizonOptions.find(HorizonOptions.POWER_STYLES, power).label)) {
                settings.horizonPowerStyle = HorizonOptions.next(HorizonOptions.POWER_STYLES, power)
            },
            // Switches live, also while a phone is projecting.
            WheelMenu.Item(getString(R.string.wheel_menu_map_layout), getString(HorizonOptions.find(HorizonOptions.MAP_LAYOUTS, layout).label)) {
                HorizonOptions.setMapLayout(this, HorizonOptions.next(HorizonOptions.MAP_LAYOUTS, layout))
            })
        // The map can only be switched while a phone is projecting.
        if (ClusterMapToggle.inSession()) {
            val on = ClusterMapToggle.isOn(this)
            list += WheelMenu.Item(getString(R.string.wheel_menu_map), getString(if (on) R.string.wheel_menu_on else R.string.wheel_menu_off)) {
                ClusterMapToggle.toggle(this)
            }
        }
        return list
    }

    private fun onChange(state: WheelMenu.State) {
        try {
            file.write(state, settings.horizonLeftCard, settings.horizonPowerStyle, settings.horizonMapLayout)
        } catch (e: Exception) {
            AppLog.w("WheelMenu: could not write the menu file: ${e.message}")
        }
        handler.removeCallbacks(closeIdle)
        if (state.open) handler.postDelayed(closeIdle, IDLE_CLOSE_MS)
    }

    companion object {
        private const val IDLE_CLOSE_MS = 10_000L

        @Volatile private var running: WheelMenuService? = null

        /** Set by the BYD panel to take the next key pressed as the menu key; called on the main thread. */
        @Volatile var learner: ((Int) -> Unit)? = null

        val isRunning: Boolean get() = running != null

        /** The key that opens the menu: the driver's choice, else BYD's programmable key. */
        fun menuKey(settings: Settings): Int = settings.wheelMenuKey.takeIf { it != 0 } ?: WheelKeys.CUSTOM
    }
}
