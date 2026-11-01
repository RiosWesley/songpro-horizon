package com.andrerinas.openheadunit.aap.cluster

import android.content.Context
import android.provider.Settings
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.adb.AdbManager

/**
 * The overlay grant (SYSTEM_ALERT_WINDOW) the cluster map and its floating button are drawn with.
 * Without it the projection still opens on the cluster, but nothing is drawn there: a black frame.
 * A fresh install starts without it, so it is given as the shell user through Self-ADB, like the
 * panel commands.
 */
internal object OverlayGrant {

    private const val TAG = "OverlayGrant"

    /** True once this app may draw overlays, granting it first if needed. */
    suspend fun ensure(context: Context): Boolean {
        if (Settings.canDrawOverlays(context)) return true
        val (code, out) = AdbManager.exec(context, "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow 2>&1")
        val granted = Settings.canDrawOverlays(context)
        if (granted) AppLog.i("$TAG: overlay granted through Self-ADB")
        else AppLog.w("$TAG: could not grant the overlay ($code): $out")
        return granted
    }
}
