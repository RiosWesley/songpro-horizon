package com.andrerinas.openheadunit.aap.cluster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.BuildConfig
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings

/**
 * Debug builds only: the content insets and the picture's placement, set live from the PC, so where
 * the phone puts its cards can be measured on the car without a build per try:
 *
 *   adb shell am broadcast -a com.rioswesley.songprohorizon.CLUSTER_PROBE --es insets 1,1,1,1 --es placement 0.8,128,-42
 *
 * (`am` refuses an empty value, so full size is `--es placement 1,0,0`. The part of the picture drawn
 * is tuned in the app: Painel → Ajuste fino do mapa.)
 * Each change is applied like a layout switch ([ClusterDisplayOutput.relayout]). The values hold
 * until the next layout switch or app start, which fit the map again. `--es cycle after` runs the
 * cluster focus cycle a new picture gets (insets sent after it); `--es cycle first` sends them before too.
 */
internal object ClusterLayoutProbe {

    private const val ACTION = "com.rioswesley.songprohorizon.CLUSTER_PROBE"

    fun register(context: Context) {
        if (!BuildConfig.DEBUG) return
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val settings = Settings(context)
            val insets = intent.getStringExtra("insets")
            val placement = intent.getStringExtra("placement")
            insets?.let { settings.clusterContentInsets = it }
            placement?.let { settings.clusterMapPlacement = it }
            AppLog.i("ClusterLayoutProbe: insets ${settings.clusterContentInsets}, placement ${settings.clusterMapPlacement}")
            if (insets != null || placement != null) ClusterDisplayOutput.relayout()
            intent.getStringExtra("cycle")?.let {
                App.provide(context).commManager.probeClusterFocusCycle(insetsFirst = it == "first")
            }
        }
    }
}
