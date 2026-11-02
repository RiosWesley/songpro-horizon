package com.andrerinas.openheadunit.main.onboarding

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.main.OnboardingActivity

/** The risks, accepted with a tick before the guide goes on. The tick is the app's safety disclaimer. */
class RisksStep : Fragment(R.layout.onb_step_risks), OnboardingStep {

    private var accept: CompoundButton? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        showPoints(view.findViewById(R.id.onb_points), listOf(
            R.string.hz_onb_risk_parked to R.string.hz_onb_risk_parked_text,
            R.string.hz_onb_risk_undo to R.string.hz_onb_risk_undo_text,
            R.string.hz_onb_risk_firmware to R.string.hz_onb_risk_firmware_text,
            R.string.hz_onb_risk_updates to R.string.hz_onb_risk_updates_text,
            R.string.hz_onb_risk_adb to R.string.hz_onb_risk_adb_text,
            R.string.hz_onb_risk_own to R.string.hz_onb_risk_own_text))
        val settings = App.provide(requireContext()).settings
        accept = view.findViewById<CompoundButton>(R.id.onb_risks_accept).apply {
            isChecked = settings.hasAcceptedDisclaimer
            setOnCheckedChangeListener { _, checked ->
                settings.hasAcceptedDisclaimer = checked
                (activity as? OnboardingActivity)?.refreshNav()
            }
        }
    }

    override fun onDestroyView() {
        accept = null
        super.onDestroyView()
    }

    override fun canContinue(): Boolean = accept?.isChecked == true
}
