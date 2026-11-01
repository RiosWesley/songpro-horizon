package com.andrerinas.openheadunit.aap.cluster

import android.content.Context
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.adb.AdbManager
import kotlinx.coroutines.delay

/**
 * Switches the BYD DiLink cluster between its native scene and the Android projection display,
 * and hides the native ADAS pane that would otherwise cover the map.
 *
 * Both need shell privileges (the AutoContainer binder and the clusterdebug CAN receiver accept
 * uid 2000), so they go through Self-ADB. The sequences are the ones DashCast and Electro use on
 * DiLink 3: `16 → 35` opens the projection without changing the panel geometry (no `30`);
 * DashCast's teardown is `18`, 1 s, `0`, then the ADAS restore.
 *
 * Everything runs as short synchronous Self-ADB calls from this process: a shell process left in
 * the background is killed when its adb session closes on this head unit, so the long ADAS passes
 * go out in batches of at most ten frames, like DashCast sends them.
 */
internal object ClusterPanelControl {

    private const val TAG = "ClusterPanelControl"

    private const val ADAS_HIDE = "23,50,00,08,01,00"
    private const val ADAS_RESTORE = "23,50,00,08,01,01"
    private const val BATCH = 10

    /** Electro's recovered masking pass; a single hide frame is not enough, the pane comes back. */
    private val MASKING_PHASES = listOf(
        ADAS_HIDE to 3,
        "26,F0,00,28,01,06" to 60,
        "43,20,00,00,01,01" to 50,
        "43,20,00,21,01,01" to 50,
        "26,F0,00,28,01,06" to 40,
        ADAS_HIDE to 60,
    )

    private const val INIT_CLUSTERDEBUG =
        "cmd deviceidle whitelist +com.byd.clusterdebug >/dev/null 2>&1; " +
            "am startservice -n com.byd.clusterdebug/.ClusterDebugService >/dev/null 2>&1"

    private fun container(code: Int) = "service call AutoContainer 2 i32 1000 i32 $code s16 '' >/dev/null"

    private fun frames(count: Int, frame: String) =
        "i=0; while [ \$i -lt $count ]; do am broadcast -a com.byd.cluster.spi -p com.byd.clusterdebug " +
            "-f 0x20 --es wholeFrame $frame >/dev/null 2>&1; i=\$((i+1)); done"

    /**
     * Opens the projection on the cluster, then hides the ADAS pane. The masking pass stops early
     * once [stillWanted] turns false, so a quick "off" is not held up for the whole pass.
     */
    suspend fun show(context: Context, stillWanted: () -> Boolean): Boolean {
        if (!exec(context, "open", "${container(16)}; sleep 3; ${container(35)}")) return false
        if (!exec(context, "adas-init", "${container(13)}; $INIT_CLUSTERDEBUG")) return false
        delay(1_000)
        for ((frame, count) in MASKING_PHASES) {
            // Cut short by an "off": the projection is open, so report on and let the caller close
            // it. A failed batch while still wanted reports failure, so the next request retries.
            if (!sendFrames(context, frame, count, stillWanted)) return !stillWanted()
        }
        return true
    }

    /** Returns the cluster to its native scene, then brings the ADAS pane back (DashCast's order). */
    suspend fun hide(context: Context): Boolean {
        val closed = exec(context, "close", "${container(18)}; sleep 1; ${container(0)}")
        exec(context, "adas-init", INIT_CLUSTERDEBUG)
        delay(1_000)
        sendFrames(context, ADAS_RESTORE, 40) { true }
        exec(context, "adas-show", container(12))
        delay(1_200)
        exec(context, "adas-53", container(53))
        // The panel is what the user sees; the ADAS restore is best effort on top of it.
        return closed
    }

    /** Sends [frame] [count] times in batches; false if a batch fails or [keepGoing] turns false. */
    private suspend fun sendFrames(context: Context, frame: String, count: Int, keepGoing: () -> Boolean): Boolean {
        var sent = 0
        while (sent < count) {
            if (!keepGoing()) return false
            val n = minOf(BATCH, count - sent)
            if (!exec(context, "frames", frames(n, frame), quiet = true)) return false
            sent += n
        }
        return true
    }

    private suspend fun exec(context: Context, name: String, script: String, quiet: Boolean = false): Boolean {
        val (code, out) = AdbManager.exec(context, script)
        if (code != 0) AppLog.w("$TAG: $name failed: $out") else if (!quiet) AppLog.i("$TAG: $name sent")
        return code == 0
    }
}
