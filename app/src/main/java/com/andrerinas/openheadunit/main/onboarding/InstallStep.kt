package com.andrerinas.openheadunit.main.onboarding

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.CarCheck
import com.andrerinas.openheadunit.byd.theme.HorizonInstaller
import com.andrerinas.openheadunit.main.OnboardingActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * The car check, then the install when it passes. The check only reads ([CarCheck]); the install
 * backs the original panel up and selects Horizon, which shows after the restart the last step offers.
 */
class InstallStep : Fragment(R.layout.onb_step_install), OnboardingStep {

    private lateinit var install: MaterialButton
    private lateinit var recheck: MaterialButton
    private var busy = false
    private var canInstall = false
    private var installed = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        install = view.findViewById(R.id.onb_install)
        recheck = view.findViewById(R.id.onb_recheck)
        install.setOnClickListener { confirmInstall() }
        recheck.setOnClickListener { runCheck() }
        runCheck()
    }

    override fun canContinue(): Boolean = !busy

    private fun runCheck() {
        val view = requireView()
        val list = view.findViewById<LinearLayout>(R.id.onb_checks)
        val verdict = view.findViewById<TextView>(R.id.onb_verdict)
        list.removeAllViews()
        verdict.setText(R.string.hz_onb_checking)
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            var warned = false
            canInstall = CarCheck(requireContext().applicationContext).run { item ->
                if (item.status == CarCheck.Status.WARNING) warned = true
                addCheck(list, item)
            }
            verdict.setText(when {
                !canInstall -> R.string.hz_onb_verdict_no
                warned -> R.string.hz_onb_verdict_warning
                else -> R.string.hz_onb_verdict_ok
            })
            setBusy(false)
        }
    }

    private fun addCheck(list: LinearLayout, item: CarCheck.Item) {
        val row = layoutInflater.inflate(R.layout.item_onb_check, list, false)
        row.findViewById<TextView>(R.id.onb_check_title).text = item.title
        row.findViewById<TextView>(R.id.onb_check_detail).text = item.detail
        tintDot(row.findViewById<ImageView>(R.id.onb_check_dot), when (item.status) {
            CarCheck.Status.OK -> R.color.hz_positive
            CarCheck.Status.WARNING -> R.color.hz_warning
            CarCheck.Status.FAILED -> R.color.hz_negative
        })
        list.addView(row)
    }

    private fun confirmInstall() {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(R.string.hz_onb_install_button)
            .setMessage(R.string.byd_theme_install_confirm)
            .setPositiveButton(R.string.byd_continue) { _, _ -> runInstall() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runInstall() {
        val log = requireView().findViewById<TextView>(R.id.onb_log)
        // The installer reports from a background thread.
        val installer = HorizonInstaller(requireContext().applicationContext) { line -> log.post { showLine(log, line) } }
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = installer.install()
            showLine(log, outcome.message)
            if (outcome !is HorizonInstaller.Outcome.Failed) {
                installed = true
                install.setText(R.string.hz_onb_installed)
                (activity as? OnboardingActivity)?.installedNow = true
            }
            setBusy(false)
        }
    }

    private fun setBusy(busy: Boolean) {
        this.busy = busy
        install.isEnabled = !busy && canInstall && !installed
        recheck.isEnabled = !busy
        (activity as? OnboardingActivity)?.refreshNav()
    }
}
