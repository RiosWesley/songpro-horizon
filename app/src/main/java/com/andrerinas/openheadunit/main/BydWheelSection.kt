package com.andrerinas.openheadunit.main

import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.wheel.WheelKeys
import com.andrerinas.openheadunit.byd.wheel.WheelMenuService
import com.andrerinas.openheadunit.byd.wheel.WheelMenuSwitch
import com.andrerinas.openheadunit.utils.Settings
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/** The steering wheel menu (Volante): the switch, the menu key and learning a new one. */
internal class BydWheelSection(
    private val root: View,
    private val scope: LifecycleCoroutineScope,
    private val log: (String) -> Unit,
) {
    private val context = root.context.applicationContext
    private val settings = Settings(context)
    private val switch: CompoundButton = root.findViewById(R.id.byd_wheel_switch)
    private val keyLabel: TextView = root.findViewById(R.id.byd_wheel_key)
    private val learn: MaterialButton = root.findViewById(R.id.byd_wheel_learn)
    private var showing = false

    init {
        show()
        switch.setOnCheckedChangeListener { _, on -> if (!showing) setOn(on) }
        learn.setOnClickListener { learnKey() }
    }

    /** Drops a pending "choose key" when the screen goes away. */
    fun release() {
        WheelMenuService.learner = null
    }

    private fun show() {
        showing = true
        switch.isChecked = WheelMenuSwitch.isOn(context)
        showing = false
        keyLabel.text = WheelKeys.name(WheelMenuService.menuKey(settings))
    }

    private fun setOn(on: Boolean) {
        switch.isEnabled = false
        scope.launch {
            WheelMenuSwitch.set(context, on)?.let(log)
            switch.isEnabled = true
            show()
        }
    }

    private fun learnKey() {
        if (!WheelMenuService.isRunning) {
            log(root.context.getString(R.string.byd_wheel_needs_on))
            return
        }
        log(root.context.getString(R.string.byd_wheel_learning))
        WheelMenuService.learner = { code ->
            log(root.context.getString(R.string.byd_wheel_learned, WheelKeys.name(code)))
            show()
        }
    }
}
