package org.classflow.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.classflow.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data bundle capturing 100% of the application state across all modules:
 * - Timetable courses & schedules
 * - Calendar events & Agenda schedules (早晚/全天日程)
 * - Reminders & Checklist ToDos (待办事项清单)
 * - Study focus tasks & timer records (自习任务与专注历史打卡)
 * - User appearance, timetable, and sync configurations
 */
data class FullSyncBundle(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String = UUID.randomUUID().toString(),
    val courses: List<Course> = emptyList(),
    val schedules: List<CalendarEvent> = emptyList(),
    val todos: List<CalendarEvent> = emptyList(),
    val studyTasks: List<StudyTask> = emptyList(),
    val focusRecords: List<FocusRecord> = emptyList(),
    val settings: AppSettings = AppSettings()
)

/**
 * Full-scale Multi-Module Cloud Synchronization Manager:
 * Synchronizes all courses, schedules, todos, study tasks, focus records, and settings.
 * Supports configurable sync intervals (Manual, On Launch, 15m, 30m, 1h, 6h, Daily).
 */
object FullSyncManager {
    const val SYNC_FILE_NAME = "classflow_full_sync.json"

    var lastSyncTimestamp: Long = 0L
    var lastSyncStatus: String = "尚未同步"

    /**
     * Serializes FullSyncBundle to JSON String.
     */
    fun serializeBundle(bundle: FullSyncBundle): String {
        val root = JSONObject()
        root.put("version", bundle.version)
        root.put("timestamp", bundle.timestamp)
        root.put("deviceId", bundle.deviceId)

        // Courses
        val coursesArr = JSONArray()
        bundle.courses.forEach { c ->
            val cObj = JSONObject()
            cObj.put("id", c.id)
            cObj.put("semesterId", c.semesterId)
            cObj.put("name", c.name)
            cObj.put("teacher", c.teacher ?: "")
            cObj.put("color", c.color)
            cObj.put("notes", c.notes ?: "")

            val schedArr = JSONArray()
            c.schedules.forEach { s ->
                val sObj = JSONObject()
                sObj.put("id", s.id)
                sObj.put("dayOfWeek", s.dayOfWeek)
                sObj.put("startSection", s.startSection)
                sObj.put("endSection", s.endSection)
                sObj.put("classroom", s.classroom ?: "")
                sObj.put("weekPattern", s.weekPattern.name)
                val weeksArr = JSONArray()
                s.customWeeks.forEach { weeksArr.put(it) }
                sObj.put("customWeeks", weeksArr)
                schedArr.put(sObj)
            }
            cObj.put("schedules", schedArr)
            coursesArr.put(cObj)
        }
        root.put("courses", coursesArr)

        // Schedules (Calendar Events)
        val schedsArr = JSONArray()
        bundle.schedules.forEach { s ->
            val sObj = JSONObject()
            sObj.put("id", s.id)
            sObj.put("title", s.title)
            sObj.put("startTime", s.startTime)
            sObj.put("endTime", s.endTime)
            sObj.put("isAllDay", s.isAllDay)
            sObj.put("location", s.location ?: "")
            sObj.put("description", s.description ?: "")
            sObj.put("color", s.color)
            sObj.put("frequency", s.frequency)
            schedsArr.put(sObj)
        }
        root.put("schedules", schedsArr)

        // ToDos (Tasks)
        val todosArr = JSONArray()
        bundle.todos.forEach { t ->
            val tObj = JSONObject()
            tObj.put("id", t.id)
            tObj.put("title", t.title)
            tObj.put("isCompleted", t.isCompleted)
            tObj.put("color", t.color)
            tObj.put("description", t.description ?: "")
            todosArr.put(tObj)
        }
        root.put("todos", todosArr)

        // Study Tasks
        val studyTasksArr = JSONArray()
        bundle.studyTasks.forEach { st ->
            val stObj = JSONObject()
            stObj.put("id", st.id)
            stObj.put("title", st.title)
            stObj.put("courseName", st.courseName ?: "")
            stObj.put("targetMinutes", st.targetMinutes)
            stObj.put("completedMinutes", st.completedMinutes)
            stObj.put("isCompleted", st.isCompleted)
            studyTasksArr.put(stObj)
        }
        root.put("studyTasks", studyTasksArr)

        // Focus Records
        val recordsArr = JSONArray()
        bundle.focusRecords.forEach { fr ->
            val rObj = JSONObject()
            rObj.put("id", fr.id)
            rObj.put("taskTitle", fr.taskTitle)
            rObj.put("courseName", fr.courseName ?: "")
            rObj.put("durationMinutes", fr.durationMinutes)
            rObj.put("completedAt", fr.completedAt)
            recordsArr.put(rObj)
        }
        root.put("focusRecords", recordsArr)

        // Settings
        val setObj = JSONObject()
        setObj.put("themeMode", bundle.settings.themeMode)
        setObj.put("enableStudyModule", bundle.settings.enableStudyModule)
        setObj.put("showWeekends", bundle.settings.showWeekends)
        setObj.put("cardOpacity", bundle.settings.cardOpacity.toDouble())
        setObj.put("wallpaperPreset", bundle.settings.wallpaperPreset)
        setObj.put("syncInterval", bundle.settings.syncInterval)
        setObj.put("autoSyncEnabled", bundle.settings.autoSyncEnabled)
        root.put("settings", setObj)

        return root.toString(2)
    }

    /**
     * Parses FullSyncBundle from JSON String.
     */
    fun deserializeBundle(jsonStr: String): FullSyncBundle {
        val root = JSONObject(jsonStr)
        val version = root.optInt("version", 1)
        val timestamp = root.optLong("timestamp", System.currentTimeMillis())
        val deviceId = root.optString("deviceId", UUID.randomUUID().toString())

        // Courses
        val courses = mutableListOf<Course>()
        val coursesArr = root.optJSONArray("courses")
        if (coursesArr != null) {
            for (i in 0 until coursesArr.length()) {
                val cObj = coursesArr.getJSONObject(i)
                val schedList = mutableListOf<CourseSchedule>()
                val schedArr = cObj.optJSONArray("schedules")
                if (schedArr != null) {
                    for (j in 0 until schedArr.length()) {
                        val sObj = schedArr.getJSONObject(j)
                        val customWeeks = mutableListOf<Int>()
                        val weeksArr = sObj.optJSONArray("customWeeks")
                        if (weeksArr != null) {
                            for (w in 0 until weeksArr.length()) {
                                customWeeks.add(weeksArr.getInt(w))
                            }
                        }
                        val wpName = sObj.optString("weekPattern", "ALL")
                        val wp = try { WeekPatternType.valueOf(wpName) } catch (e: Exception) { WeekPatternType.ALL }
                        schedList.add(
                            CourseSchedule(
                                id = sObj.optString("id", UUID.randomUUID().toString()),
                                dayOfWeek = sObj.optInt("dayOfWeek", 1),
                                startSection = sObj.optInt("startSection", 1),
                                endSection = sObj.optInt("endSection", 2),
                                classroom = sObj.optString("classroom").ifEmpty { null },
                                weekPattern = wp,
                                customWeeks = customWeeks
                            )
                        )
                    }
                }
                courses.add(
                    Course(
                        id = cObj.optString("id", UUID.randomUUID().toString()),
                        semesterId = cObj.optString("semesterId", "sem_1"),
                        name = cObj.optString("name", "未命名课程"),
                        teacher = cObj.optString("teacher").ifEmpty { null },
                        color = cObj.optString("color", "#4F46E5"),
                        notes = cObj.optString("notes").ifEmpty { null },
                        schedules = schedList
                    )
                )
            }
        }

        // Schedules
        val schedules = mutableListOf<CalendarEvent>()
        val schedArr = root.optJSONArray("schedules")
        if (schedArr != null) {
            for (i in 0 until schedArr.length()) {
                val sObj = schedArr.getJSONObject(i)
                schedules.add(
                    CalendarEvent(
                        id = sObj.optString("id", UUID.randomUUID().toString()),
                        title = sObj.optString("title", "未命名日程"),
                        startTime = sObj.optString("startTime", "08:00"),
                        endTime = sObj.optString("endTime", "09:00"),
                        isAllDay = sObj.optBoolean("isAllDay", false),
                        location = sObj.optString("location").ifEmpty { null },
                        description = sObj.optString("description").ifEmpty { null },
                        color = sObj.optString("color", "#039BE5"),
                        frequency = sObj.optString("frequency", "none"),
                        isTask = false
                    )
                )
            }
        }

        // ToDos
        val todos = mutableListOf<CalendarEvent>()
        val todosArr = root.optJSONArray("todos")
        if (todosArr != null) {
            for (i in 0 until todosArr.length()) {
                val tObj = todosArr.getJSONObject(i)
                todos.add(
                    CalendarEvent(
                        id = tObj.optString("id", UUID.randomUUID().toString()),
                        title = tObj.optString("title", "未命名待办"),
                        isCompleted = tObj.optBoolean("isCompleted", false),
                        color = tObj.optString("color", "#059669"),
                        description = tObj.optString("description").ifEmpty { null },
                        isTask = true
                    )
                )
            }
        }

        // Study Tasks
        val studyTasks = mutableListOf<StudyTask>()
        val studyArr = root.optJSONArray("studyTasks")
        if (studyArr != null) {
            for (i in 0 until studyArr.length()) {
                val stObj = studyArr.getJSONObject(i)
                studyTasks.add(
                    StudyTask(
                        id = stObj.optString("id", UUID.randomUUID().toString()),
                        title = stObj.optString("title", "自习任务"),
                        courseName = stObj.optString("courseName").ifEmpty { null },
                        targetMinutes = stObj.optInt("targetMinutes", 45),
                        completedMinutes = stObj.optInt("completedMinutes", 0),
                        isCompleted = stObj.optBoolean("isCompleted", false)
                    )
                )
            }
        }

        // Focus Records
        val focusRecords = mutableListOf<FocusRecord>()
        val recArr = root.optJSONArray("focusRecords")
        if (recArr != null) {
            for (i in 0 until recArr.length()) {
                val rObj = recArr.getJSONObject(i)
                focusRecords.add(
                    FocusRecord(
                        id = rObj.optString("id", UUID.randomUUID().toString()),
                        taskTitle = rObj.optString("taskTitle", "专注记录"),
                        courseName = rObj.optString("courseName").ifEmpty { null },
                        durationMinutes = rObj.optInt("durationMinutes", 25),
                        completedAt = rObj.optString("completedAt", "")
                    )
                )
            }
        }

        // Settings
        val setObj = root.optJSONObject("settings")
        val settings = if (setObj != null) {
            AppSettings(
                themeMode = setObj.optString("themeMode", "system"),
                enableStudyModule = setObj.optBoolean("enableStudyModule", true),
                showWeekends = setObj.optBoolean("showWeekends", true),
                cardOpacity = setObj.optDouble("cardOpacity", 0.88).toFloat(),
                wallpaperPreset = setObj.optString("wallpaperPreset", "default"),
                syncInterval = setObj.optString("syncInterval", "manual"),
                autoSyncEnabled = setObj.optBoolean("autoSyncEnabled", false)
            )
        } else AppSettings()

        return FullSyncBundle(
            version = version,
            timestamp = timestamp,
            deviceId = deviceId,
            courses = courses,
            schedules = schedules,
            todos = todos,
            studyTasks = studyTasks,
            focusRecords = focusRecords,
            settings = settings
        )
    }

    /**
     * Executes bidirectional sync with WebDAV.
     * Uploads local bundle if newer or downloads remote bundle if remote is newer.
     */
    suspend fun syncWithWebDAV(
        provider: WebDAVStorageProvider,
        localBundle: FullSyncBundle
    ): Result<FullSyncBundle?> = withContext(Dispatchers.IO) {
        try {
            // 1. Download existing remote file
            val downloadRes = provider.downloadFile(SYNC_FILE_NAME)
            val remoteJson = downloadRes.getOrNull()

            val remoteBundle = if (remoteJson != null) {
                try {
                    deserializeBundle(String(remoteJson, Charsets.UTF_8))
                } catch (e: Exception) {
                    null
                }
            } else null

            if (remoteBundle != null && remoteBundle.timestamp > localBundle.timestamp) {
                // Remote is newer: adopt remote bundle
                lastSyncTimestamp = System.currentTimeMillis()
                val df = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                lastSyncStatus = "已从云端恢复 (${df.format(Date(lastSyncTimestamp))})"
                Result.success(remoteBundle)
            } else {
                // Local is newer or remote does not exist: upload local bundle
                val jsonPayload = serializeBundle(localBundle)
                val uploadRes = provider.uploadFile(SYNC_FILE_NAME, jsonPayload.toByteArray(Charsets.UTF_8))
                if (uploadRes.isSuccess) {
                    lastSyncTimestamp = System.currentTimeMillis()
                    val df = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    lastSyncStatus = "已同步到云端 (${df.format(Date(lastSyncTimestamp))})"
                    Result.success(null) // Local remains authorative
                } else {
                    val err = uploadRes.exceptionOrNull()?.message ?: "上传失败"
                    lastSyncStatus = "同步失败: $err"
                    Result.failure(Exception(err))
                }
            }
        } catch (e: Exception) {
            lastSyncStatus = "同步异常: ${e.message}"
            Result.failure(e)
        }
    }
}
