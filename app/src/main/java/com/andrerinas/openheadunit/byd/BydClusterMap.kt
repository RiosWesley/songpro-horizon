package com.andrerinas.openheadunit.byd

import android.content.Context
import com.andrerinas.openheadunit.aap.cluster.ClusterMapArea
import com.andrerinas.openheadunit.byd.theme.HorizonOptions
import com.andrerinas.openheadunit.utils.Settings

/**
 * The phone's map on the Song Pro's own cluster display, as tested on the car. The BYD panel turns on
 * the cluster output (the floating map button and the theme's now-playing feed come with it) and
 * fits the map to the installed cluster theme: the factory panel's free band or, with Horizon, its
 * window or the whole panel. The map itself stays off until the driver turns it on with the
 * floating button.
 */
object BydClusterMap {

    /** The map fills Horizon's centre window legibly at this density (tested on the car). */
    const val HORIZON_MAP_DPI = 240

    /** Asks the phone for its cluster stream and sends it to the cluster display output. */
    fun enable(settings: Settings) {
        settings.enableClusterVideo = true
        settings.showClusterOnClusterDisplay = true
    }

    /**
     * Fits the map to the factory panel's free band or, with Horizon, to its window or the whole
     * panel (settings.horizonMapLayout). With Horizon the phone always draws for the whole panel,
     * so a layout change applies live (ClusterDisplayOutput.relayout); a change of theme waits for
     * the next connection.
     */
    fun fitTo(settings: Settings, horizon: Boolean) {
        enable(settings)
        settings.horizonInstalled = horizon
        val full = horizon && settings.horizonMapLayout == "full"
        settings.clusterDisplayRect = if (horizon) ClusterMapArea.HORIZON_FULL else ClusterMapArea.FACTORY_FREE_BAND
        settings.clusterContentInsets = when {
            !horizon -> ""
            full -> ClusterMapArea.HORIZON_FULL_INSETS
            else -> ClusterMapArea.HORIZON_WINDOW_INSETS
        }
        settings.clusterMapShiftY = if (horizon && !full) ClusterMapArea.HORIZON_WINDOW_SHIFT_Y else 0
        settings.clusterVideoDpi = if (horizon) HORIZON_MAP_DPI else 0
    }

    /** A connection began: the theme is told the current choices. */
    fun onSessionStart(context: Context) = HorizonOptions.publish(context)
}
