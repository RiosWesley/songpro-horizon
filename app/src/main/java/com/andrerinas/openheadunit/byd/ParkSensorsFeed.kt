package com.andrerinas.openheadunit.byd

import android.content.Context
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings
import com.andrerinas.openheadunit.utils.adb.AdbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * The parking sensors for Horizon (horizon/tools/park-feed): a small reader that runs as the shell
 * user, since the radar permission is signature-only, and writes park.json next to the now-playing
 * feed whenever a zone changes. Started through Self-ADB each time the app comes up with Horizon on
 * the car (the boot receiver wakes the app), when Horizon is installed and when a phone connects;
 * the reader holds a lock, so starting it again is harmless. [STOP] takes it off the car.
 */
object ParkSensorsFeed {

    private const val TAG = "ParkSensorsFeed"
    private const val NAME = "horizon-park-feed.dex"
    private const val DEX = "/data/local/tmp/$NAME"
    private const val FEED_DIR = "/sdcard/.cluster/horizon"
    // Right after boot the local adbd may not take connections yet.
    private const val ATTEMPTS = 3
    private const val RETRY_MS = 20_000L

    /** Stops the reader and removes it (the bracket keeps pkill off the shell running it). */
    const val STOP = "pkill -f '[H]orizonParkFeed'; rm -f $DEX"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureRunning(context: Context) {
        val app = context.applicationContext
        if (!Settings(app).horizonInstalled) return
        scope.launch {
            repeat(ATTEMPTS) { attempt ->
                if (start(app)) return@launch
                if (attempt < ATTEMPTS - 1) delay(RETRY_MS)
            }
        }
    }

    private suspend fun start(context: Context): Boolean {
        val staging = context.getExternalFilesDir("horizon") ?: return false
        val staged = File(staging, NAME)
        try {
            context.assets.open("horizon/$NAME").use { input -> staged.outputStream().use { input.copyTo(it) } }
        } catch (e: Exception) {
            AppLog.w("$TAG: reader missing from the app (${e.message})")
            return false
        }
        val (code, out) = AdbManager.exec(context,
            "cp '${staged.path}' $DEX && chmod 644 $DEX && mkdir -p $FEED_DIR && " +
                "(setsid nohup sh -c 'CLASSPATH=$DEX app_process / HorizonParkFeed $FEED_DIR >/dev/null 2>&1' " +
                ">/dev/null 2>&1 &) && echo STARTED")
        val started = code == 0 && out.contains("STARTED")
        if (started) AppLog.i("$TAG: reader running") else AppLog.w("$TAG: could not start the reader ($code): $out")
        return started
    }
}
