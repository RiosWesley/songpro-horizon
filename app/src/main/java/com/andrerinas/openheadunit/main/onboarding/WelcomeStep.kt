package com.andrerinas.openheadunit.main.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.andrerinas.openheadunit.R

/** First step: what Song Pro Horizon does. */
class WelcomeStep : Fragment(R.layout.onb_step_welcome) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        showPoints(view.findViewById(R.id.onb_points), listOf(
            R.string.hz_onb_feature_theme to R.string.hz_onb_feature_theme_text,
            R.string.hz_onb_feature_map to R.string.hz_onb_feature_map_text,
            R.string.hz_onb_feature_wheel to R.string.hz_onb_feature_wheel_text))
    }
}
