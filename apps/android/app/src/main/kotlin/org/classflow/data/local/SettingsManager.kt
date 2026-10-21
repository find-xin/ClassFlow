package org.classflow.data.local

import android.content.Context
import android.net.Uri
import org.classflow.data.model.AppSettings
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object SettingsManager {
    private const val PREFS_NAME = "classflow_settings"
    private const val KEY_SETTINGS_JSON = "key_settings_json"

    fun loadSettings(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SETTINGS_JSON, null) ?: return AppSettings()
        return try {
            val obj = JSONObject(jsonStr)
            AppSettings(
                themeMode = obj.optString("themeMode", "system"),
                enableStudyModule = obj.optBoolean("enableStudyModule", true),
                showWeekends = obj.optBoolean("showWeekends", true),
                showNonCurrentWeekCourses = obj.optBoolean("showNonCurrentWeekCourses", true),
                cardOpacity = obj.optDouble("cardOpacity", 0.88).toFloat(),
                wallpaperPreset = obj.optString("wallpaperPreset", "default"),
                customWallpaperUri = if (obj.has("customWallpaperUri")) obj.optString("customWallpaperUri") else null,
                usePureColorBackground = obj.optBoolean("usePureColorBackground", false),
                headerColorHex = obj.optString("headerColorHex", "#1F1F1F"),
                showGridGuideLines = obj.optBoolean("showGridGuideLines", false),
                showSectionTimeInAxis = obj.optBoolean("showSectionTimeInAxis", true),
                showSaturday = obj.optBoolean("showSaturday", true),
                showSunday = obj.optBoolean("showSunday", true),
                cardTextColorMode = obj.optString("cardTextColorMode", "auto"),
                cardBorderColorMode = obj.optString("cardBorderColorMode", "none"),
                gridHeight = obj.optDouble("gridHeight", 64.0).toFloat(),
                cardCornerRadius = obj.optDouble("cardCornerRadius", 6.0).toFloat(),
                nonCurrentWeekOpacityRatio = obj.optDouble("nonCurrentWeekOpacityRatio", 0.50).toFloat(),
                cardFontSize = obj.optDouble("cardFontSize", 13.0).toFloat(),
                showTimeInCard = obj.optBoolean("showTimeInCard", false),
                showLocationInCard = obj.optBoolean("showLocationInCard", true),
                showAtPrefixForRoom = obj.optBoolean("showAtPrefixForRoom", true),
                showTeacherInCard = obj.optBoolean("showTeacherInCard", true),
                showNotesInCard = obj.optBoolean("showNotesInCard", false),
                cardTextHorizontalCenter = obj.optBoolean("cardTextHorizontalCenter", false),
                cardTextVerticalCenter = obj.optBoolean("cardTextVerticalCenter", false),
                cardUseDottedBorder = obj.optBoolean("cardUseDottedBorder", false),
                cardBorderUseCardColor = obj.optBoolean("cardBorderUseCardColor", true),
                cardTextBlendCardColor = obj.optBoolean("cardTextBlendCardColor", false),
                syncInterval = obj.optString("syncInterval", "manual"),
                autoSyncEnabled = obj.optBoolean("autoSyncEnabled", false),
                syncOnStartup = obj.optBoolean("syncOnStartup", false),
                lastSyncedTime = if (obj.has("lastSyncedTime")) obj.optString("lastSyncedTime") else null,
                calendarName = obj.optString("calendarName", "ClassFlow"),
                calendarReminderMinutes = if (obj.has("calendarReminderMinutes")) obj.optInt("calendarReminderMinutes") else 15,
                calendarIncludeNotes = obj.optBoolean("calendarIncludeNotes", true)
            )
        } catch (e: Throwable) {
            AppSettings()
        }
    }

    fun saveSettings(context: Context, settings: AppSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val obj = JSONObject().apply {
                put("themeMode", settings.themeMode)
                put("enableStudyModule", settings.enableStudyModule)
                put("showWeekends", settings.showWeekends)
                put("showNonCurrentWeekCourses", settings.showNonCurrentWeekCourses)
                put("cardOpacity", settings.cardOpacity.toDouble())
                put("wallpaperPreset", settings.wallpaperPreset)
                if (settings.customWallpaperUri != null) {
                    put("customWallpaperUri", settings.customWallpaperUri)
                }
                put("usePureColorBackground", settings.usePureColorBackground)
                put("headerColorHex", settings.headerColorHex)
                put("showGridGuideLines", settings.showGridGuideLines)
                put("showSectionTimeInAxis", settings.showSectionTimeInAxis)
                put("showSaturday", settings.showSaturday)
                put("showSunday", settings.showSunday)
                put("cardTextColorMode", settings.cardTextColorMode)
                put("cardBorderColorMode", settings.cardBorderColorMode)
                put("gridHeight", settings.gridHeight.toDouble())
                put("cardCornerRadius", settings.cardCornerRadius.toDouble())
                put("nonCurrentWeekOpacityRatio", settings.nonCurrentWeekOpacityRatio.toDouble())
                put("cardFontSize", settings.cardFontSize.toDouble())
                put("showTimeInCard", settings.showTimeInCard)
                put("showLocationInCard", settings.showLocationInCard)
                put("showAtPrefixForRoom", settings.showAtPrefixForRoom)
                put("showTeacherInCard", settings.showTeacherInCard)
                put("showNotesInCard", settings.showNotesInCard)
                put("cardTextHorizontalCenter", settings.cardTextHorizontalCenter)
                put("cardTextVerticalCenter", settings.cardTextVerticalCenter)
                put("cardUseDottedBorder", settings.cardUseDottedBorder)
                put("cardBorderUseCardColor", settings.cardBorderUseCardColor)
                put("cardTextBlendCardColor", settings.cardTextBlendCardColor)
                put("syncInterval", settings.syncInterval)
                put("autoSyncEnabled", settings.autoSyncEnabled)
                put("syncOnStartup", settings.syncOnStartup)
                if (settings.lastSyncedTime != null) {
                    put("lastSyncedTime", settings.lastSyncedTime)
                }
                put("calendarName", settings.calendarName)
                if (settings.calendarReminderMinutes != null) {
                    put("calendarReminderMinutes", settings.calendarReminderMinutes)
                }
                put("calendarIncludeNotes", settings.calendarIncludeNotes)
            }
            prefs.edit().putString(KEY_SETTINGS_JSON, obj.toString()).apply()
        } catch (e: Throwable) {
            // Log or ignore safely
        }
    }

    /**
     * Copies the content from chosen URI to the app's internal storage
     * so that the file remains accessible forever across process restarts.
     */
    fun saveCustomWallpaperFromUri(context: Context, sourceUri: Uri): String? {
        return try {
            val destFile = File(context.filesDir, "custom_wallpaper.png")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Throwable) {
            null
        }
    }

    // MARK: - BYOC Cloud Sync Credentials & Device ID

    data class SyncCredentials(
        val providerType: String = "s3", // "s3", "webdav", "none"
        val s3Endpoint: String = "",
        val s3Region: String = "",
        val s3Bucket: String = "",
        val s3AccessKey: String = "",
        val s3SecretKey: String = "",
        val s3PathPrefix: String = "classflow-sync/",
        val webdavUrl: String = "",
        val webdavUsername: String = "",
        val webdavPassword: String = "",
        val webdavRootDir: String = "ClassFlow"
    )

    private const val PREFS_SYNC = "classflow_sync_prefs"
    private const val KEY_SYNC_CREDENTIALS = "sync_credentials_json"
    private const val KEY_SYNC_DEVICE_ID = "sync_device_id"
    private const val KEY_SYNC_SEQUENCE = "sync_sequence"

    fun loadSyncCredentials(context: Context): SyncCredentials {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SYNC_CREDENTIALS, null) ?: return SyncCredentials()
        return try {
            val obj = JSONObject(jsonStr)
            SyncCredentials(
                providerType = obj.optString("providerType", "s3"),
                s3Endpoint = obj.optString("s3Endpoint", ""),
                s3Region = obj.optString("s3Region", ""),
                s3Bucket = obj.optString("s3Bucket", ""),
                s3AccessKey = obj.optString("s3AccessKey", ""),
                s3SecretKey = obj.optString("s3SecretKey", ""),
                s3PathPrefix = obj.optString("s3PathPrefix", "classflow-sync/"),
                webdavUrl = obj.optString("webdavUrl", ""),
                webdavUsername = obj.optString("webdavUsername", ""),
                webdavPassword = obj.optString("webdavPassword", ""),
                webdavRootDir = obj.optString("webdavRootDir", "ClassFlow")
            )
        } catch (_: Exception) {
            SyncCredentials()
        }
    }

    fun saveSyncCredentials(context: Context, creds: SyncCredentials) {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        val obj = JSONObject().apply {
            put("providerType", creds.providerType)
            put("s3Endpoint", creds.s3Endpoint)
            put("s3Region", creds.s3Region)
            put("s3Bucket", creds.s3Bucket)
            put("s3AccessKey", creds.s3AccessKey)
            put("s3SecretKey", creds.s3SecretKey)
            put("s3PathPrefix", creds.s3PathPrefix)
            put("webdavUrl", creds.webdavUrl)
            put("webdavUsername", creds.webdavUsername)
            put("webdavPassword", creds.webdavPassword)
            put("webdavRootDir", creds.webdavRootDir)
        }
        prefs.edit().putString(KEY_SYNC_CREDENTIALS, obj.toString()).apply()
    }

    fun getOrCreateSyncDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        var devId = prefs.getString(KEY_SYNC_DEVICE_ID, null)
        if (devId.isNullOrBlank()) {
            devId = "dev_droid_" + UUID.randomUUID().toString().take(8).lowercase()
            prefs.edit().putString(KEY_SYNC_DEVICE_ID, devId).apply()
        }
        return devId
    }

    fun getSyncSequence(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SYNC_SEQUENCE, 0)
    }

    fun setSyncSequence(context: Context, seq: Int) {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SYNC_SEQUENCE, seq).apply()
    }
}
