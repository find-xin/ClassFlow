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
            if (c.credit != null) cObj.put("credit", c.credit)
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
                sObj.put("teacher", s.teacher ?: "")
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
            if (s.semesterId != null) sObj.put("semesterId", s.semesterId)
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
            if (t.semesterId != null) tObj.put("semesterId", t.semesterId)
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
            if (st.semesterId != null) stObj.put("semesterId", st.semesterId)
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
        setObj.put("syncOnStartup", bundle.settings.syncOnStartup)
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
                                teacher = sObj.optString("teacher").ifEmpty { null },
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
                        credit = if (cObj.has("credit") && !cObj.isNull("credit")) {
                            val num = cObj.optDouble("credit")
                            if (num.isNaN()) null else num
                        } else null,
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
                        semesterId = sObj.optString("semesterId").ifEmpty { null },
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
                        semesterId = tObj.optString("semesterId").ifEmpty { null },
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
                        semesterId = stObj.optString("semesterId").ifEmpty { null },
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
                autoSyncEnabled = setObj.optBoolean("autoSyncEnabled", false),
                syncOnStartup = setObj.optBoolean("syncOnStartup", false)
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

    internal fun mergePeerSnapshot(
        peerSnapJson: JSONObject,
        localCourses: MutableList<Course>,
        localSchedules: MutableList<CalendarEvent>,
        localExceptions: MutableList<ScheduleException>,
        localSemesters: MutableList<Semester>,
        localFocusRecords: MutableList<FocusRecord>,
        localStudyTasks: MutableList<StudyTask>,
        localTombstones: MutableList<SyncTombstone>
    ): Int {
        var downloadedCount = 0

        // 1. Ingest peer tombstones first (with GC)
        val peerTombstonesArr = peerSnapJson.optJSONArray("tombstones")
        if (peerTombstonesArr != null) {
            val peerTombstones = org.classflow.data.local.CourseDataManager.deserializeTombstones(peerTombstonesArr.toString())
            val validPeerTombstones = org.classflow.data.local.CourseDataManager.garbageCollectTombstones(peerTombstones)
            for (pt in validPeerTombstones) {
                val existing = localTombstones.find { it.entityId == pt.entityId }
                if (existing == null) {
                    localTombstones.add(pt)
                } else if (pt.deletedAt > existing.deletedAt) {
                    localTombstones.remove(existing)
                    localTombstones.add(pt)
                }
            }
        }
        val gcedTombstones = org.classflow.data.local.CourseDataManager.garbageCollectTombstones(localTombstones)
        localTombstones.clear()
        localTombstones.addAll(gcedTombstones)

        val peerCoursesArr = peerSnapJson.optJSONArray("courses")
        val peerCourses = if (peerCoursesArr != null) org.classflow.data.local.CourseDataManager.deserializeCourses(peerCoursesArr.toString()) else emptyList()
        val peerSchedulesArr = peerSnapJson.optJSONArray("calendarEvents") ?: peerSnapJson.optJSONArray("schedules")
        val peerScheds = if (peerSchedulesArr != null) org.classflow.data.local.CourseDataManager.deserializeSchedules(peerSchedulesArr.toString()) else emptyList()

        val peerTaskArr = peerSnapJson.optJSONArray("pomodoroTasks") ?: peerSnapJson.optJSONArray("studyTasks")
        val peerTasks = if (peerTaskArr != null) org.classflow.data.local.CourseDataManager.deserializeStudyTasks(peerTaskArr.toString()) else emptyList()

        val tombstoneIds = localTombstones.map { it.entityId }.toSet()

        // 2. Purge local entities that match tombstones
        localCourses.removeAll { it.id in tombstoneIds }
        for (i in localCourses.indices) {
            val c = localCourses[i]
            val filteredScheds = c.schedules.filter { it.id !in tombstoneIds }
            if (filteredScheds.size != c.schedules.size) {
                localCourses[i] = c.copy(schedules = filteredScheds)
            }
        }
        localSchedules.removeAll { it.id in tombstoneIds }
        localExceptions.removeAll { it.id in tombstoneIds || it.scheduleId in tombstoneIds }
        localSemesters.removeAll { it.id in tombstoneIds }
        localFocusRecords.removeAll { it.id in tombstoneIds }
        localStudyTasks.removeAll { it.id in tombstoneIds }

        // 3. Merge Semesters first to establish peer-to-local semesterId mapping (by id or by name)
        val semesterIdMapping = mutableMapOf<String, String>()
        val peerSemestersArr = peerSnapJson.optJSONArray("semesters")
        if (peerSemestersArr != null) {
            val peerSems = org.classflow.data.local.CourseDataManager.deserializeSemesters(peerSemestersArr.toString())
            for (pSem in peerSems) {
                if (pSem.id in tombstoneIds) continue
                val trimmedName = pSem.name.trim()
                val existingById = localSemesters.indexOfFirst { it.id == pSem.id }
                val existingByName = localSemesters.indexOfFirst { it.name.trim() == trimmedName }

                val defaultTimes = org.classflow.data.local.CourseDataManager.defaultSectionTimes()
                if (existingById >= 0) {
                    semesterIdMapping[pSem.id] = pSem.id
                    val cur = localSemesters[existingById]
                    val isCurDefault = cur.sections.isEmpty() || cur.sections == defaultTimes
                    val resolvedSections = if (pSem.sections.isNotEmpty() && (isCurDefault || pSem.sections != cur.sections)) pSem.sections else cur.sections
                    localSemesters[existingById] = cur.copy(
                        totalWeeks = maxOf(cur.totalWeeks, pSem.totalWeeks),
                        sections = resolvedSections
                    )
                } else if (existingByName >= 0) {
                    val cur = localSemesters[existingByName]
                    semesterIdMapping[pSem.id] = cur.id
                    val isCurDefault = cur.sections.isEmpty() || cur.sections == defaultTimes
                    val resolvedSections = if (pSem.sections.isNotEmpty() && (isCurDefault || pSem.sections != cur.sections)) pSem.sections else cur.sections
                    localSemesters[existingByName] = cur.copy(
                        totalWeeks = maxOf(cur.totalWeeks, pSem.totalWeeks),
                        sections = resolvedSections
                    )
                } else {
                    val finalSections = if (pSem.sections.isNotEmpty()) pSem.sections else defaultTimes
                    localSemesters.add(pSem.copy(sections = finalSections))
                    semesterIdMapping[pSem.id] = pSem.id
                }
            }
        }
        val defaultSemId = localSemesters.firstOrNull()?.id ?: ""

        // 4. Merge Courses
        for (pc in peerCourses) {
            if (pc.id in tombstoneIds) continue
            val targetSemId = pc.semesterId?.let {
                semesterIdMapping[it] ?: if (localSemesters.any { s -> s.id == it }) it else defaultSemId
            } ?: defaultSemId
            val pcName = pc.name.trim()

            val existingById = localCourses.indexOfFirst { it.id == pc.id }
            val existingByName = localCourses.indexOfFirst {
                (it.semesterId == targetSemId || (it.semesterId.isNullOrBlank() && targetSemId == defaultSemId)) && it.name.trim().equals(pcName, ignoreCase = true)
            }

            if (existingById >= 0) {
                val existing = localCourses[existingById]
                val mergedScheds = mergeCourseSchedules(existing.schedules, pc.schedules, tombstoneIds)
                localCourses[existingById] = existing.copy(
                    semesterId = targetSemId,
                    credit = pc.credit ?: existing.credit,
                    schedules = mergedScheds
                )
            } else if (existingByName >= 0) {
                val existing = localCourses[existingByName]
                val mergedScheds = mergeCourseSchedules(existing.schedules, pc.schedules, tombstoneIds)
                localCourses[existingByName] = existing.copy(
                    teacher = if (existing.teacher.isNullOrBlank()) pc.teacher else existing.teacher,
                    credit = if (existing.credit == null || existing.credit <= 0.0) pc.credit else existing.credit,
                    notes = if (existing.notes.isNullOrBlank()) pc.notes else existing.notes,
                    schedules = mergedScheds
                )
            } else {
                val filteredScheds = pc.schedules.filter { it.id !in tombstoneIds }
                localCourses.add(pc.copy(semesterId = targetSemId, schedules = filteredScheds))
                downloadedCount++
            }
        }

        val consolidatedCourses = org.classflow.data.local.CourseDataManager.consolidateCourses(localCourses).toList()
        localCourses.clear()
        localCourses.addAll(consolidatedCourses)

        // 5. Merge Schedules (Calendar Events)
        for (ps in peerScheds) {
            if (ps.id in tombstoneIds) continue
            val targetSemId = ps.semesterId?.let {
                semesterIdMapping[it] ?: if (localSemesters.any { s -> s.id == it }) it else defaultSemId
            } ?: defaultSemId
            val psTitle = ps.title.trim()

            val existingById = localSchedules.indexOfFirst { it.id == ps.id }
            val existingByTitle = localSchedules.indexOfFirst {
                (it.semesterId == targetSemId || (it.semesterId.isNullOrBlank() && targetSemId == defaultSemId)) &&
                it.title.trim() == psTitle && it.startTime == ps.startTime
            }

            if (existingById >= 0) {
                localSchedules[existingById] = ps.copy(semesterId = targetSemId)
            } else if (existingByTitle >= 0) {
                localSchedules[existingByTitle] = ps.copy(semesterId = targetSemId)
            } else {
                localSchedules.add(ps.copy(semesterId = targetSemId))
                downloadedCount++
            }
        }

        val consolidatedScheds = org.classflow.data.local.CourseDataManager.consolidateCalendarEvents(localSchedules).toList()
        localSchedules.clear()
        localSchedules.addAll(consolidatedScheds)

        // 6. Merge Exceptions
        val peerExceptionsArr = peerSnapJson.optJSONArray("exceptions")
        if (peerExceptionsArr != null) {
            val peerExceptions = org.classflow.data.local.CourseDataManager.deserializeExceptions(peerExceptionsArr.toString())
            for (pe in peerExceptions) {
                if (pe.id in tombstoneIds || pe.scheduleId in tombstoneIds) continue
                if (localExceptions.none { it.id == pe.id }) {
                    localExceptions.add(pe)
                    downloadedCount++
                }
            }
        }

        // 7. Merge Focus Records
        val peerFocusArr = peerSnapJson.optJSONArray("pomodoroRecords") ?: peerSnapJson.optJSONArray("focusRecords")
        if (peerFocusArr != null) {
            val peerRecords = org.classflow.data.local.CourseDataManager.deserializeFocusRecords(peerFocusArr.toString())
            for (pr in peerRecords) {
                if (pr.id in tombstoneIds) continue
                if (localFocusRecords.none { it.id == pr.id }) {
                    localFocusRecords.add(pr)
                    downloadedCount++
                }
            }
        }

        // 8. Merge Study Tasks
        for (pt in peerTasks) {
            if (pt.id in tombstoneIds) continue
            val targetSemId = pt.semesterId?.let { semesterIdMapping[it] ?: it } ?: defaultSemId
            val ptTitle = pt.title.trim()

            val existingById = localStudyTasks.indexOfFirst { it.id == pt.id }
            val existingByTitle = localStudyTasks.indexOfFirst {
                (it.semesterId == targetSemId || (it.semesterId.isNullOrBlank() && targetSemId == defaultSemId)) && it.title.trim() == ptTitle
            }

            if (existingById >= 0) {
                val cur = localStudyTasks[existingById]
                localStudyTasks[existingById] = cur.copy(
                    semesterId = targetSemId,
                    isCompleted = cur.isCompleted || pt.isCompleted,
                    completedMinutes = maxOf(cur.completedMinutes, pt.completedMinutes)
                )
            } else if (existingByTitle >= 0) {
                val cur = localStudyTasks[existingByTitle]
                localStudyTasks[existingByTitle] = cur.copy(
                    semesterId = targetSemId,
                    isCompleted = cur.isCompleted || pt.isCompleted,
                    completedMinutes = maxOf(cur.completedMinutes, pt.completedMinutes)
                )
            } else {
                localStudyTasks.add(pt.copy(semesterId = targetSemId))
                downloadedCount++
            }
        }

        return downloadedCount
    }

    private fun mergeCourseSchedules(
        localScheds: List<CourseSchedule>,
        peerScheds: List<CourseSchedule>,
        tombstoneIds: Set<String>
    ): List<CourseSchedule> {
        val result = localScheds.filter { it.id !in tombstoneIds }.toMutableList()
        for (ps in peerScheds) {
            if (ps.id in tombstoneIds) continue
            val overlapIdx = result.indexOfFirst {
                it.dayOfWeek == ps.dayOfWeek && maxOf(it.startSection, ps.startSection) <= minOf(it.endSection, ps.endSection)
            }
            if (overlapIdx >= 0) {
                val cur = result[overlapIdx]
                val mergedWeeks = (cur.customWeeks + ps.customWeeks).distinct().sorted()
                result[overlapIdx] = cur.copy(
                    startSection = minOf(cur.startSection, ps.startSection),
                    endSection = maxOf(cur.endSection, ps.endSection),
                    classroom = if (cur.classroom.isNullOrBlank()) ps.classroom else cur.classroom,
                    customWeeks = if (mergedWeeks.isNotEmpty()) mergedWeeks else cur.customWeeks
                )
            } else if (result.none { it.id == ps.id }) {
                result.add(ps)
            }
        }
        return result
    }

    /**
     * Executes decentralized synchronization adhering to docs/SYNC_PROTOCOL.md.
     * Uses device-isolated paths (devices/{deviceId}/) and snapshot bootstrapping.
     */
    suspend fun executeFullSync(context: Context): Result<SyncResultSummary> = withContext(Dispatchers.IO) {
        try {
            val creds = org.classflow.data.local.SettingsManager.loadSyncCredentials(context)
            if (creds.providerType == "none") {
                return@withContext Result.failure(Exception("请先在设置中选择并配置云存储服务 (S3 / WebDAV)"))
            }

            val deviceId = org.classflow.data.local.SettingsManager.getOrCreateSyncDeviceId(context)
            val currentSeq = org.classflow.data.local.SettingsManager.getSyncSequence(context)
            val nextSeq = currentSeq + 1

            val localCourses = org.classflow.data.local.CourseDataManager.loadCourses(context).toMutableList()
            val localSchedules = org.classflow.data.local.CourseDataManager.loadSchedules(context).toMutableList()
            val localSemesters = org.classflow.data.local.CourseDataManager.loadSemesters(context).toMutableList()
            val localExceptions = org.classflow.data.local.CourseDataManager.loadExceptions(context).toMutableList()
            val localFocusRecords = org.classflow.data.local.CourseDataManager.loadFocusRecords(context).toMutableList()
            val localStudyTasks = org.classflow.data.local.CourseDataManager.loadStudyTasks(context).toMutableList()
            val localTombstones = org.classflow.data.local.CourseDataManager.loadTombstones(context).toMutableList()

            var downloadedCount = 0
            var peersFound = 0

            val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val revFileName = String.format("%06d_%s.json", nextSeq, UUID.randomUUID().toString())

            if (creds.providerType == "s3") {
                if (creds.s3Bucket.isBlank() || creds.s3AccessKey.isBlank() || creds.s3SecretKey.isBlank()) {
                    return@withContext Result.failure(Exception("S3 桶名或 AccessKey/SecretKey 不能为空"))
                }
                val provider = S3StorageProvider(
                    endpoint = creds.s3Endpoint,
                    region = creds.s3Region,
                    bucket = creds.s3Bucket,
                    accessKey = creds.s3AccessKey,
                    secretKey = creds.s3SecretKey,
                    pathPrefix = creds.s3PathPrefix
                )

                // 1. Register self in global/device_registry.json
                val regPath = "global/device_registry.json"
                val regRead = provider.readObject(regPath)
                val regObj = if (regRead.isSuccess) {
                    try { JSONObject(String(regRead.getOrThrow(), Charsets.UTF_8)) } catch (_: Exception) { JSONObject() }
                } else JSONObject()

                val devicesMap = regObj.optJSONObject("devices") ?: JSONObject()
                devicesMap.put(deviceId, "Android Device (${android.os.Build.MODEL ?: "Phone"})")
                regObj.put("devices", devicesMap)
                val regBytes = regObj.toString(2).toByteArray(Charsets.UTF_8)
                provider.writeObject(regPath, regBytes)

                // 2. Upload snapshot to devices/{deviceId}/snapshots/snapshot.json
                val snapObj = JSONObject()
                snapObj.put("deviceId", deviceId)
                snapObj.put("sequence", nextSeq)
                snapObj.put("courses", JSONArray(org.classflow.data.local.CourseDataManager.serializeCourses(localCourses)))
                val schedsJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeSchedules(localSchedules))
                snapObj.put("schedules", schedsJson)
                snapObj.put("calendarEvents", schedsJson)
                snapObj.put("exceptions", JSONArray(org.classflow.data.local.CourseDataManager.serializeExceptions(localExceptions)))
                snapObj.put("semesters", JSONArray(org.classflow.data.local.CourseDataManager.serializeSemesters(localSemesters)))
                val focusRecordsJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeFocusRecords(localFocusRecords))
                snapObj.put("focusRecords", focusRecordsJson)
                snapObj.put("pomodoroRecords", focusRecordsJson)
                val studyTasksJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeStudyTasks(localStudyTasks))
                snapObj.put("studyTasks", studyTasksJson)
                snapObj.put("pomodoroTasks", studyTasksJson)
                val gcedTombstones = org.classflow.data.local.CourseDataManager.garbageCollectTombstones(localTombstones)
                snapObj.put("tombstones", JSONArray(org.classflow.data.local.CourseDataManager.serializeTombstones(gcedTombstones)))
                val snapBytes = snapObj.toString(2).toByteArray(Charsets.UTF_8)
                val snapPath = "devices/$deviceId/snapshots/snapshot.json"
                val writeSnapRes = provider.writeObject(snapPath, snapBytes)
                if (writeSnapRes.isFailure) {
                    return@withContext Result.failure(writeSnapRes.exceptionOrNull() ?: Exception("快照写入失败"))
                }

                // 3. Upload append-only revision log to devices/{deviceId}/revisions/
                val revObj = JSONObject()
                revObj.put("opId", UUID.randomUUID().toString())
                revObj.put("deviceId", deviceId)
                revObj.put("sequence", nextSeq)
                revObj.put("timestamp", isoDateFormatter.format(Date()))
                revObj.put("entityType", "snapshot")
                revObj.put("entityId", UUID.randomUUID().toString())
                revObj.put("action", "SYNC")
                val revBytes = revObj.toString(2).toByteArray(Charsets.UTF_8)
                provider.writeObject("devices/$deviceId/revisions/$revFileName", revBytes)

                // 4. Upload manifest to devices/{deviceId}/device_manifest.json
                val manifestObj = JSONObject()
                manifestObj.put("schemaVersion", 1)
                manifestObj.put("deviceId", deviceId)
                manifestObj.put("deviceName", "Android Device (${android.os.Build.MODEL ?: "Phone"})")
                manifestObj.put("clientVersion", "1.0.0")
                manifestObj.put("lastUpdated", isoDateFormatter.format(Date()))
                manifestObj.put("latestSequence", nextSeq)
                manifestObj.put("latestRevisionFile", "revisions/$revFileName")
                manifestObj.put("snapshotFile", "snapshots/snapshot.json")
                val manifestBytes = manifestObj.toString(2).toByteArray(Charsets.UTF_8)
                provider.writeObject("devices/$deviceId/device_manifest.json", manifestBytes)

                // 5. Ingest peer revisions/snapshots
                val peerKeys = devicesMap.keys()
                while (peerKeys.hasNext()) {
                    val peerId = peerKeys.next()
                    if (peerId == deviceId) continue
                    peersFound++

                    val peerSnapPath = "devices/$peerId/snapshots/snapshot.json"
                    val peerSnapRead = provider.readObject(peerSnapPath)
                    if (peerSnapRead.isSuccess) {
                        try {
                            val peerSnapJson = JSONObject(String(peerSnapRead.getOrThrow(), Charsets.UTF_8))
                            downloadedCount += mergePeerSnapshot(
                                peerSnapJson = peerSnapJson,
                                localCourses = localCourses,
                                localSchedules = localSchedules,
                                localExceptions = localExceptions,
                                localSemesters = localSemesters,
                                localFocusRecords = localFocusRecords,
                                localStudyTasks = localStudyTasks,
                                localTombstones = localTombstones
                            )
                        } catch (_: Exception) {}
                    }
                }

                // If downloaded new peer items, re-publish self snapshot immediately so cloud snapshot reflects the merged truth
                if (downloadedCount > 0) {
                    val updatedSnap = JSONObject()
                    updatedSnap.put("deviceId", deviceId)
                    updatedSnap.put("sequence", nextSeq + 1)
                    updatedSnap.put("courses", JSONArray(org.classflow.data.local.CourseDataManager.serializeCourses(localCourses)))
                    val schedsJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeSchedules(localSchedules))
                    updatedSnap.put("schedules", schedsJson)
                    updatedSnap.put("calendarEvents", schedsJson)
                    updatedSnap.put("exceptions", JSONArray(org.classflow.data.local.CourseDataManager.serializeExceptions(localExceptions)))
                    updatedSnap.put("semesters", JSONArray(org.classflow.data.local.CourseDataManager.serializeSemesters(localSemesters)))
                    val focusRecordsJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeFocusRecords(localFocusRecords))
                    updatedSnap.put("focusRecords", focusRecordsJson)
                    updatedSnap.put("pomodoroRecords", focusRecordsJson)
                    val studyTasksJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeStudyTasks(localStudyTasks))
                    updatedSnap.put("studyTasks", studyTasksJson)
                    updatedSnap.put("pomodoroTasks", studyTasksJson)
                    val gcedTombstones = org.classflow.data.local.CourseDataManager.garbageCollectTombstones(localTombstones)
                    updatedSnap.put("tombstones", JSONArray(org.classflow.data.local.CourseDataManager.serializeTombstones(gcedTombstones)))
                    provider.writeObject(snapPath, updatedSnap.toString(2).toByteArray(Charsets.UTF_8))
                }
            } else if (creds.providerType == "webdav") {
                if (creds.webdavUrl.isBlank() || creds.webdavUsername.isBlank() || creds.webdavPassword.isBlank()) {
                    return@withContext Result.failure(Exception("WebDAV 地址、账号或密码不能为空"))
                }
                val provider = WebDAVStorageProvider(
                    serverUrl = creds.webdavUrl,
                    username = creds.webdavUsername,
                    appPassword = creds.webdavPassword,
                    rootDirectory = creds.webdavRootDir
                )

                // 1. Register self
                val regPath = "global/device_registry.json"
                val regRead = provider.downloadFile(regPath)
                val regObj = if (regRead.isSuccess) {
                    try { JSONObject(String(regRead.getOrThrow(), Charsets.UTF_8)) } catch (_: Exception) { JSONObject() }
                } else JSONObject()

                val devicesMap = regObj.optJSONObject("devices") ?: JSONObject()
                devicesMap.put(deviceId, "Android Device (${android.os.Build.MODEL ?: "Phone"})")
                regObj.put("devices", devicesMap)
                val regBytes = regObj.toString(2).toByteArray(Charsets.UTF_8)
                provider.uploadFile(regPath, regBytes)

                // 2. Snapshot
                val snapObj = JSONObject()
                snapObj.put("deviceId", deviceId)
                snapObj.put("sequence", nextSeq)
                snapObj.put("courses", JSONArray(org.classflow.data.local.CourseDataManager.serializeCourses(localCourses)))
                val schedsJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeSchedules(localSchedules))
                snapObj.put("schedules", schedsJson)
                snapObj.put("calendarEvents", schedsJson)
                snapObj.put("exceptions", JSONArray(org.classflow.data.local.CourseDataManager.serializeExceptions(localExceptions)))
                snapObj.put("semesters", JSONArray(org.classflow.data.local.CourseDataManager.serializeSemesters(localSemesters)))
                val focusRecordsJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeFocusRecords(localFocusRecords))
                snapObj.put("focusRecords", focusRecordsJson)
                snapObj.put("pomodoroRecords", focusRecordsJson)
                val studyTasksJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeStudyTasks(localStudyTasks))
                snapObj.put("studyTasks", studyTasksJson)
                snapObj.put("pomodoroTasks", studyTasksJson)
                val gcedTombstones = org.classflow.data.local.CourseDataManager.garbageCollectTombstones(localTombstones)
                snapObj.put("tombstones", JSONArray(org.classflow.data.local.CourseDataManager.serializeTombstones(gcedTombstones)))
                val snapBytes = snapObj.toString(2).toByteArray(Charsets.UTF_8)
                val snapPath = "devices/$deviceId/snapshots/snapshot.json"
                val writeSnapRes = provider.uploadFile(snapPath, snapBytes)
                if (writeSnapRes.isFailure) {
                    return@withContext Result.failure(writeSnapRes.exceptionOrNull() ?: Exception("快照写入失败"))
                }

                // 3. Upload revision log
                val revObj = JSONObject()
                revObj.put("opId", UUID.randomUUID().toString())
                revObj.put("deviceId", deviceId)
                revObj.put("sequence", nextSeq)
                revObj.put("timestamp", isoDateFormatter.format(Date()))
                revObj.put("entityType", "snapshot")
                revObj.put("entityId", UUID.randomUUID().toString())
                revObj.put("action", "SYNC")
                val revBytes = revObj.toString(2).toByteArray(Charsets.UTF_8)
                provider.uploadFile("devices/$deviceId/revisions/$revFileName", revBytes)

                // 4. Manifest
                val manifestObj = JSONObject()
                manifestObj.put("schemaVersion", 1)
                manifestObj.put("deviceId", deviceId)
                manifestObj.put("deviceName", "Android Device (${android.os.Build.MODEL ?: "Phone"})")
                manifestObj.put("clientVersion", "1.0.0")
                manifestObj.put("lastUpdated", isoDateFormatter.format(Date()))
                manifestObj.put("latestSequence", nextSeq)
                manifestObj.put("latestRevisionFile", "revisions/$revFileName")
                manifestObj.put("snapshotFile", "snapshots/snapshot.json")
                val manifestBytes = manifestObj.toString(2).toByteArray(Charsets.UTF_8)
                provider.uploadFile("devices/$deviceId/device_manifest.json", manifestBytes)

                // 5. Ingest peers
                val peerKeys = devicesMap.keys()
                while (peerKeys.hasNext()) {
                    val peerId = peerKeys.next()
                    if (peerId == deviceId) continue
                    peersFound++

                    val peerSnapPath = "devices/$peerId/snapshots/snapshot.json"
                    val peerSnapRead = provider.downloadFile(peerSnapPath)
                    if (peerSnapRead.isSuccess) {
                        try {
                            val peerSnapJson = JSONObject(String(peerSnapRead.getOrThrow(), Charsets.UTF_8))
                            downloadedCount += mergePeerSnapshot(
                                peerSnapJson = peerSnapJson,
                                localCourses = localCourses,
                                localSchedules = localSchedules,
                                localExceptions = localExceptions,
                                localSemesters = localSemesters,
                                localFocusRecords = localFocusRecords,
                                localStudyTasks = localStudyTasks,
                                localTombstones = localTombstones
                            )
                        } catch (_: Exception) {}
                    }
                }

                // If downloaded new peer items, re-publish self snapshot immediately so cloud snapshot reflects the merged truth
                if (downloadedCount > 0) {
                    val updatedSnap = JSONObject()
                    updatedSnap.put("deviceId", deviceId)
                    updatedSnap.put("sequence", nextSeq + 1)
                    updatedSnap.put("courses", JSONArray(org.classflow.data.local.CourseDataManager.serializeCourses(localCourses)))
                    val schedsJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeSchedules(localSchedules))
                    updatedSnap.put("schedules", schedsJson)
                    updatedSnap.put("calendarEvents", schedsJson)
                    updatedSnap.put("exceptions", JSONArray(org.classflow.data.local.CourseDataManager.serializeExceptions(localExceptions)))
                    updatedSnap.put("semesters", JSONArray(org.classflow.data.local.CourseDataManager.serializeSemesters(localSemesters)))
                    val focusRecordsJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeFocusRecords(localFocusRecords))
                    updatedSnap.put("focusRecords", focusRecordsJson)
                    updatedSnap.put("pomodoroRecords", focusRecordsJson)
                    val studyTasksJson = JSONArray(org.classflow.data.local.CourseDataManager.serializeStudyTasks(localStudyTasks))
                    updatedSnap.put("studyTasks", studyTasksJson)
                    updatedSnap.put("pomodoroTasks", studyTasksJson)
                    val gcedTombstones = org.classflow.data.local.CourseDataManager.garbageCollectTombstones(localTombstones)
                    updatedSnap.put("tombstones", JSONArray(org.classflow.data.local.CourseDataManager.serializeTombstones(gcedTombstones)))
                    provider.uploadFile(snapPath, updatedSnap.toString(2).toByteArray(Charsets.UTF_8))
                }
            }

            // Save local merged state
            org.classflow.data.local.CourseDataManager.saveCourses(context, localCourses)
            org.classflow.data.local.CourseDataManager.saveSchedules(context, localSchedules)
            org.classflow.data.local.CourseDataManager.saveExceptions(context, localExceptions)
            org.classflow.data.local.CourseDataManager.saveSemesters(context, localSemesters)
            val activeSem = localSemesters.find { it.isCurrent } ?: localSemesters.firstOrNull()
            if (activeSem != null && activeSem.sections.isNotEmpty()) {
                org.classflow.data.local.CourseDataManager.saveSectionTimes(context, activeSem.sections)
            }
            org.classflow.data.local.CourseDataManager.saveFocusRecords(context, localFocusRecords)
            org.classflow.data.local.CourseDataManager.saveStudyTasks(context, localStudyTasks)
            org.classflow.data.local.CourseDataManager.saveTombstones(context, localTombstones)
            org.classflow.data.local.SettingsManager.setSyncSequence(context, nextSeq)

            lastSyncTimestamp = System.currentTimeMillis()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastSyncTimestamp))
            lastSyncStatus = "已同步 ($timeStr)"

            val summary = SyncResultSummary(
                uploadedCount = 1,
                downloadedCount = downloadedCount,
                mergedCoursesCount = localCourses.size,
                peerDevicesCount = peersFound,
                activeCoursesCount = localCourses.size
            )
            Result.success(summary)
        } catch (e: Exception) {
            lastSyncStatus = "同步失败: ${e.message}"
            Result.failure(e)
        }
    }

    /**
     * Forces full download of cloud/peer snapshot, clearing all local tombstones and overwriting local state.
     * Guarantees recovery from local deletions or tombstone conflicts.
     */
    suspend fun executeForcePullFromCloud(context: Context): Result<SyncResultSummary> = withContext(Dispatchers.IO) {
        try {
            val creds = org.classflow.data.local.SettingsManager.loadSyncCredentials(context)
            if (creds.providerType == "none") {
                return@withContext Result.failure(Exception("请先在设置中配置云存储服务 (S3 / WebDAV)"))
            }
            val deviceId = org.classflow.data.local.SettingsManager.getOrCreateSyncDeviceId(context)

            // 1. Purge all local tombstones so nothing is suppressed
            org.classflow.data.local.CourseDataManager.saveTombstones(context, emptyList())

            var bestSnapJson: JSONObject? = null
            var bestCourseCount = 0

            if (creds.providerType == "s3") {
                val provider = S3StorageProvider(
                    endpoint = creds.s3Endpoint,
                    region = creds.s3Region,
                    bucket = creds.s3Bucket,
                    accessKey = creds.s3AccessKey,
                    secretKey = creds.s3SecretKey,
                    pathPrefix = creds.s3PathPrefix
                )
                val regRead = provider.readObject("global/device_registry.json")
                if (regRead.isFailure) {
                    return@withContext Result.failure(Exception("无法读取云端设备注册表，云端可能为空"))
                }
                val regObj = JSONObject(String(regRead.getOrThrow(), Charsets.UTF_8))
                val devicesMap = regObj.optJSONObject("devices") ?: JSONObject()

                val keys = devicesMap.keys()
                while (keys.hasNext()) {
                    val peerId = keys.next()
                    if (peerId == deviceId) continue
                    val peerSnapRead = provider.readObject("devices/$peerId/snapshots/snapshot.json")
                    if (peerSnapRead.isSuccess) {
                        try {
                            val snap = JSONObject(String(peerSnapRead.getOrThrow(), Charsets.UTF_8))
                            val courses = snap.optJSONArray("courses")?.length() ?: 0
                            if (courses > bestCourseCount || bestSnapJson == null) {
                                bestSnapJson = snap
                                bestCourseCount = courses
                            }
                        } catch (_: Exception) {}
                    }
                }
            } else if (creds.providerType == "webdav") {
                val provider = WebDAVStorageProvider(
                    serverUrl = creds.webdavUrl,
                    username = creds.webdavUsername,
                    appPassword = creds.webdavPassword,
                    rootDirectory = creds.webdavRootDir
                )
                val regRead = provider.downloadFile("global/device_registry.json")
                if (regRead.isFailure) {
                    return@withContext Result.failure(Exception("无法读取云端设备注册表，云端可能为空"))
                }
                val regObj = JSONObject(String(regRead.getOrThrow(), Charsets.UTF_8))
                val devicesMap = regObj.optJSONObject("devices") ?: JSONObject()

                val keys = devicesMap.keys()
                while (keys.hasNext()) {
                    val peerId = keys.next()
                    if (peerId == deviceId) continue
                    val peerSnapRead = provider.downloadFile("devices/$peerId/snapshots/snapshot.json")
                    if (peerSnapRead.isSuccess) {
                        try {
                            val snap = JSONObject(String(peerSnapRead.getOrThrow(), Charsets.UTF_8))
                            val courses = snap.optJSONArray("courses")?.length() ?: 0
                            if (courses > bestCourseCount || bestSnapJson == null) {
                                bestSnapJson = snap
                                bestCourseCount = courses
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            val targetSnap = bestSnapJson ?: return@withContext Result.failure(Exception("未在云端找到其他设备的有效快照"))

            val peerCoursesArr = targetSnap.optJSONArray("courses")
            val courses = if (peerCoursesArr != null) org.classflow.data.local.CourseDataManager.deserializeCourses(peerCoursesArr.toString()) else emptyList()
            val peerSchedsArr = targetSnap.optJSONArray("calendarEvents") ?: targetSnap.optJSONArray("schedules")
            val scheds = if (peerSchedsArr != null) org.classflow.data.local.CourseDataManager.deserializeSchedules(peerSchedsArr.toString()) else emptyList()
            val peerExcArr = targetSnap.optJSONArray("exceptions")
            val exceptions = if (peerExcArr != null) org.classflow.data.local.CourseDataManager.deserializeExceptions(peerExcArr.toString()) else emptyList()
            val peerSemArr = targetSnap.optJSONArray("semesters")
            val semesters = if (peerSemArr != null) org.classflow.data.local.CourseDataManager.deserializeSemesters(peerSemArr.toString()) else emptyList()
            val peerFocusArr = targetSnap.optJSONArray("pomodoroRecords") ?: targetSnap.optJSONArray("focusRecords")
            val focusRecords = if (peerFocusArr != null) org.classflow.data.local.CourseDataManager.deserializeFocusRecords(peerFocusArr.toString()) else emptyList()
            val peerTaskArr = targetSnap.optJSONArray("pomodoroTasks") ?: targetSnap.optJSONArray("studyTasks")
            val studyTasks = if (peerTaskArr != null) org.classflow.data.local.CourseDataManager.deserializeStudyTasks(peerTaskArr.toString()) else emptyList()

            org.classflow.data.local.CourseDataManager.saveCourses(context, courses)
            org.classflow.data.local.CourseDataManager.saveSchedules(context, scheds)
            org.classflow.data.local.CourseDataManager.saveExceptions(context, exceptions)
            if (semesters.isNotEmpty()) {
                org.classflow.data.local.CourseDataManager.saveSemesters(context, semesters)
                val activeSem = semesters.find { it.isCurrent } ?: semesters.firstOrNull()
                if (activeSem != null && activeSem.sections.isNotEmpty()) {
                    org.classflow.data.local.CourseDataManager.saveSectionTimes(context, activeSem.sections)
                }
            }
            org.classflow.data.local.CourseDataManager.saveFocusRecords(context, focusRecords)
            org.classflow.data.local.CourseDataManager.saveStudyTasks(context, studyTasks)
            org.classflow.data.local.CourseDataManager.saveTombstones(context, emptyList())

            executeFullSync(context)

            lastSyncTimestamp = System.currentTimeMillis()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastSyncTimestamp))
            lastSyncStatus = "已全量恢复 (${courses.size}门课程, $timeStr)"

            Result.success(SyncResultSummary(uploadedCount = 1, downloadedCount = courses.size, mergedCoursesCount = courses.size, peerDevicesCount = 1, activeCoursesCount = courses.size))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Tests connectivity with the configured storage provider.
     */
    suspend fun testConnection(context: Context): Result<Boolean> = withContext(Dispatchers.IO) {
        val creds = org.classflow.data.local.SettingsManager.loadSyncCredentials(context)
        when (creds.providerType) {
            "s3" -> {
                val provider = S3StorageProvider(
                    endpoint = creds.s3Endpoint,
                    region = creds.s3Region,
                    bucket = creds.s3Bucket,
                    accessKey = creds.s3AccessKey,
                    secretKey = creds.s3SecretKey,
                    pathPrefix = creds.s3PathPrefix
                )
                provider.testConnection()
            }
            "webdav" -> {
                val provider = WebDAVStorageProvider(
                    serverUrl = creds.webdavUrl,
                    username = creds.webdavUsername,
                    appPassword = creds.webdavPassword,
                    rootDirectory = creds.webdavRootDir
                )
                provider.testConnection()
            }
            else -> Result.failure(Exception("未选择存储服务提供商"))
        }
    }
}

data class SyncResultSummary(
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val mergedCoursesCount: Int = 0,
    val peerDevicesCount: Int = 0,
    val activeCoursesCount: Int = 0
)
