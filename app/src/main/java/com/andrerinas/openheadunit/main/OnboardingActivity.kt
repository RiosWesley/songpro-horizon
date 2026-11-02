package com.andrerinas.openheadunit.main

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.app.BaseActivity
import com.andrerinas.openheadunit.main.onboarding.AccessStep
import com.andrerinas.openheadunit.main.onboarding.InstallStep
import com.andrerinas.openheadunit.main.onboarding.OnboardingStep
import com.andrerinas.openheadunit.main.onboarding.ReadyStep
import com.andrerinas.openheadunit.main.onboarding.RisksStep
import com.andrerinas.openheadunit.main.onboarding.WelcomeStep
import com.andrerinas.openheadunit.utils.SystemOptimizer
import com.google.android.material.button.MaterialButton

/**
 * First run: gets the driver ready to use Horizon, one step at a time. What Horizon is, its risks
 * (accepted with a tick, the app's safety disclaimer), local ADB, a check of this car and the
 * install, Android Auto and the wheel key (the app's own screens), then the panel restart.
 *
 * Shown when the stored onboardingVersion is older than [CURRENT_ONBOARDING_VERSION], and
 * re-launchable from Settings. Back from the first step leaves it for the next app start.
 */
class OnboardingActivity : BaseActivity() {

    private val settings by lazy { App.provide(this).settings }
    private lateinit var progress: TextView
    private lateinit var back: MaterialButton
    private lateinit var next: MaterialButton
    private var step = 0

    /** Horizon was installed during this guide: the last step offers the panel restart. */
    var installedNow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        progress = findViewById(R.id.onb_progress)
        back = findViewById(R.id.onb_back)
        next = findViewById(R.id.onb_next)
        back.setOnClickListener { go(step - 1) }
        next.setOnClickListener { if (step == STEPS.lastIndex) complete() else go(step + 1) }

        step = savedInstanceState?.getInt(KEY_STEP) ?: 0
        installedNow = savedInstanceState?.getBoolean(KEY_INSTALLED) ?: false
        // After a recreate the fragment manager brings the step back by itself.
        if (savedInstanceState == null) show() else refreshNav()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, step)
        outState.putBoolean(KEY_INSTALLED, installedNow)
    }

    @Deprecated("Back walks the steps")
    override fun onBackPressed() {
        go(step - 1)
    }

    /** Called by the steps when what they allow changes. */
    fun refreshNav() {
        progress.text = getString(R.string.hz_onb_progress, step + 1, STEPS.size)
        back.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        next.setText(if (step == STEPS.lastIndex) R.string.hz_onb_finish else R.string.hz_onb_continue)
        val current = supportFragmentManager.findFragmentById(R.id.onb_container) as? OnboardingStep
        next.isEnabled = current?.canContinue() ?: true
    }

    /** Marks the guide done; [finish] false when something else ends the activity (the panel restart). */
    fun complete(finish: Boolean = true) {
        applyRecommendedVideo()
        settings.hasAcceptedDisclaimer = true
        settings.hasCompletedSetupWizard = true
        settings.onboardingVersion = CURRENT_ONBOARDING_VERSION
        settings.commit()
        if (finish) finish()
    }

    private fun go(to: Int) {
        if (to < 0) {
            deferredThisSession = true
            finish()
            return
        }
        step = to
        show()
    }

    private fun show() {
        supportFragmentManager.beginTransaction().replace(R.id.onb_container, STEPS[step]()).commitNow()
        refreshNav()
    }

    /**
     * A fresh install starts with the video setup recommended for this screen, as the old wizard
     * did; picture size and density stay automatic (Android Auto section). Upgraders keep theirs.
     */
    private fun applyRecommendedVideo() {
        val upgrader = settings.hasCompletedSetupWizard || settings.onboardingVersion in 1 until CURRENT_ONBOARDING_VERSION
        if (upgrader) return
        val result = SystemOptimizer(this).calculateOptimalSettings(SystemOptimizer.DisplaySizePreset.LARGE_11_PLUS, false)
        val previousViewMode = settings.viewMode
        settings.videoCodec = result.recommendedVideoCodec
        settings.viewMode = result.recommendedViewMode
        settings.screenOrientation = result.suggestedOrientation
        // A new renderer is confirmed on the first projection.
        if (settings.viewMode != previousViewMode) settings.pendingRendererConfirm = true
    }

    companion object {
        const val CURRENT_ONBOARDING_VERSION = 3
        // Set when the driver backs out of the first step, so MainActivity does not relaunch the
        // guide this session. Resets on process restart (next app open).
        @Volatile
        var deferredThisSession = false
        private const val KEY_STEP = "onb_step"
        private const val KEY_INSTALLED = "onb_installed"
        private val STEPS: List<() -> Fragment> = listOf(
            ::WelcomeStep, ::RisksStep, ::AccessStep, ::InstallStep, ::AndroidAutoFragment, ::WheelFragment, ::ReadyStep)
    }
}
