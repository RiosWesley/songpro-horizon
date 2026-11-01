package com.andrerinas.openheadunit.byd

import com.andrerinas.openheadunit.aap.cluster.ClusterMapArea
import com.andrerinas.openheadunit.utils.Settings

/**
 * The phone's map on the Song Pro's own cluster display, as tested on the car. The BYD panel turns on
 * the cluster output (the floating map button and the theme's now-playing feed come with it) and
 * fits the map to the installed cluster theme. The map itself stays off until the driver turns it
 * on with the floating button.
 */
object BydClusterMap {

    /** The map fills Horizon's centre window legibly at this density (tested on the car). */
    const val HORIZON_MAP_DPI = 240

    /** Asks the phone for its cluster stream and sends it to the cluster display output. */
    fun enable(settings: Settings) {
        settings.enableClusterVideo = true
        settings.showClusterOnClusterDisplay = true
    }

    /** Fits the map to Horizon's centre window, or to the free band of the factory panel. */
    fun fitTo(settings: Settings, horizon: Boolean) {
        enable(settings)
        settings.clusterDisplayRect = if (horizon) ClusterMapArea.HORIZON_MAP_HOLE else ClusterMapArea.FACTORY_FREE_BAND
        settings.clusterVideoDpi = if (horizon) HORIZON_MAP_DPI else 0
    }
}
