package com.andrerinas.openheadunit.main

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.cluster.ClusterMapTune
import com.andrerinas.openheadunit.byd.BydClusterMap
import com.andrerinas.openheadunit.byd.theme.HorizonOptions
import com.andrerinas.openheadunit.utils.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Painel → Ajuste fino do mapa (advanced): each Horizon map layout's scale, position and visible part,
 * and the map density, to try on the car without a new build. The layout edited is the one the
 * cluster shows, so with the map on, each change shows at once; the density takes effect from the
 * next connection. Every value goes back to the one measured on the car ([ClusterMapTune.default]).
 */
class MapTuneFragment : Fragment(R.layout.fragment_hz_map_tune) {

    private lateinit var settings: Settings
    private lateinit var visibleSize: TextView
    private val steppers = mutableListOf<HzStepper>()
    private var coarse = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext().applicationContext
        settings = Settings(context)
        visibleSize = view.findViewById(R.id.hz_tune_visible_size)
        view.findViewById<View>(R.id.hz_tune_note).visibility = if (settings.horizonInstalled) View.GONE else View.VISIBLE

        HzChoices.bind(view.findViewById(R.id.hz_tune_layout_group), HorizonOptions.MAP_LAYOUTS, settings.horizonMapLayout) {
            HorizonOptions.setMapLayout(context, it)
            HorizonOptions.publish(context)
            refresh()
        }
        HzChoices.bind(view.findViewById(R.id.hz_tune_step_group),
            listOf(false to getString(R.string.hz_tune_step_fine), true to getString(R.string.hz_tune_step_coarse)), coarse) { coarse = it }

        val picture = view.findViewById<LinearLayout>(R.id.hz_tune_picture_card)
        steppers += HzStepper.add(picture, R.string.hz_tune_scale, { getString(R.string.hz_tune_scale_value, tune().scale) }) { d ->
            edit { it.copy(scale = it.scale + d * if (coarse) 0.05f else 0.005f) }
        }
        steppers += HzStepper.add(picture, R.string.hz_tune_x, { px(tune().x) }) { d -> edit { it.copy(x = it.x + d * pxStep()) } }
        steppers += HzStepper.add(picture, R.string.hz_tune_y, { px(tune().y) }) { d -> edit { it.copy(y = it.y + d * pxStep()) } }

        val visible = view.findViewById<LinearLayout>(R.id.hz_tune_visible_card)
        steppers += HzStepper.add(visible, R.string.hz_tune_cut_top, { px(tune().top) }) { d -> edit { it.copy(top = it.top + d * pxStep()) } }
        steppers += HzStepper.add(visible, R.string.hz_tune_cut_bottom, { px(ClusterMapTune.PICTURE_HEIGHT - tune().bottom) }) { d ->
            edit { it.copy(bottom = it.bottom - d * pxStep()) }
        }
        steppers += HzStepper.add(visible, R.string.hz_tune_cut_left, { px(tune().left) }) { d -> edit { it.copy(left = it.left + d * pxStep()) } }
        steppers += HzStepper.add(visible, R.string.hz_tune_cut_right, { px(ClusterMapTune.PICTURE_WIDTH - tune().right) }) { d ->
            edit { it.copy(right = it.right - d * pxStep()) }
        }

        steppers += HzStepper.add(view.findViewById(R.id.hz_tune_density_card), R.string.hz_tune_dpi, ::dpiText) { d ->
            settings.horizonMapDpi = (BydClusterMap.mapDpi(settings) + d * if (coarse) 20 else 5).coerceIn(MIN_DPI, MAX_DPI)
            apply()
        }

        view.findViewById<View>(R.id.hz_tune_reset).setOnClickListener {
            ClusterMapTune.save(settings, settings.horizonMapLayout, null)
            apply()
        }
        view.findViewById<View>(R.id.hz_tune_reset_all).setOnClickListener { confirmResetAll() }
        refresh()
    }

    private fun tune() = ClusterMapTune.of(settings, settings.horizonMapLayout)

    private fun edit(change: (ClusterMapTune) -> ClusterMapTune) {
        ClusterMapTune.save(settings, settings.horizonMapLayout, change(tune()))
        apply()
    }

    private fun apply() {
        BydClusterMap.applyTune(settings)
        refresh()
    }

    private fun refresh() {
        steppers.forEach { it.refresh() }
        val visible = tune().visible
        visibleSize.text = getString(R.string.hz_tune_visible_size, visible.width(), visible.height())
    }

    private fun confirmResetAll() {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(R.string.hz_tune_reset_all)
            .setMessage(R.string.hz_tune_reset_all_message)
            .setPositiveButton(R.string.byd_continue) { _, _ ->
                HorizonOptions.MAP_LAYOUTS.forEach { ClusterMapTune.save(settings, it.key, null) }
                settings.horizonMapDpi = 0
                apply()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pxStep() = if (coarse) 10 else 1

    private fun px(value: Int) = getString(R.string.hz_tune_px, value)

    private fun dpiText(): String {
        val dpi = BydClusterMap.mapDpi(settings)
        return if (dpi == BydClusterMap.HORIZON_MAP_DPI) getString(R.string.hz_tune_dpi_default, dpi) else dpi.toString()
    }

    private companion object {
        const val MIN_DPI = 100
        const val MAX_DPI = 320
    }
}
