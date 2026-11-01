package com.andrerinas.openheadunit.byd

import android.content.Context
import android.content.pm.PackageManager
import com.andrerinas.openheadunit.utils.Settings
import com.andrerinas.openheadunit.utils.adb.AdbManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Who answers the phone on the USB cable: BYD's own Android Auto (com.byd.androidauto) or this app.
 *
 * Both want the phone, so only one may run: choosing this app disables BYD's (as the shell user,
 * "pm disable-user", reversible with "pm enable") and turns on this app's USB auto-start and the map
 * on the cluster; choosing BYD's turns BYD's back on and this app's USB auto-start off. Nothing is
 * uninstalled. The phone has to be plugged in again after a switch.
 */
object BydNativeAndroidAuto {

    const val PACKAGE = "com.byd.androidauto"

    enum class State { ENABLED, DISABLED, ABSENT }

    fun state(context: Context): State = try {
        when (context.packageManager.getApplicationEnabledSetting(PACKAGE)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> State.DISABLED
            else -> State.ENABLED
        }
    } catch (e: IllegalArgumentException) {
        State.ABSENT
    }

    /** Makes this app (true) or BYD's (false) the USB Android Auto; returns null on success, else why not. */
    suspend fun useThisApp(context: Context, thisApp: Boolean): String? = withContext(NonCancellable) {
        val command = if (thisApp) "pm disable-user --user 0 $PACKAGE" else "pm enable $PACKAGE"
        val (code, out) = AdbManager.exec(context, "$command 2>&1")
        val wanted = if (thisApp) State.DISABLED else State.ENABLED
        if (code != 0 || state(context) != wanted) return@withContext out.ifBlank { "sem resposta do ADB local" }

        val settings = Settings(context)
        settings.autoStartOnUsb = thisApp
        Settings.syncAutoStartOnUsbToDeviceStorage(context, thisApp)
        settings.autoConnectSingleUsbDevice = thisApp
        if (thisApp) BydClusterMap.enable(settings)
        null
    }
}
