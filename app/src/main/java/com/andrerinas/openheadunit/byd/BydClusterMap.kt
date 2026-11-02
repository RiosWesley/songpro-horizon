package com.andrerinas.openheadunit.byd

import android.content.Context
import com.andrerinas.openheadunit.aap.cluster.ClusterDisplayOutput
import com.andrerinas.openheadunit.aap.cluster.ClusterMapArea
import com.andrerinas.openheadunit.aap.cluster.ClusterMapTune
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
        settings.clusterContentInsets = if (horizon) ClusterMapArea.HORIZON_INSETS else ""
        settings.clusterMapPlacement = if (horizon) ClusterMapTune.of(settings, layout).placement else ""
        settings.clusterVideoDpi = if (horizon) mapDpi(settings) else 0
    }

    /** The density the phone draws the Horizon map with: the driver's choice, else [HORIZON_MAP_DPI]. */
    fun mapDpi(settings: Settings): Int = settings.horizonMapDpi.takeIf { it > 0 } ?: HORIZON_MAP_DPI

    /**
     * After a change in Painel → Ajuste fino do mapa: the current layout's placement and visible part
     * apply at once, the density from the next connection. Without Horizon nothing changes now.
     */
    fun applyTune(settings: Settings) {
        if (!settings.horizonInstalled) return
        settings.clusterMapPlacement = ClusterMapTune.of(settings, settings.horizonMapLayout).placement
        settings.clusterVideoDpi = mapDpi(settings)
        ClusterDisplayOutput.reposition()
    }

    /** A connection began: the theme is told the current choices, and its parking sensors run. */
    fun onSessionStart(context: Context) {
        HorizonOptions.publish(context)
        ParkSensorsFeed.ensureRunning(context)
    }
}
