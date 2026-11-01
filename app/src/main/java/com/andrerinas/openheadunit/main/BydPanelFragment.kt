package com.andrerinas.openheadunit.main

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.BydNativeAndroidAuto
import com.andrerinas.openheadunit.byd.theme.HorizonCarrier
import com.andrerinas.openheadunit.byd.theme.HorizonInstaller
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * The BYD screen of the shared build: install or remove the Horizon cluster theme, the steering
 * wheel menu ([BydWheelSection]), and who answers the phone on the USB cable (this app or BYD's own
 * Android Auto).
 */
class BydPanelFragment : Fragment() {

    private lateinit var installer: HorizonInstaller
    private lateinit var themeStatus: TextView
    private lateinit var install: MaterialButton
    private lateinit var remove: MaterialButton
    private lateinit var aaMode: MaterialButtonToggleGroup
    private lateinit var logView: TextView
    private var wheel: BydWheelSection? = null
    private var showingAaState = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_byd_panel, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { findNavController().popBackStack() }
        themeStatus = view.findViewById(R.id.byd_theme_status)
        install = view.findViewById(R.id.byd_theme_install)
        remove = view.findViewById(R.id.byd_theme_remove)
        aaMode = view.findViewById(R.id.byd_aa_mode)
        logView = view.findViewById(R.id.byd_log)
        // The installer reports from a background thread.
        installer = HorizonInstaller(requireContext().applicationContext) { line -> logView.post { log(line) } }

        install.setOnClickListener {
            confirm(R.string.byd_theme_install, R.string.byd_theme_install_confirm) { runTheme { installer.install() } }
        }
        remove.setOnClickListener { runTheme { installer.remove() } }
        aaMode.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !showingAaState) switchAndroidAuto(id == R.id.byd_aa_this_app)
        }

        wheel = BydWheelSection(view, viewLifecycleOwner.lifecycleScope, ::log)
        showAaState()
        refreshTheme()
    }

    override fun onDestroyView() {
        wheel?.release()
        wheel = null
        super.onDestroyView()
    }

    private fun refreshTheme() {
        if (Build.FINGERPRINT != HorizonCarrier.SUPPORTED_FINGERPRINT) {
            themeStatus.setText(R.string.byd_theme_unsupported)
            install.isEnabled = false
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val installed = runCatching { installer.installed() }
            themeStatus.text = installed.fold(
                onSuccess = { name ->
                    if (name == null) getString(R.string.byd_theme_factory)
                    else getString(R.string.byd_theme_installed, name.removePrefix(HorizonCarrier.NAME_PREFIX).take(8))
                },
                onFailure = { getString(R.string.byd_adb_unavailable) })
        }
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

    private fun showAaState() {
        val state = BydNativeAndroidAuto.state(requireContext())
        requireView().findViewById<View>(R.id.byd_aa_section).visibility =
            if (state == BydNativeAndroidAuto.State.ABSENT) View.GONE else View.VISIBLE
        showingAaState = true
        aaMode.check(if (state == BydNativeAndroidAuto.State.DISABLED) R.id.byd_aa_this_app else R.id.byd_aa_native)
        showingAaState = false
    }

    private fun switchAndroidAuto(thisApp: Boolean) {
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val error = BydNativeAndroidAuto.useThisApp(requireContext().applicationContext, thisApp)
            if (view == null) return@launch
            log(error ?: getString(if (thisApp) R.string.byd_aa_now_this_app else R.string.byd_aa_now_native))
            setBusy(false)
            showAaState()
        }
    }

    private fun setBusy(busy: Boolean) {
        install.isEnabled = !busy && Build.FINGERPRINT == HorizonCarrier.SUPPORTED_FINGERPRINT
        remove.isEnabled = !busy
        for (i in 0 until aaMode.childCount) aaMode.getChildAt(i).isEnabled = !busy
    }

    private fun confirm(title: Int, message: Int, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.byd_continue) { _, _ -> onYes() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun log(line: String) {
        logView.append(line + "\n")
    }
}
