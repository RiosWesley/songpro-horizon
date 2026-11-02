package com.andrerinas.openheadunit.main.onboarding

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.theme.HorizonInstaller
import com.andrerinas.openheadunit.main.OnboardingActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Last step: where things are now, and the panel restart when Horizon was installed during the guide. */
class ReadyStep : Fragment(R.layout.onb_step_ready) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val installed = (activity as? OnboardingActivity)?.installedNow == true
        view.findViewById<TextView>(R.id.onb_lead).setText(
            if (installed) R.string.hz_onb_ready_lead_installed else R.string.hz_onb_ready_lead_later)
        showPoints(view.findViewById(R.id.onb_points), listOfNotNull(
            if (installed) R.string.hz_onb_tip_theme to R.string.hz_onb_tip_theme_text else null,
            R.string.hz_onb_tip_sections to R.string.hz_onb_tip_sections_text,
            R.string.hz_onb_tip_factory to R.string.hz_onb_tip_factory_text))
        view.findViewById<View>(R.id.onb_restart).apply {
            visibility = if (installed) View.VISIBLE else View.GONE
            setOnClickListener { confirmRestart() }
        }
    }

    private fun confirmRestart() {
        MaterialAlertDialogBuilder(requireContext(), R.style.DarkAlertDialog)
            .setTitle(R.string.byd_restart_title)
            .setMessage(R.string.byd_restart_message)
            .setPositiveButton(R.string.byd_continue) { _, _ -> restart() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** The guide is done before the head unit goes down with the panel. */
    private fun restart() {
        val activity = activity as? OnboardingActivity ?: return
        val log = requireView().findViewById<TextView>(R.id.onb_log)
        showLine(log, getString(R.string.byd_restarting))
        activity.complete(finish = false)
        val installer = HorizonInstaller(activity.applicationContext) {}
        viewLifecycleOwner.lifecycleScope.launch {
            if (installer.restartPanel()) activity.finish() else showLine(log, getString(R.string.byd_adb_unavailable))
        }
    }
}
