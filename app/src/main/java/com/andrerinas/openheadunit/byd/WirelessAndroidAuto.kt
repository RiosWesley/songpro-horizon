package com.andrerinas.openheadunit.byd

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.connection.wifi.WifiLauncherMode
import com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.NativeStrategy
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings
import com.andrerinas.openheadunit.utils.adb.AdbManager

/**
 * Wireless Android Auto the way a car with it built in does it: the head unit's Bluetooth advertises
 * Android Auto, so when the car connects to the phone the phone dials back, is handed the password of
 * a Wi-Fi Direct network this app hosts, joins it and projects. The protocol is Open Headunit's Native
 * AA mode; this switches it on and off and keeps the chosen phones.
 *
 * Wi-Fi Direct rather than the head unit's hotspot: the car's Wi-Fi stays free to join a network, and
 * the hotspot is the user's to switch on. Calls stay with BYD's stack, whose radio already advertises
 * Hands-Free, so the native mode registers no stand-in.
 *
 * The chosen phones are kept in the wake-poke list and copied to the Bluetooth auto-start list only
 * while this is on: that list also opens the app when a phone connects, which with this off would be
 * the app jumping up on every drive.
 */
object WirelessAndroidAuto {

    private const val TAG = "WirelessAndroidAuto"

    fun isOn(settings: Settings): Boolean = settings.wifiConnectionMode == WifiLauncherMode.NATIVE

    fun phones(settings: Settings): Set<String> = settings.nativePokeBtMacs

    /** Switches it on (true) or off; returns null on success, else why not. */
    suspend fun set(context: Context, on: Boolean): String? {
        val settings = Settings(context)
        if (on) {
            // Android 10 tells only an app with fine location about the Wi-Fi Direct group it hosts.
            grantLocation(context)?.let { return it }
            settings.nativeApStrategy = NativeStrategy.WIFI_DIRECT
            // An empty list already means every kind. With a list, Bluetooth auto-start reads a
            // missing Wi-Fi as "not wireless" and would try Self Mode instead.
            val modes = settings.connectionModes
            if (modes.isNotEmpty()) settings.connectionModes = modes + Settings.ConnectionMode.WIFI
        }
        settings.wifiConnectionMode = if (on) WifiLauncherMode.NATIVE else WifiLauncherMode.MANUAL
        startOnBluetooth(context, settings, if (on) phones(settings) else emptySet())
        settings.commit()

        val action = if (on) AapService.ACTION_START_WIRELESS else AapService.ACTION_STOP_WIRELESS
        context.startService(Intent(context, AapService::class.java).setAction(action))
        AppLog.i("$TAG: ${if (on) "on" else "off"}")
        return null
    }

    fun setPhones(context: Context, macs: Set<String>) {
        val settings = Settings(context)
        settings.nativePokeBtMacs = macs
        if (isOn(settings)) startOnBluetooth(context, settings, macs)
    }

    private fun startOnBluetooth(context: Context, settings: Settings, macs: Set<String>) {
        settings.autoStartBluetoothDeviceMacs = macs
        Settings.syncAutoStartBtMacsToDeviceStorage(context, macs)
    }

    private suspend fun grantLocation(context: Context): String? {
        if (hasLocation(context)) return null
        val (code, out) = AdbManager.exec(context, "pm grant ${context.packageName} ${Manifest.permission.ACCESS_FINE_LOCATION} 2>&1")
        if (hasLocation(context)) return null
        AppLog.w("$TAG: could not grant location ($code): $out")
        return out.ifBlank { "sem resposta do ADB local" }
    }

    private fun hasLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
