package com.andrerinas.openheadunit.aap.cluster

import android.content.Context
import com.andrerinas.openheadunit.BuildConfig
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings
import com.andrerinas.openheadunit.utils.adb.AdbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debug builds only: a capture of what the phone's picture really looks like on the cluster
 * display, a few seconds after the map shows or changes layout, in
 * /data/local/tmp/horizon-map-<layout>.png (shell's screencap through Self-ADB; the head unit's
 * own screen is FLAG_SECURE, the cluster display is not). It is how the black strip at the bottom
 * of the picture gets measured without someone at the car.
 */
internal object ClusterMapSnapshot {

    private const val DELAY_MS = 8_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    fun schedule(context: Context, displayId: Int) {
        if (!BuildConfig.DEBUG) return
        pending?.cancel()
        pending = scope.launch {
            delay(DELAY_MS)
            val layout = Settings(context).horizonMapLayout.ifBlank { "factory" }
            val out = "/data/local/tmp/horizon-map-$layout.png"
            val (code, text) = AdbManager.exec(context, "screencap -d $displayId -p $out 2>&1")
            AppLog.i("ClusterMapSnapshot: $out ($code) $text")
        }
    }
}
