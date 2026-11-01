package com.andrerinas.openheadunit.byd.wheel

import android.view.KeyEvent

/**
 * The BYD steering wheel keys as Android sees them: the "simulate-keys" input device and its
 * /system/usr/keylayout/simulate-keys.kl on the Song Pro. ◁ ▷ are the standard previous/next
 * track keys; BYD's own keys (roller, MODE, the programmable key) are not in the SDK, so they are
 * looked up by the names the key layout uses. The cluster menu key never reaches Android.
 */
internal object WheelKeys {

    const val PREVIOUS = KeyEvent.KEYCODE_MEDIA_PREVIOUS
    const val NEXT = KeyEvent.KEYCODE_MEDIA_NEXT

    /** The roller press (or play/pause), which confirms in the menu. */
    val OK: Set<Int> by lazy {
        setOf(byd("AUTO_VOLUME_MUTE"), byd("AUTO_MEDIA_PLAY_PAUSE"), KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) - KeyEvent.KEYCODE_UNKNOWN
    }

    /** The programmable key, short press: opens the menu unless the driver picked another key. */
    val CUSTOM: Int by lazy { byd("AUTO_CUSTOM_KEY") }

    fun byd(name: String): Int = KeyEvent.keyCodeFromString("KEYCODE_$name")

    fun name(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
}
