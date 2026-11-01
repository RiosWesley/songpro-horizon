package com.andrerinas.openheadunit.byd.wheel

import android.content.ComponentName
import android.content.Context
import android.provider.Settings.Secure
import com.andrerinas.openheadunit.utils.adb.AdbManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Switches the steering wheel menu on and off: adds or removes [WheelMenuService] in the system's
 * enabled accessibility services through Self-ADB (the shell may write secure settings), so the
 * driver does not have to find it in Android's accessibility screen. Other services (Electro's,
 * for one) are left as they are.
 */
object WheelMenuSwitch {

    fun isOn(context: Context): Boolean = enabledServices(context).any { it == component(context) }

    /** @return null when switched, else why not. */
    suspend fun set(context: Context, on: Boolean): String? = withContext(NonCancellable) {
        val others = enabledServices(context).filter { it != component(context) }
        val list = if (on) others + component(context) else others
        val command = if (list.isEmpty()) "settings delete secure enabled_accessibility_services"
            else "settings put secure enabled_accessibility_services '${list.joinToString(":") { it.flattenToString() }}'" +
                " && settings put secure accessibility_enabled 1"
        val (code, out) = AdbManager.exec(context, command)
        if (code == 0 && isOn(context) == on) null else out.ifBlank { "sem resposta do ADB local" }
    }

    private fun component(context: Context) = ComponentName(context, WheelMenuService::class.java)

    private fun enabledServices(context: Context): List<ComponentName> =
        Secure.getString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').mapNotNull { ComponentName.unflattenFromString(it.trim()) }
}
