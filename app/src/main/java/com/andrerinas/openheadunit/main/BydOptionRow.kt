package com.andrerinas.openheadunit.main

import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.theme.HorizonOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A BYD panel row for one Horizon choice: "<what>: <current value>" and a button that picks another
 * from a list (the power gauge, the map layout).
 */
internal class BydOptionRow(
    root: View,
    @IdRes labelId: Int,
    @IdRes buttonId: Int,
    @StringRes private val title: Int,
    @StringRes private val format: Int,
    private val options: List<HorizonOptions.Option>,
    private val current: () -> String,
    private val choose: (String) -> Unit,
) {
    private val context = root.context
    private val label: TextView = root.findViewById(labelId)

    init {
        show()
        root.findViewById<MaterialButton>(buttonId).setOnClickListener { pick() }
    }

    private fun show() {
        label.text = context.getString(format, context.getString(HorizonOptions.find(options, current()).label))
    }

    private fun pick() {
        val index = options.indexOf(HorizonOptions.find(options, current()))
        MaterialAlertDialogBuilder(context, R.style.DarkAlertDialog)
            .setTitle(title)
            .setSingleChoiceItems(options.map { context.getString(it.label) }.toTypedArray(), index) { d, which ->
                choose(options[which].key)
                show()
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
