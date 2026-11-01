package com.andrerinas.openheadunit.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.andrerinas.openheadunit.R

/**
 * Sobre: what Song Pro Horizon is, the version, its credits (open-headunit, AGPL-3.0), and the way
 * into the full settings it inherited, kept whole behind one button.
 */
class HorizonAboutFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_hz_about, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        view.findViewById<TextView>(R.id.hz_about_version).text =
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
        view.findViewById<View>(R.id.hz_about_open_advanced).setOnClickListener {
            startActivity(Intent(context, SettingsActivity::class.java))
        }
    }
}
