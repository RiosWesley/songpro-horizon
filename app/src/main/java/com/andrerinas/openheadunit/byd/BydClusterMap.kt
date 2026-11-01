package com.andrerinas.openheadunit.byd

import android.content.Context
import com.andrerinas.openheadunit.aap.cluster.ClusterMapArea
import com.andrerinas.openheadunit.byd.theme.HorizonOptions
import com.andrerinas.openheadunit.utils.Settings

/**
 * The phone's map on the Song Pro's own cluster display, as tested on the car. The BYD panel turns on
 * the cluster output (the theme's now-playing feed comes with it) and
 * fits the map to the installed cluster theme: the factory panel's free band or, with Horizon, its
 * centre window, its right side or the whole panel. The map itself stays off until the driver turns
 * it on from the wheel menu.
 */
object BydClusterMap {

    /**
     * The phone lays its cluster map out for the whole panel at this density; 240 was too large
     * once the window showed only part of it (seen on the car), and Electro runs Waze there at 178.
     */
    const val HORIZON_MAP_DPI = 200

    /** Asks the phone for its cluster stream and sends it to the cluster display output. */
    fun enable(settings: Settings) {
        settings.enableClusterVideo = true
        settings.showClusterOnClusterDisplay = true
    }

    /**
     * Fits the map to the factory panel's free band or, with Horizon, to its centre window, its
     * right side or the whole panel (settings.horizonMapLayout). With Horizon the phone always
     * draws for the whole panel, so a layout change applies live (ClusterDisplayOutput.relayout);
     * a change of theme waits for the next connection.
     */
    fun fitTo(settings: Settings, horizon: Boolean) {
        enable(settings)
        settings.horizonInstalled = horizon
        val layout = if (horizon) settings.horizonMapLayout else ""
        settings.clusterDisplayRect = if (horizon) ClusterMapArea.HORIZON_FULL else ClusterMapArea.FACTORY_FREE_BAND
        settings.clusterContentInsets = when {
            !horizon -> ""
            layout == "full" -> ClusterMapArea.HORIZON_FULL_INSETS
            layout == "side" -> ClusterMapArea.HORIZON_SIDE_INSETS
            else -> ClusterMapArea.HORIZON_WINDOW_INSETS
        }
        settings.clusterMapPlacement = when {
            !horizon || layout == "full" -> ""
            layout == "side" -> ClusterMapArea.HORIZON_SIDE_PLACEMENT
            else -> ClusterMapArea.HORIZON_WINDOW_PLACEMENT
        }
        settings.clusterVideoDpi = if (horizon) HORIZON_MAP_DPI else 0
    }

    /** A connection began: the theme is told the current choices, and its parking sensors run. */
    fun onSessionStart(context: Context) {
        HorizonOptions.publish(context)
        ParkSensorsFeed.ensureRunning(context)
    }
}
