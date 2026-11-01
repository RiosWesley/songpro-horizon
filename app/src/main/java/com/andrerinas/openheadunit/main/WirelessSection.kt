package com.andrerinas.openheadunit.main

import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.BydNativeAndroidAuto
import com.andrerinas.openheadunit.byd.WirelessAndroidAuto
import com.andrerinas.openheadunit.utils.BluetoothHelper
import com.andrerinas.openheadunit.utils.Settings
import kotlinx.coroutines.launch

/** Wireless Android Auto (Sem fio pelo Bluetooth): the switch and the phones it answers. */
internal class WirelessSection(
    private val root: View,
    private val scope: LifecycleCoroutineScope,
    private val log: (String) -> Unit,
) {
    private val context = root.context.applicationContext
    private val settings = Settings(context)
    private val switch: CompoundButton = root.findViewById(R.id.hz_switch_wireless)
    private val phoneRow: View = root.findViewById(R.id.hz_wireless_phone_row)
    private val phone: TextView = root.findViewById(R.id.hz_wireless_phone)
    private var showing = false

    init {
        show()
        switch.setOnCheckedChangeListener { _, on -> if (!showing) setOn(on) }
        phoneRow.setOnClickListener { pickPhones() }
    }

    /** Usable only while this app answers Android Auto; BYD's own would be a second one on the same radio. */
    fun show() {
        showing = true
        switch.isChecked = WirelessAndroidAuto.isOn(settings)
        showing = false
        switch.isEnabled = BydNativeAndroidAuto.state(context) != BydNativeAndroidAuto.State.ENABLED
        phoneRow.visibility = if (switch.isChecked) View.VISIBLE else View.GONE
        phone.text = BluetoothDevicePicker.summaryFor(context, WirelessAndroidAuto.phones(settings))
            ?: root.context.getString(R.string.hz_aa_wireless_any)
    }

    /** For when BYD's Android Auto takes over. */
    suspend fun turnOff() {
        if (WirelessAndroidAuto.isOn(settings)) WirelessAndroidAuto.set(context, false)
        show()
    }

    private fun setOn(on: Boolean) {
        switch.isEnabled = false
        scope.launch {
            WirelessAndroidAuto.set(context, on)?.let { log(root.context.getString(R.string.hz_aa_wireless_failed, it)) }
            show()
            // The first time, straight on to choosing the phone.
            if (on && switch.isChecked && WirelessAndroidAuto.phones(settings).isEmpty()) pickPhones()
        }
    }

    private fun pickPhones() {
        if (BluetoothHelper.getBluetoothAdapter(context)?.isEnabled != true) {
            log(root.context.getString(R.string.hz_aa_wireless_bt_off))
            return
        }
        BluetoothDevicePicker.show(root.context, R.string.hz_aa_wireless_pick, WirelessAndroidAuto.phones(settings)) { macs ->
            WirelessAndroidAuto.setPhones(context, macs)
            show()
        }
    }
}
