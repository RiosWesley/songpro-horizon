package com.andrerinas.openheadunit.byd.theme

import android.content.Context
import androidx.annotation.StringRes
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.cluster.ClusterDisplayOutput
import com.andrerinas.openheadunit.byd.BydClusterMap
import com.andrerinas.openheadunit.byd.wheel.HorizonMenuFile
import com.andrerinas.openheadunit.byd.wheel.WheelMenu
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings

/**
 * What the driver chooses about the Horizon scene, from the wheel menu or the BYD panel: the left
 * card, the power gauge, the map layout and whether the Simples slot shows Horizon at all. The keys
 * are the ones the theme reads from menu.json.
 */
object HorizonOptions {

    class Option(val key: String, @StringRes val label: Int)

    val LEFT_CARDS = listOf(
        Option("auto", R.string.wheel_menu_card_auto),
        Option("music", R.string.wheel_menu_card_music),
        Option("trip", R.string.wheel_menu_card_trip))

    val POWER_STYLES = listOf(
        Option("ring", R.string.power_style_ring),
        Option("segments", R.string.power_style_segments),
        Option("arc", R.string.power_style_arc),
        Option("column", R.string.power_style_column),
        Option("wave", R.string.power_style_wave))

    val MAP_LAYOUTS = listOf(
        Option("window", R.string.map_layout_window),
        Option("side", R.string.map_layout_side),
        Option("full", R.string.map_layout_full))

    /** What the cluster's Simples slot shows: Horizon, or the factory Simples it replaced. */
    val SKINS = listOf(
        Option("horizon", R.string.skin_horizon),
        Option("factory", R.string.skin_factory))

    fun find(options: List<Option>, key: String): Option = options.firstOrNull { it.key == key } ?: options[0]

    fun next(options: List<Option>, key: String): String = options[(options.indexOf(find(options, key)) + 1) % options.size].key

    /**
     * A new map layout, applied live: the map refitted and, during a connection, moved on the
     * cluster with the phone told where its cards go. The theme follows with the next menu.json.
     */
    fun setMapLayout(context: Context, key: String) {
        val settings = Settings(context)
        settings.horizonMapLayout = key
        BydClusterMap.fitTo(settings, horizon = true)
        ClusterDisplayOutput.relayout()
    }

    /** Hands the current choices to the theme now, with the menu closed (a change made in the app). */
    fun publish(context: Context) {
        val settings = Settings(context)
        try {
            HorizonMenuFile().write(WheelMenu.State(false, 0, emptyList()), settings)
        } catch (e: Exception) {
            AppLog.w("HorizonOptions: could not write the menu file: ${e.message}")
        }
    }
}
