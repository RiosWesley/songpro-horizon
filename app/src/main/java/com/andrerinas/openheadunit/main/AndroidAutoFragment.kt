package com.andrerinas.openheadunit.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.BydNativeAndroidAuto
import com.andrerinas.openheadunit.utils.Settings
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch

/**
 * Android Auto: who answers the phone on the USB cable (this app or BYD's own), and how a connection
 * starts: on its own, with the map on the cluster, with lyrics, at which picture size and density.
 * The last two are what the phone is told when it connects, so they take effect from the next one.
 */
class AndroidAutoFragment : Fragment() {

    private lateinit var settings: Settings
    private lateinit var who: MaterialButtonToggleGroup
    private lateinit var lastMessage: TextView
    private lateinit var wireless: WirelessSection

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_hz_android_auto, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = Settings(requireContext().applicationContext)
        who = view.findViewById(R.id.byd_aa_mode)
        lastMessage = view.findViewById(R.id.hz_aa_log)
        showWho()
        wireless = WirelessSection(view, viewLifecycleOwner.lifecycleScope, ::showMessage)

        toggle(view, R.id.hz_switch_autostart, settings.autoStartOnUsb) {
            settings.autoStartOnUsb = it
            Settings.syncAutoStartOnUsbToDeviceStorage(requireContext().applicationContext, it)
        }
        toggle(view, R.id.hz_switch_map, settings.clusterMapEnabled) { settings.clusterMapEnabled = it }
        toggle(view, R.id.hz_switch_lyrics, settings.clusterLyrics) { settings.clusterLyrics = it }

        HzChoices.bind(view.findViewById(R.id.hz_quality_group),
            listOf(Settings.Resolution.AUTO.id to getString(R.string.hz_auto),
                Settings.Resolution._1280x720.id to Settings.Resolution._1280x720.resName,
                Settings.Resolution._1920x1080.id to Settings.Resolution._1920x1080.resName),
            settings.resolutionId) { settings.resolutionId = it }
        HzChoices.bind(view.findViewById(R.id.hz_density_group),
            listOf(SMALLER_DPI to getString(R.string.hz_density_smaller), 0 to getString(R.string.hz_density_default),
                LARGER_DPI to getString(R.string.hz_density_larger)),
            settings.dpiPixelDensity) { settings.dpiPixelDensity = it }
    }

    private fun toggle(root: View, id: Int, on: Boolean, change: (Boolean) -> Unit) {
        val switch = root.findViewById<CompoundButton>(id)
        switch.isChecked = on
        switch.setOnCheckedChangeListener { _, checked -> change(checked) }
    }

    private fun showWho() {
        val state = BydNativeAndroidAuto.state(requireContext())
        requireView().findViewById<View>(R.id.byd_aa_section).visibility =
            if (state == BydNativeAndroidAuto.State.ABSENT) View.GONE else View.VISIBLE
        HzChoices.bind(who, listOf(true to getString(R.string.hz_this_app), false to getString(R.string.hz_byd_aa)),
            state == BydNativeAndroidAuto.State.DISABLED) { thisApp -> switchAndroidAuto(thisApp) }
    }

    private fun switchAndroidAuto(thisApp: Boolean) {
        for (i in 0 until who.childCount) who.getChildAt(i).isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val error = BydNativeAndroidAuto.useThisApp(requireContext().applicationContext, thisApp)
            if (view == null) return@launch
            showMessage(error ?: getString(if (thisApp) R.string.byd_aa_now_this_app else R.string.byd_aa_now_native))
            showWho()
            if (error == null && !thisApp) wireless.turnOff() else wireless.show()
            // Choosing this app also turns its USB auto-start on.
            requireView().findViewById<CompoundButton>(R.id.hz_switch_autostart).isChecked = settings.autoStartOnUsb
        }
    }

    private fun showMessage(text: String) {
        lastMessage.text = text
        lastMessage.visibility = View.VISIBLE
    }

    private companion object {
        // Android Auto's own layout density: below the cluster screen's 240 draws more, smaller;
        // above it, fewer and larger. 0 lets the app pick from the screen.
        const val SMALLER_DPI = 200
        const val LARGER_DPI = 280
    }
}
