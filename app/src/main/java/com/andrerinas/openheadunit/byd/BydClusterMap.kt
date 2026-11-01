package com.andrerinas.openheadunit.byd

import android.content.Context
import com.andrerinas.openheadunit.aap.cluster.ClusterMapArea
import com.andrerinas.openheadunit.byd.theme.HorizonOptions
import com.andrerinas.openheadunit.utils.Settings

/**
 * The phone's map on the Song Pro's own cluster display, as tested on the car. The BYD panel turns on
 * the cluster output (the floating map button and the theme's now-playing feed come with it) and
 * fits the map to the installed cluster theme: the factory panel's free band, Horizon's window or,
 * if chosen, the whole panel. The map itself stays off until the driver turns it on with the
 * floating button.
 */
object BydClusterMap {

    /** The map fills Horizon's centre window legibly at this density (tested on the car). */
    const val HORIZON_MAP_DPI = 240

    /** The Horizon layout the current connection was set up for; null between connections. */
    @Volatile var sessionLayout: String? = null
        private set

    /** Asks the phone for its cluster stream and sends it to the cluster display output. */
    fun enable(settings: Settings) {
        settings.enableClusterVideo = true
        settings.showClusterOnClusterDisplay = true
    }

    /**
     * Fits the map to the factory panel's free band or, with Horizon, to its window or the whole
     * panel (settings.horizonMapLayout), with the edges the theme covers as content insets.
     * The phone's stream is negotiated per connection, so this takes effect at the next one.
     */
    fun fitTo(settings: Settings, horizon: Boolean) {
        enable(settings)
        settings.horizonInstalled = horizon
        val full = horizon && settings.horizonMapLayout == "full"
        settings.clusterDisplayRect = when {
            !horizon -> ClusterMapArea.FACTORY_FREE_BAND
            full -> ClusterMapArea.HORIZON_FULL
            else -> ClusterMapArea.HORIZON_MAP_VIDEO
        }
        settings.clusterContentInsets = when {
            !horizon -> ""
            full -> ClusterMapArea.HORIZON_FULL_INSETS
            else -> ClusterMapArea.HORIZON_MAP_INSETS
        }
        settings.clusterVideoDpi = if (horizon) HORIZON_MAP_DPI else 0
    }

    /** A connection began: the theme is told which layout its stream was set up for. */
    fun onSessionStart(context: Context) {
        sessionLayout = Settings(context).horizonMapLayout
        HorizonOptions.publish(context)
    }

    fun onSessionEnd() {
        sessionLayout = null
    }
}
