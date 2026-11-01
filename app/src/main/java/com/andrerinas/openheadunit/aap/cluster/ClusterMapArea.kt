package com.andrerinas.openheadunit.aap.cluster

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import com.andrerinas.openheadunit.utils.Settings

/**
 * Where the cluster map is drawn on the cluster display, in display pixels.
 *
 * The setting is "x,y,width,height". Blank or invalid means a centred box at 5:3 (the 800x480 stream)
 * covering 80% of the display height, which leaves the sides free for the native gauges or another
 * app's overlay. A box running off the display is clamped to it.
 */
internal object ClusterMapArea {

    /**
     * The Horizon cluster theme's map window (horizon/qml/HorizonGeometry.js, map.video at 2/3):
     * the clear hole (390,100,500,292) and 32 px more above and 20 below, which run on under the
     * theme's fades. The insets keep the phone's cards in the clear part.
     */
    const val HORIZON_MAP_VIDEO = "390,68,500,344"
    const val HORIZON_MAP_INSETS = "0,32,0,20"

    /**
     * Horizon full screen: the whole display, the phone's cards kept out of the speed band at the
     * top, the bottom bar and the telltale columns (HorizonGeometry.js `full`).
     */
    const val HORIZON_FULL = "0,0,1280,480"
    const val HORIZON_FULL_INSETS = "70,100,70,86"

    /** Without a theme, the whole band above the factory 8.8" bottom bar. */
    const val FACTORY_FREE_BAND = "0,0,1280,420"

    /** Assumed when the cluster display is not up yet (BYD DiLink 3, 8.8" cluster). */
    const val FALLBACK_WIDTH = 1280
    const val FALLBACK_HEIGHT = 480

    /** The map area on the current cluster display, or on the fallback size if there is none yet. */
    fun current(context: Context): Rect {
        val dm = context.getSystemService(DisplayManager::class.java)
        val size = dm?.let { ClusterDisplayLocator.find(it) }?.let { d ->
            Point().also {
                @Suppress("DEPRECATION")
                d.getRealSize(it)
            }
        } ?: Point(FALLBACK_WIDTH, FALLBACK_HEIGHT)
        return resolve(Settings(context).clusterDisplayRect, size.x, size.y)
    }

    fun resolve(setting: String, displayWidth: Int, displayHeight: Int): Rect {
        val parsed = parse(setting)
        return if (parsed != null) clamp(parsed, displayWidth, displayHeight)
        else centred(displayWidth, displayHeight)
    }

    /** "left,top,right,bottom" -> the four values, or null when blank, invalid or all zero. */
    fun parseInsets(setting: String): IntArray? {
        val n = setting.split(",").map { it.trim().toIntOrNull() ?: return null }
        if (n.size != 4 || n.any { it < 0 } || n.all { it == 0 }) return null
        return n.toIntArray()
    }

    private fun parse(setting: String): Rect? {
        val n = setting.split(",").map { it.trim().toIntOrNull() ?: return null }
        if (n.size != 4 || n[2] <= 0 || n[3] <= 0 || n[0] < 0 || n[1] < 0) return null
        return Rect(n[0], n[1], n[0] + n[2], n[1] + n[3])
    }

    private fun clamp(r: Rect, w: Int, h: Int) =
        Rect(r.left.coerceAtMost(w), r.top.coerceAtMost(h), r.right.coerceAtMost(w), r.bottom.coerceAtMost(h))

    private fun centred(w: Int, h: Int): Rect {
        val boxH = h * 8 / 10
        val boxW = (boxH * 5 / 3).coerceAtMost(w)
        val left = (w - boxW) / 2
        val top = (h - boxH) / 2
        return Rect(left, top, left + boxW, top + boxH)
    }
}
