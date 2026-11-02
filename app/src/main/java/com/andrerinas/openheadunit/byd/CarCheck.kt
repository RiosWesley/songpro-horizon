package com.andrerinas.openheadunit.byd

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.byd.theme.HorizonCarrier
import com.andrerinas.openheadunit.byd.theme.HorizonInstaller
import com.andrerinas.openheadunit.byd.theme.ThemeToolOutput
import com.andrerinas.openheadunit.utils.adb.AdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whether this car can take Horizon, checked on the car rather than assumed from its firmware
 * version (the app is shared, and Song Pro firmwares differ). It only reads:
 * - local ADB, which every change to the panel goes through;
 * - the cluster's theme service, through the theme tool's read-only query (the tool refuses unless
 *   the service's interface is the one it knows), and what the panel has selected;
 * - the factory panel files: the Horizon carrier is built in memory, so every patch must fit;
 * - the firmware: one other than the tested build is a warning, not a refusal;
 * - free space for the backup of the original panel.
 */
class CarCheck(private val context: Context) {

    enum class Status { OK, WARNING, FAILED }

    class Item(val title: String, val status: Status, val detail: String)

    /** Runs the checks in order, reporting each on the main thread as it ends; true when Horizon can be installed. */
    suspend fun run(report: (Item) -> Unit): Boolean = withContext(Dispatchers.IO) {
        var canInstall = true
        suspend fun add(item: Item) {
            if (item.status == Status.FAILED) canInstall = false
            withContext(Dispatchers.Main) { report(item) }
        }
        val installer = HorizonInstaller(context) {}
        val adb = adb()
        add(adb)
        add(if (adb.status == Status.OK) panel(installer) else item(R.string.hz_onb_check_panel, Status.FAILED, R.string.hz_onb_check_needs_adb))
        add(factoryFiles(installer))
        add(firmware())
        add(space())
        canInstall
    }

    private suspend fun adb(): Item =
        if (adbReachable(context)) item(R.string.hz_onb_check_adb, Status.OK, R.string.hz_onb_check_adb_ok)
        else item(R.string.hz_onb_check_adb, Status.FAILED, R.string.hz_onb_check_adb_failed)

    private suspend fun panel(installer: HorizonInstaller): Item {
        val out = try {
            installer.queryPanel()
        } catch (e: Exception) {
            return Item(text(R.string.hz_onb_check_panel), Status.FAILED, e.message ?: e.javaClass.simpleName)
        }
        if (ThemeToolOutput.exitCode(out) != 0)
            return Item(text(R.string.hz_onb_check_panel), Status.FAILED, context.getString(R.string.hz_onb_check_panel_refused, ThemeToolOutput.reason(out)))
        return when (ThemeToolOutput.selection(out)) {
            ThemeToolOutput.Selection.FACTORY -> item(R.string.hz_onb_check_panel, Status.OK, R.string.hz_onb_check_panel_factory)
            ThemeToolOutput.Selection.HORIZON -> item(R.string.hz_onb_check_panel, Status.OK, R.string.hz_onb_check_panel_horizon)
            else -> item(R.string.hz_onb_check_panel, Status.FAILED, R.string.hz_onb_check_panel_other)
        }
    }

    private fun factoryFiles(installer: HorizonInstaller): Item = try {
        installer.buildCarrier()
        item(R.string.hz_onb_check_files, Status.OK, R.string.hz_onb_check_files_ok)
    } catch (e: HorizonCarrier.Refused) {
        Item(text(R.string.hz_onb_check_files), Status.FAILED, e.message ?: "")
    } catch (e: Exception) {
        Item(text(R.string.hz_onb_check_files), Status.FAILED, "${e.javaClass.simpleName}: ${e.message}")
    }

    private fun firmware(): Item =
        if (Build.FINGERPRINT == HorizonCarrier.TESTED_FINGERPRINT) item(R.string.hz_onb_check_firmware, Status.OK, R.string.hz_onb_check_firmware_tested)
        else Item(text(R.string.hz_onb_check_firmware), Status.WARNING, context.getString(R.string.hz_onb_check_firmware_other, Build.VERSION.INCREMENTAL))

    private fun space(): Item {
        @Suppress("DEPRECATION")
        val free = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
        return if (free >= NEEDED_BYTES) Item(text(R.string.hz_onb_check_space), Status.OK, context.getString(R.string.hz_onb_check_space_ok, free / MB))
        else Item(text(R.string.hz_onb_check_space), Status.FAILED, context.getString(R.string.hz_onb_check_space_low, NEEDED_BYTES / MB, free / MB))
    }

    private fun item(title: Int, status: Status, detail: Int) = Item(text(title), status, text(detail))

    private fun text(id: Int) = context.getString(id)

    companion object {
        private const val ADB_PROBE = "horizon-adb-ok"
        private const val MB = 1024L * 1024
        // The backup of the original panel (about 290 MB on SX309) plus the carrier and some room.
        private const val NEEDED_BYTES = 400 * MB

        /**
         * Whether local ADB answers. The first time, the head unit asks the driver to allow this
         * app's key; the connection waits about 8 s for that, so a slower answer needs a second try.
         */
        suspend fun adbReachable(context: Context): Boolean {
            val (code, out) = AdbManager.exec(context.applicationContext, "echo $ADB_PROBE", maxReadMs = 15_000)
            return code == 0 && out.contains(ADB_PROBE)
        }
    }
}
