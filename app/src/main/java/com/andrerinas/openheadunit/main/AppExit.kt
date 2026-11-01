package com.andrerinas.openheadunit.main

import android.app.Activity
import android.content.Intent
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.AapService

/**
 * Leaving the app: the connection ends, and the service stops unless something is set to start it
 * again (boot, screen on, USB with reopen).
 */
internal object AppExit {

    fun exit(activity: Activity) {
        val settings = App.provide(activity).settings
        val keepServiceAlive = settings.autoStartOnBoot ||
            settings.autoStartOnScreenOn ||
            (settings.autoStartOnUsb && settings.reopenOnReconnection)
        val action = if (keepServiceAlive) AapService.ACTION_DISCONNECT else AapService.ACTION_STOP_SERVICE
        ContextCompat.startForegroundService(activity, Intent(activity, AapService::class.java).setAction(action))
        activity.finishAffinity()
    }
}
