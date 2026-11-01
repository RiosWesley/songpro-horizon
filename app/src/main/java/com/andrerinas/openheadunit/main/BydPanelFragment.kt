package com.andrerinas.openheadunit.main

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.BydClusterMap
import com.andrerinas.openheadunit.byd.BydNativeAndroidAuto
import com.andrerinas.openheadunit.byd.theme.FactoryRestore
import com.andrerinas.openheadunit.byd.theme.HorizonCarrier
import com.andrerinas.openheadunit.byd.theme.HorizonInstaller
import com.andrerinas.openheadunit.byd.theme.HorizonOptions
import com.andrerinas.openheadunit.byd.wheel.WheelMenuSwitch
import com.andrerinas.openheadunit.utils.Settings
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Painel: the Horizon cluster theme (install or update, restart the cluster, back to factory) and
 * what Horizon shows: the Simples slot, the power gauge, the left card and the map layout. Every
 * choice reaches the cluster at once through menu.json. The first time it finds the car without
 * Horizon, it offers to install it; "back to factory" undoes everything the app changed
 * ([FactoryRestore]). Only here: the app's own screen, not the cluster's menu.
 */
class BydPanelFragment : Fragment() {

    private lateinit var installer: HorizonInstaller
    private lateinit var settings: Settings
    private lateinit var themeStatus: TextView
    private lateinit var themeHint: TextView
    private lateinit var install: MaterialButton
    private lateinit var restart: MaterialButton
    private lateinit var remove: MaterialButton
    private lateinit var lastMessage: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_byd_panel, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = Settings(requireContext().applicationContext)
        themeStatus = view.findViewById(R.id.byd_theme_status)
        themeHint = view.findViewById(R.id.hz_theme_hint)
        install = view.findViewById(R.id.byd_theme_install)
        restart = view.findViewById(R.id.hz_theme_restart)
        remove = view.findViewById(R.id.byd_theme_remove)
        lastMessage = view.findViewById(R.id.byd_log)
        // The installer reports from a background thread.
        installer = HorizonInstaller(requireContext().applicationContext) { line -> lastMessage.post { log(line) } }

        install.setOnClickListener {
            confirm(R.string.byd_theme_install, R.string.byd_theme_install_confirm) { runTheme { installer.install() } }
        }
        remove.setOnClickListener { confirmFactoryRestore() }
        restart.setOnClickListener { offerRestart() }

        val publish = { HorizonOptions.publish(requireContext().applicationContext) }
        HzChoices.bind(view.findViewById(R.id.hz_skin_group), HorizonOptions.SKINS, settings.horizonSkin) {
            settings.horizonSkin = it
            publish()
        }
        HzChoices.bind(view.findViewById(R.id.hz_card_group), HorizonOptions.LEFT_CARDS, settings.horizonLeftCard) {
            settings.horizonLeftCard = it
            publish()
        }
        HzChoices.bind(view.findViewById(R.id.hz_map_group), HorizonOptions.MAP_LAYOUTS, settings.horizonMapLayout) {
            HorizonOptions.setMapLayout(requireContext().applicationContext, it)
            publish()
        }
        showPowerTiles(view.findViewById(R.id.hz_power_tiles))
        refreshTheme()
    }

    private fun confirmFactoryRestore() {
        val context = requireContext().applicationContext
        confirm(R.string.hz_factory_title, R.string.hz_factory_message) {
            runTheme { FactoryRestore(context) { line -> lastMessage.post { log(line) } }.run() }
        }
    }

    /** The five power gauges as Horizon draws them; the chosen one is raised. */
    private fun showPowerTiles(row: LinearLayout) {
        val pictures = mapOf("ring" to R.drawable.hz_gauge_ring, "segments" to R.drawable.hz_gauge_segments,
            "arc" to R.drawable.hz_gauge_arc, "column" to R.drawable.hz_gauge_column, "wave" to R.drawable.hz_gauge_wave)
        val inflater = LayoutInflater.from(row.context)
        val tiles = HorizonOptions.POWER_STYLES.map { option ->
            val tile = inflater.inflate(R.layout.item_hz_gauge_tile, row, false)
            tile.findViewById<ImageView>(R.id.hz_tile_picture).setImageResource(pictures.getValue(option.key))
            tile.findViewById<TextView>(R.id.hz_tile_label).setText(option.label)
            tile.contentDescription = getString(option.label)
            row.addView(tile)
            option.key to tile
        }
        fun mark() = tiles.forEach { (key, tile) -> tile.isActivated = key == settings.horizonPowerStyle }
        tiles.forEach { (key, tile) ->
            tile.setOnClickListener {
                settings.horizonPowerStyle = key
                HorizonOptions.publish(requireContext().applicationContext)
                mark()
            }
        }
        mark()
    }

    private fun refreshTheme() {
        if (Build.FINGERPRINT != HorizonCarrier.SUPPORTED_FINGERPRINT) {
            showTheme(R.string.hz_theme_unsupported, getString(R.string.byd_theme_unsupported), installed = false)
            install.isEnabled = false
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val found = runCatching { installer.installed() }
            if (view == null) return@launch
            found.fold(
                onSuccess = { name ->
                    // Keeps the map fitted to what is really installed (Horizon may have come from
                    // elsewhere, or have been removed by hand).
                    BydClusterMap.fitTo(settings, horizon = name != null)
                    if (name == null) showTheme(R.string.hz_theme_factory, getString(R.string.hz_theme_factory_hint), installed = false)
                    else showTheme(R.string.hz_theme_installed, getString(R.string.hz_theme_installed_hint,
                        name.removePrefix(HorizonCarrier.NAME_PREFIX).take(8)), installed = true)
                    if (name == null && !settings.horizonSetupOffered) offerFirstInstall()
                },
                onFailure = { showTheme(R.string.hz_theme_no_adb, getString(R.string.byd_adb_unavailable), installed = false) })
        }
    }

    /** The app's first time on a car without Horizon: the install, asked once. */
    private fun offerFirstInstall() {
        settings.horizonSetupOffered = true
        install.performClick()
    }

    private fun showTheme(status: Int, hint: String, installed: Boolean) {
        themeStatus.setText(status)
        themeHint.text = hint
        install.setText(if (installed) R.string.hz_theme_update else R.string.hz_theme_install)
        restart.visibility = if (installed) View.VISIBLE else View.GONE
        // Also without Horizon, while the app still has BYD's Android Auto off or the wheel menu on.
        val context = requireContext()
        val undo = installed || WheelMenuSwitch.isOn(context) ||
            BydNativeAndroidAuto.state(context) == BydNativeAndroidAuto.State.DISABLED
        remove.visibility = if (undo) View.VISIBLE else View.GONE
        requireView().findViewById<View>(R.id.hz_theme_picture).alpha = if (installed) 1f else 0.4f
    }

    private fun runTheme(action: suspend () -> HorizonInstaller.Outcome) {
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = action()
            if (view == null) return@launch
            log(outcome.message)
            setBusy(false)
            refreshTheme()
            if (outcome is HorizonInstaller.Outcome.RestartNeeded) offerRestart()
        }
    }

    private fun offerRestart() {
        confirm(R.string.byd_restart_title, R.string.byd_restart_message) {
            viewLifecycleOwner.lifecycleScope.launch {
                log(getString(R.string.byd_restarting))
                if (!installer.restartPanel()) log(getString(R.string.byd_adb_unavailable))
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        install.isEnabled = !busy && Build.FINGERPRINT == HorizonCarrier.SUPPORTED_FINGERPRINT
        restart.isEnabled = !busy
        remove.isEnabled = !busy
    }

    private fun confirm(title: Int, message: Int, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.byd_continue) { _, _ -> onYes() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** The installer's latest step, in one quiet line under the actions. */
    private fun log(line: String) {
        lastMessage.text = line
        lastMessage.visibility = View.VISIBLE
    }
}
