package com.andrerinas.openheadunit.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.andrerinas.openheadunit.R

/**
 * One adjustable value on Song Pro Horizon's screens: a label, its value and - / + buttons that call
 * [step] with -1 or +1. Held, a button repeats.
 */
internal class HzStepper private constructor(row: View, label: Int, private val show: () -> String, private val step: (Int) -> Unit) {

    private val value: TextView = row.findViewById(R.id.hz_stepper_value)

    init {
        row.findViewById<TextView>(R.id.hz_stepper_label).setText(label)
        repeatWhileHeld(row.findViewById(R.id.hz_stepper_minus)) { step(-1) }
        repeatWhileHeld(row.findViewById(R.id.hz_stepper_plus)) { step(1) }
        refresh()
    }

    fun refresh() {
        value.text = show()
    }

    @SuppressLint("ClickableViewAccessibility") // the touch listener only stops the repeat; clicks stay clicks
    private fun repeatWhileHeld(button: View, action: () -> Unit) {
        val repeat = object : Runnable {
            override fun run() {
                action()
                button.postDelayed(this, REPEAT_MS)
            }
        }
        button.setOnClickListener { action() }
        button.setOnLongClickListener { repeat.run(); true }
        button.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) button.removeCallbacks(repeat)
            false
        }
    }

    companion object {
        private const val REPEAT_MS = 80L

        /** Adds a stepper row to [parent]. */
        fun add(parent: ViewGroup, label: Int, show: () -> String, step: (Int) -> Unit): HzStepper {
            val row = LayoutInflater.from(parent.context).inflate(R.layout.item_hz_stepper, parent, false)
            parent.addView(row)
            return HzStepper(row, label, show, step)
        }
    }
}
