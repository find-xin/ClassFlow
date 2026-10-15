package org.classflow.band

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.classflow.data.model.TimelineItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Xiaomi Smart Band 10 & AstroBox Timetable Bridge.
 * Generates compact JSON payload formatted for Nexio / ClassFlow band applets,
 * and exports it to the AstroBox app storage directory or Bluetooth BLE cache.
 */
class BandSyncManager(private val context: Context) {

    /**
     * Serializes current week occurrences into the Nexio-compatible band JSON structure.
     */
    fun serializeForBand(
        currentWeek: Int,
        occurrences: List<TimelineItem>,
        nextClass: TimelineItem? = null
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("currentWeek", currentWeek)
        root.put("updatedAt", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))

        // 1. Next Class Hero Item
        if (nextClass != null) {
            val nextObj = JSONObject()
            nextObj.put("title", nextClass.title)
            nextObj.put("location", nextClass.location ?: "")
            nextObj.put("teacher", nextClass.teacher ?: "")
            nextObj.put("startSection", nextClass.startSection)
            nextObj.put("endSection", nextClass.endSection)
            nextObj.put("startTime", nextClass.startTime ?: "")
            nextObj.put("endTime", nextClass.endTime ?: "")
            nextObj.put("statusText", "即将上课 ⏱")
            root.put("nextClass", nextObj)
        }

        // 2. Schedule Data by Day Index (0: Mon, 1: Tue, ... 6: Sun)
        val scheduleData = JSONObject()
        for (dayIdx in 0..6) {
            val dayOfWeekNum = dayIdx + 1 // 1: Mon .. 7: Sun
            val dayItems = occurrences.filter { it.dayOfWeek == dayOfWeekNum }
            val courseArr = JSONArray()

            for (item in dayItems) {
                val cObj = JSONObject()
                cObj.put("title", item.title)
                cObj.put("location", item.location ?: "")
                cObj.put("startSection", item.startSection)
                cObj.put("endSection", item.endSection)
                cObj.put("startTime", item.startTime ?: "")
                cObj.put("endTime", item.endTime ?: "")
                cObj.put("colorHex", item.color)
                courseArr.put(cObj)
            }
            scheduleData.put(dayIdx.toString(), courseArr)
        }
        root.put("scheduleData", scheduleData)

        return root.toString(2)
    }

    /**
     * Writes the timetable payload to the shared AstroBox staging path.
     * Path: /sdcard/Android/data/com.astrobox.band/files/apps/org.classflow.band/data.json
     * or app's external files directory for AstroBox manual import.
     */
    suspend fun pushToAstroBox(jsonPayload: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(context.getExternalFilesDir(null), "band_exports")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, "classflow_band_schedule.json")
            targetFile.writeText(jsonPayload, Charsets.UTF_8)

            // Also attempt writing to common AstroBox shared directory if accessible
            val astroBoxDir = File("/sdcard/AstroBox/apps/org.classflow.band")
            if (astroBoxDir.exists()) {
                val astroFile = File(astroBoxDir, "data.json")
                astroFile.writeText(jsonPayload, Charsets.UTF_8)
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
