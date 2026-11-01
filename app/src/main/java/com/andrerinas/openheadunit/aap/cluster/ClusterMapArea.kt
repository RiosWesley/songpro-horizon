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
     * With Horizon the phone always draws for the whole display, so its three layouts switch live:
     * only where the picture sits ("scale,x,y": scaled from its top-left corner, then moved; see
     * parsePlacement) and the content insets change. The insets are the clear part of Horizon's
     * opening, mapped back into the phone's picture, so its cards land there; the car arrow, drawn
     * low in the frame, lands inside the opening even if the phone ignores them.
     *
     * Full screen: the picture as is; the cards kept out of the band at the top (speed, gear,
     * power) and the bottom bar (HorizonGeometry.js `full`, at 2/3).
     */
    const val HORIZON_FULL = "0,0,1280,480"
    const val HORIZON_FULL_INSETS = "0,100,0,84"

    /**
     * Window: Horizon's centre opening (390,68,500,344; clear 450..830 x 108..383, from
     * HorizonGeometry.js map.video and its fades). The picture at 0.8 shows more of the map than
     * at full size, centred, with the arrow low in the opening.
     */
    const val HORIZON_WINDOW_PLACEMENT = "0.8,128,34"
    const val HORIZON_WINDOW_INSETS = "402,92,402,44"

    /**
     * Side (the second layout): Horizon's opening on the right (827,67,341,333; clear 869..1144 x
     * 104..371, from HorizonGeometry.js side.video and side.feather), the picture at 0.72.
     */
    const val HORIZON_SIDE_PLACEMENT = "0.72,536,56"
    const val HORIZON_SIDE_INSETS = "463,66,435,43"

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

    /** "scale,x,y" -> the picture's scale (from its top-left corner) and offset; blank: as is. */
    fun parsePlacement(setting: String): Triple<Float, Float, Float> {
        val n = setting.split(",").map { it.trim().toFloatOrNull() }
        if (n.size != 3 || n.any { it == null } || n[0]!! <= 0f) return Triple(1f, 0f, 0f)
        return Triple(n[0]!!, n[1]!!, n[2]!!)
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
