package com.andrerinas.openheadunit.aap.cluster

import com.andrerinas.openheadunit.aap.protocol.messages.ClusterUiConfigRequest
import com.andrerinas.openheadunit.utils.Settings

/**
 * What the phone is told about the cluster picture once it feeds it: the margins ServiceDiscovery
 * announced, split evenly as they are cropped, and the content insets of cluster_content_insets.
 * Insets are given on top of the margins (from the stream's own edge), so whichever way the phone
 * counts them its cards land inside the clear area, never under the theme's fades.
 */
internal object ClusterUiConfig {

    /** Null when no insets are set: the phone then lays out as before. */
    fun request(settings: Settings): ClusterUiConfigRequest? {
        val insets = ClusterMapArea.parseInsets(settings.clusterContentInsets) ?: return null
        val area = ClusterMapArea.resolve(settings.clusterDisplayRect, ClusterMapArea.FALLBACK_WIDTH, ClusterMapArea.FALLBACK_HEIGHT)
        val geometry = ClusterVideoGeometry.forArea(area.width(), area.height()) ?: return null
        val mx = geometry.marginWidth / 2
        val my = geometry.marginHeight / 2
        return ClusterUiConfigRequest(
            intArrayOf(mx, my, mx, my),
            intArrayOf(mx + insets[0], my + insets[1], mx + insets[2], my + insets[3]))
    }
}
