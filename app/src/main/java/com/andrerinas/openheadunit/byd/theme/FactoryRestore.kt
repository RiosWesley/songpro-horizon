package com.andrerinas.openheadunit.byd.theme

import android.content.Context
import com.andrerinas.openheadunit.byd.BydNativeAndroidAuto
import com.andrerinas.openheadunit.byd.wheel.WheelMenuSwitch
import com.andrerinas.openheadunit.utils.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Back to the car as it came: everything Song Pro Horizon changed on it is undone, and the factory
 * panel, without any change, shows after one restart.
 * - every file of Horizon's leaves the car ([HorizonInstaller.removeEverything]);
 * - BYD's own Android Auto comes back on, and this app's USB auto-start goes off;
 * - the wheel menu's accessibility service goes off.
 * The backup of the original panel ([OriginalBackup], outside the cluster's folders) and this app
 * stay; uninstalling the app is the driver's call.
 */
class FactoryRestore(private val context: Context, private val log: (String) -> Unit) {

    suspend fun run(): HorizonInstaller.Outcome = withContext(Dispatchers.IO + NonCancellable) {
        val installer = HorizonInstaller(context, log)
        val theme = installer.removeEverything()
        if (theme is HorizonInstaller.Outcome.Failed) return@withContext theme

        val problems = mutableListOf<String>()
        if (BydNativeAndroidAuto.state(context) == BydNativeAndroidAuto.State.DISABLED) {
            log("Religando o Android Auto da BYD")
            BydNativeAndroidAuto.useThisApp(context, thisApp = false)?.let { problems += "Android Auto da BYD ($it)" }
        }
        if (WheelMenuSwitch.isOn(context)) {
            log("Desligando o menu do volante")
            WheelMenuSwitch.set(context, on = false)?.let { problems += "menu do volante ($it)" }
        }
        // A later install starts from Horizon in the Simples slot again.
        Settings(context).horizonSkin = "horizon"

        when {
            problems.isNotEmpty() -> HorizonInstaller.Outcome.Failed("Não consegui desfazer: ${problems.joinToString()}. Tente de novo.")
            // Only the theme needs the restart; the rest is already as it came.
            theme is HorizonInstaller.Outcome.RestartNeeded ->
                HorizonInstaller.Outcome.RestartNeeded("Tudo desfeito. O painel de fábrica volta depois de reiniciar.")
            else -> HorizonInstaller.Outcome.NothingToDo("Tudo desfeito. O painel já é o de fábrica.")
        }
    }
}
