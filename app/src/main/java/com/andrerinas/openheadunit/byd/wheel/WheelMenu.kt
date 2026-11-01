package com.andrerinas.openheadunit.byd.wheel

/**
 * The Horizon menu the steering wheel drives, and which key presses it takes.
 *
 * The menu key opens and closes it. While it is open, ◁ ▷ move between items and OK changes the
 * selected one; everything else (volume, calls, voice, MODE) passes through. While it is closed,
 * only the menu key is taken, so the media keys keep skipping tracks. A key taken on press is also
 * taken on release, so the system never sees half a key.
 */
internal class WheelMenu(
    var keys: Keys,
    private val items: () -> List<Item>,
    private val onChange: (State) -> Unit,
) {

    class Keys(val menu: Int, val previous: Int, val next: Int, val ok: Set<Int>)

    /** One line of the menu: what it is, its current value, and how OK changes it. */
    class Item(val label: String, val value: String, val change: () -> Unit)

    class State(val open: Boolean, val selected: Int, val items: List<Item>)

    var open = false
        private set
    private var selected = 0
    private val held = HashSet<Int>()

    /** @return true when the key is the menu's, so the system must not act on it. */
    fun onKey(code: Int, down: Boolean, repeat: Int): Boolean {
        if (!down) return held.remove(code)
        val taken = code == keys.menu || (open && (code == keys.previous || code == keys.next || code in keys.ok))
        if (!taken) return false
        held.add(code)
        if (repeat == 0) act(code)
        return true
    }

    fun close() {
        if (!open) return
        open = false
        publish()
    }

    fun publish() {
        val list = items()
        selected = selected.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        onChange(State(open, selected, list))
    }

    private fun act(code: Int) {
        val list = items()
        when {
            code == keys.menu -> open = !open
            list.isEmpty() -> Unit
            code == keys.previous -> selected = (selected - 1 + list.size) % list.size
            code == keys.next -> selected = (selected + 1) % list.size
            else -> list[selected.coerceIn(0, list.size - 1)].change()
        }
        publish()
    }
}
