package com.andrerinas.openheadunit.main.onboarding

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.CarCheck
import kotlinx.coroutines.launch

/** Local ADB: how to turn it on, and a check that also brings up the head unit's first prompt. */
class AccessStep : Fragment(R.layout.onb_step_access) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        showPoints(view.findViewById(R.id.onb_points), listOf(
            R.string.hz_onb_access_on to R.string.hz_onb_access_on_text,
            R.string.hz_onb_access_allow to R.string.hz_onb_access_allow_text))
        view.findViewById<View>(R.id.onb_access_check).setOnClickListener { check(view) }
    }

    private fun check(view: View) {
        val button = view.findViewById<View>(R.id.onb_access_check)
        val dot = view.findViewById<ImageView>(R.id.onb_access_dot)
        val status = view.findViewById<TextView>(R.id.onb_access_status)
        button.isEnabled = false
        dot.visibility = View.GONE
        status.setText(R.string.hz_onb_access_waiting)
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = CarCheck.adbReachable(requireContext())
            button.isEnabled = true
            dot.visibility = View.VISIBLE
            tintDot(dot, if (ok) R.color.hz_positive else R.color.hz_negative)
            status.setText(if (ok) R.string.hz_onb_access_ok else R.string.hz_onb_access_failed)
        }
    }
}
