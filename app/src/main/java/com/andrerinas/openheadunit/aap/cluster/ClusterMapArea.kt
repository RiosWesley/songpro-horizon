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
 * app's overlay. A box running off the display is clamped to it, except that its bottom may run
 * [HORIZON_OVERSCAN] px past it.
 */
internal object ClusterMapArea {

    /**
     * With Horizon the phone always draws one picture, 1280x720 (the whole cluster stream, no margins),
     * so its three layouts switch live: only where the picture sits ("scale,x,y": scaled from its
     * top-left corner, then moved; see parsePlacement) changes.
     *
     * Measured on the car with a route (2026-11-01, ClusterLayoutProbe): the phone centres the car
     * arrow in the content area and keeps its turn card's top and arrival card's bottom about 98 px
     * inside the area's top and bottom edges (each card about 71 px tall). An area too small for the
     * cards is not taken at all (the arrow drops to the phone's own default, ~82% down), so the cards
     * cannot be turned off; and the head unit already shows the turn, distance and time. So the area
     * is the whole picture: the arrow sits on row 360, the cards on rows ~98..169 and ~551..622, and
     * only the rows between them ([HORIZON_CARD_FREE_TOP]..[HORIZON_CARD_FREE_BOTTOM]) are drawn.
     * Each layout places that band so the arrow lands in the middle of Horizon's opening.
     *
     * Full screen: the picture at full size, the arrow midway between the speed band (y 113) and
     * the bottom bar (y 393); the band covers y 68..438.
     */
    const val HORIZON_OVERSCAN = 240
    const val HORIZON_FULL = "0,0,1280,720"
    const val HORIZON_INSETS = "1,1,1,1"
    const val HORIZON_CARD_FREE_TOP = 175
    const val HORIZON_CARD_FREE_BOTTOM = 545
    const val HORIZON_FULL_PLACEMENT = "1,0,-107"

    /**
     * Window: Horizon's centre opening (390,68,500,344; clear 450..830 x 108..383, from
     * HorizonGeometry.js map.video and its fades). The band at 0.93 covers the whole opening, fades
     * included (y 68..412); at 0.8 its top and bottom fades showed black (seen in the mock).
     */
    const val HORIZON_WINDOW_PLACEMENT = "0.93,45,-95"

    /**
     * Side (the second layout): Horizon's opening on the right (863,69,305,324; clear 911..1161 x
     * 107..373, from HorizonGeometry.js side.video and side.feather): the band at 0.876 covers the
     * opening with its feather (y 69..393), the arrow under the clear part's centre.
     */
    const val HORIZON_SIDE_PLACEMENT = "0.876,475,-84"

    /** Debug builds: the card-free rows set live by ClusterLayoutProbe, or null for the constants. */
    @Volatile var probeCardFreeRows: IntRange? = null

    /** The rows of the phone's picture to draw: all of them without Horizon. */
    fun cardFreeRows(settings: Settings): IntRange? =
        if (settings.clusterDisplayRect != HORIZON_FULL) null
        else probeCardFreeRows ?: HORIZON_CARD_FREE_TOP..HORIZON_CARD_FREE_BOTTOM

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

    // The bottom may run past the display by the overscan, where the phone's black strip goes.
    private fun clamp(r: Rect, w: Int, h: Int) = Rect(r.left.coerceAtMost(w), r.top.coerceAtMost(h),
        r.right.coerceAtMost(w), r.bottom.coerceAtMost(h + HORIZON_OVERSCAN))

    private fun centred(w: Int, h: Int): Rect {
        val boxH = h * 8 / 10
        val boxW = (boxH * 5 / 3).coerceAtMost(w)
        val left = (w - boxW) / 2
        val top = (h - boxH) / 2
        return Rect(left, top, left + boxW, top + boxH)
    }
}
