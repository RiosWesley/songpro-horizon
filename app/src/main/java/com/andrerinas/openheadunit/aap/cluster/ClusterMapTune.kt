package com.andrerinas.openheadunit.aap.cluster

import android.graphics.Rect
import com.andrerinas.openheadunit.utils.Settings
import kotlin.math.roundToInt

/**
 * How one Horizon map layout shows the phone's 1280x720 picture: its scale (from the top-left
 * corner) and offset on the cluster display, and the part of the picture drawn, in picture pixels.
 * Each layout keeps its own, fine-tuned in Painel → Ajuste fino do mapa; unset is [default].
 * Scaling is always even: stretching one way only would distort the map.
 */
internal data class ClusterMapTune(
    val scale: Float,
    val x: Int,
    val y: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /** "scale,x,y" for cluster_map_placement (ClusterMapArea.parsePlacement). */
    val placement: String get() = "$scale,$x,$y"

    val visible: Rect get() = Rect(left, top, right, bottom)

    /** Inside the picture, at least [MIN_SIDE] px each way, the scale in [MIN_SCALE]..[MAX_SCALE]. */
    fun clamped(): ClusterMapTune {
        val l = left.coerceIn(0, PICTURE_WIDTH - MIN_SIDE)
        val t = top.coerceIn(0, PICTURE_HEIGHT - MIN_SIDE)
        return ClusterMapTune(
            scale = ((scale.coerceIn(MIN_SCALE, MAX_SCALE) * 1000).roundToInt() / 1000f),
            x = x.coerceIn(-PICTURE_WIDTH, PICTURE_WIDTH), y = y.coerceIn(-PICTURE_HEIGHT, PICTURE_HEIGHT),
            left = l, top = t,
            right = right.coerceIn(l + MIN_SIDE, PICTURE_WIDTH), bottom = bottom.coerceIn(t + MIN_SIDE, PICTURE_HEIGHT))
    }

    private fun format() = "$scale,$x,$y,$left,$top,$right,$bottom"

    companion object {
        const val PICTURE_WIDTH = 1280
        const val PICTURE_HEIGHT = 720
        const val MIN_SCALE = 0.3f
        const val MAX_SCALE = 2f
        private const val MIN_SIDE = 40

        /**
         * The phone always draws its turn and arrival cards, centring the arrow in the content area
         * (the whole picture with Horizon) and keeping the cards about 98 px inside its top and
         * bottom edges (measured on the car at 200 dpi, 2026-11-01): the arrow on row 360, the
         * cards on rows ~98..169 and ~551..622. Only the rows between them are drawn by default.
         */
        private const val CARD_FREE_TOP = 175
        private const val CARD_FREE_BOTTOM = 545

        /**
         * - Full screen: the picture at full size, the arrow midway between the speed band (y 113)
         *   and the bottom bar (y 393); the band covers y 68..438.
         * - Window: Horizon's centre opening (390,68,500,344; clear 450..830 x 108..383, from
         *   HorizonGeometry.js map.video and its fades). At 0.93 the band covers the whole opening,
         *   fades included; at 0.8 its top and bottom fades showed black (seen in the mock).
         * - Side: the opening on the right (863,69,305,324; clear 911..1161 x 107..373, from
         *   HorizonGeometry.js side.video and side.feather): at 0.876 the band covers it with its
         *   feather, the arrow under the clear part's centre.
         */
        fun default(layout: String): ClusterMapTune = when (layout) {
            "full" -> ClusterMapTune(1f, 0, -107, 0, CARD_FREE_TOP, PICTURE_WIDTH, CARD_FREE_BOTTOM)
            "side" -> ClusterMapTune(0.876f, 475, -84, 0, CARD_FREE_TOP, PICTURE_WIDTH, CARD_FREE_BOTTOM)
            else -> ClusterMapTune(0.93f, 45, -95, 0, CARD_FREE_TOP, PICTURE_WIDTH, CARD_FREE_BOTTOM)
        }

        fun of(settings: Settings, layout: String): ClusterMapTune = parse(settings.clusterMapTune(layout)) ?: default(layout)

        /** Saves [tune] for [layout]; null goes back to the default. */
        fun save(settings: Settings, layout: String, tune: ClusterMapTune?) =
            settings.setClusterMapTune(layout, tune?.clamped()?.format() ?: "")

        private fun parse(setting: String): ClusterMapTune? {
            val n = setting.split(",").map { it.trim() }
            if (n.size != 7) return null
            val scale = n[0].toFloatOrNull() ?: return null
            val ints = n.drop(1).map { it.toIntOrNull() ?: return null }
            return ClusterMapTune(scale, ints[0], ints[1], ints[2], ints[3], ints[4], ints[5]).clamped()
        }
    }
}
