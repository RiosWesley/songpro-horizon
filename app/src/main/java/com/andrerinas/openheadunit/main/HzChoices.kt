package com.andrerinas.openheadunit.main

import android.view.LayoutInflater
import android.view.View
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.theme.HorizonOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * A single choice on Song Pro Horizon's screens: one segment per option in a toggle group, the
 * current one checked, [choose] called with the key the driver picks (not for the initial check).
 */
internal object HzChoices {

    fun <K> bind(group: MaterialButtonToggleGroup, options: List<Pair<K, String>>, current: K, choose: (K) -> Unit) {
        group.clearOnButtonCheckedListeners()
        group.removeAllViews()
        val inflater = LayoutInflater.from(group.context)
        val keys = options.map { (key, label) ->
            val button = inflater.inflate(R.layout.item_hz_segment, group, false) as MaterialButton
            button.id = View.generateViewId()
            button.text = label
            group.addView(button)
            button.id to key
        }
        keys.firstOrNull { it.second == current }?.let { group.check(it.first) }
        var chosen = current
        group.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val key = keys.firstOrNull { it.first == id }?.second ?: return@addOnButtonCheckedListener
            if (key == chosen) return@addOnButtonCheckedListener
            chosen = key
            choose(key)
        }
    }

    fun bind(group: MaterialButtonToggleGroup, options: List<HorizonOptions.Option>, current: String, choose: (String) -> Unit) =
        bind(group, options.map { it.key to group.context.getString(it.label) }, current, choose)
}
