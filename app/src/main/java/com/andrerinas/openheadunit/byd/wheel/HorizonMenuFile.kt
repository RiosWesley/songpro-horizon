package com.andrerinas.openheadunit.byd.wheel

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The menu as the Horizon theme reads it (horizon/qml/HorizonMenuFeed.qml): menu.json next to the
 * now-playing feed, in the Android cell's /sdcard/.cluster/horizon/.
 *   { "seq": n, "open": bool, "selected": i, "items": [{ "label", "value" }], "leftCard", "powerStyle", "mapLayout" }
 * (the choices' keys are HorizonOptions').
 * Written to a temp file and renamed, so the theme never reads half of it.
 */
internal class HorizonMenuFile(
    private val dir: File = File(Environment.getExternalStorageDirectory(), ".cluster/horizon"),
) {
    // Clock-based, so a write from the BYD panel and one from the wheel service never share a
    // number (the theme skips a number it has seen).
    private var seq = 0L

    fun write(state: WheelMenu.State, leftCard: String, powerStyle: String, mapLayout: String) {
        val items = JSONArray()
        for (item in state.items) items.put(JSONObject().put("label", item.label).put("value", item.value))
        val json = JSONObject()
            .put("seq", maxOf(seq + 1, System.currentTimeMillis()).also { seq = it })
            .put("open", state.open)
            .put("selected", state.selected)
            .put("items", items)
            .put("leftCard", leftCard)
            .put("powerStyle", powerStyle)
            .put("mapLayout", mapLayout)
        dir.mkdirs()
        val target = File(dir, FILE)
        val tmp = File(dir, ".$FILE.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(target)) {
            target.writeText(json.toString())
            tmp.delete()
        }
    }

    companion object {
        const val FILE = "menu.json"
    }
}
