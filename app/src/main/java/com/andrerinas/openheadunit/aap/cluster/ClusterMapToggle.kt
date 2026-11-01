package com.andrerinas.openheadunit.aap.cluster

import android.content.Context
import com.andrerinas.openheadunit.utils.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The user's "map on the cluster" switch for an Android Auto session. On, the cluster shows the
 * projection with the map and the ADAS pane hidden; off, the native cluster comes back. The choice
 * is remembered, so the next session starts the way the last one was left.
 */
internal object ClusterMapToggle {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Panel commands take seconds and taps can come faster, so taps only set [wanted] and one
     * reconciler at a time brings the panel to the latest wanted state. Launch order is not
     * guaranteed, so a command per tap could finish in the wrong order.
     */
    private val panelLock = Mutex()
    @Volatile private var wanted: Boolean? = null
    /** What the panel was last successfully set to; null when unknown. */
    private var applied: Boolean? = null

    @Volatile private var sessionActive = false

    fun isOn(context: Context) = Settings(context).clusterMapEnabled

    /** Whether a phone is projecting, so the map can be switched now. */
    fun inSession() = sessionActive

    fun onSessionStart(context: Context) {
        sessionActive = true
        apply(context.applicationContext, isOn(context))
        ClusterToggleButton.show(context)
    }

    fun onSessionEnd(context: Context) {
        if (!sessionActive) return
        sessionActive = false
        ClusterToggleButton.hide()
        // Leave the car's own cluster behind, whichever way the switch was.
        setPanel(context.applicationContext, false)
    }

    /** Flips the switch; returns the new state. */
    fun toggle(context: Context): Boolean {
        val on = !isOn(context)
        Settings(context).clusterMapEnabled = on
        apply(context.applicationContext, on)
        return on
    }

    private fun apply(context: Context, on: Boolean) {
        ClusterDisplayOutput.setVisible(on)
        setPanel(context, on)
    }

    private fun setPanel(context: Context, on: Boolean) {
        wanted = on
        scope.launch {
            panelLock.withLock {
                val target = wanted ?: return@withLock
                if (target == applied) return@withLock
                // An "off" arriving mid-masking cuts the pass short; the projection is open by then,
                // so `show` still reports on and the next reconcile closes it.
                val ok = if (target) ClusterPanelControl.show(context) { wanted == true }
                    else ClusterPanelControl.hide(context)
                // A failed command leaves the panel state unknown, so the next request retries.
                applied = if (ok) target else null
            }
        }
    }
}
