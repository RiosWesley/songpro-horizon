package com.andrerinas.openheadunit.main

import android.view.View
import android.widget.TextView
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.theme.HorizonOptions
import com.andrerinas.openheadunit.utils.Settings
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The BYD panel's "power gauge" row: shows the Horizon gauge in use and picks another. */
internal class BydPowerStyleRow(private val root: View) {
    private val context = root.context
    private val settings = Settings(context.applicationContext)
    private val label: TextView = root.findViewById(R.id.byd_power_style)

    init {
        show()
        root.findViewById<MaterialButton>(R.id.byd_power_style_change).setOnClickListener { choose() }
    }

    private fun show() {
        val style = HorizonOptions.find(HorizonOptions.POWER_STYLES, settings.horizonPowerStyle)
        label.text = context.getString(R.string.byd_power_style, context.getString(style.label))
    }

    private fun choose() {
        val styles = HorizonOptions.POWER_STYLES
        val current = styles.indexOf(HorizonOptions.find(styles, settings.horizonPowerStyle))
        MaterialAlertDialogBuilder(context, R.style.DarkAlertDialog)
            .setTitle(R.string.wheel_menu_power)
            .setSingleChoiceItems(styles.map { context.getString(it.label) }.toTypedArray(), current) { dialog, which ->
                settings.horizonPowerStyle = styles[which].key
                HorizonOptions.publish(context.applicationContext)
                show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
