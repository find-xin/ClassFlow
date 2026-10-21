package org.classflow.data.local

import android.content.Context
import org.classflow.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Unified Local First Course & Schedule Storage Manager:
 * Serves as the single local source of truth for all courses, schedules, section times,
 * semesters, and focus records.
 * Persists deterministically to SharedPreferences JSON, ensuring immediate persistence
 * across process restarts, widget refreshes, and app kills.
 */
object CourseDataManager {
    private const val PREFS_NAME = "classflow_courses_prefs"
    private const val KEY_COURSES = "courses_json"
    private const val KEY_SCHEDULES = "schedules_json"
    private const val KEY_SECTIONS = "sections_json"
    private const val KEY_SEMESTERS = "semesters_json"
    private const val KEY_FOCUS_RECORDS = "focus_records_json"
    private const val KEY_STUDY_TASKS = "study_tasks_json"
    private const val KEY_DAILY_MINUTES = "daily_minutes_json"
    private const val KEY_TOP_COURSES = "top_courses_json"
    private const val KEY_EXCEPTIONS = "exceptions_json"
    private const val KEY_TOMBSTONES = "tombstones_json"

    // MARK: - Courses Persistence & Serialization

    fun deserializeCourses(jsonStr: String): List<Course> {
        return try {
            val courses = mutableListOf<Course>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val cObj = arr.getJSONObject(i)
                val schedList = mutableListOf<CourseSchedule>()
                val schedArr = cObj.optJSONArray("schedules")
                if (schedArr != null) {
                    for (j in 0 until schedArr.length()) {
                        val sObj = schedArr.getJSONObject(j)
                        val customWeeks = mutableListOf<Int>()
                        var startW = 1
                        var endW = 30
                        var wp = WeekPatternType.ALL

                        val wpRaw = sObj.opt("weekPattern")
                        if (wpRaw is JSONObject) {
                            val typeStr = wpRaw.optString("type", "all").uppercase()
                            wp = try { WeekPatternType.valueOf(typeStr) } catch (_: Exception) { WeekPatternType.ALL }
                            startW = wpRaw.optInt("startWeek", 1)
                            endW = wpRaw.optInt("endWeek", 30)
                            val cwArr = wpRaw.optJSONArray("customWeeks")
                            if (cwArr != null) {
                                for (w in 0 until cwArr.length()) {
                                    customWeeks.add(cwArr.getInt(w))
                                }
                            }
                        } else if (wpRaw is String) {
                            wp = try { WeekPatternType.valueOf(wpRaw.uppercase()) } catch (_: Exception) { WeekPatternType.ALL }
                        }

                        if (customWeeks.isEmpty()) {
                            val weeksArr = sObj.optJSONArray("customWeeks")
                            if (weeksArr != null) {
                                for (w in 0 until weeksArr.length()) {
                                    customWeeks.add(weeksArr.getInt(w))
                                }
                            }
                        }
                        val customStart = sObj.optString("customStartTime").ifEmpty { null }
                        val customEnd = sObj.optString("customEndTime").ifEmpty { null }

                        schedList.add(
                            CourseSchedule(
                                id = sObj.optString("id").ifBlank { UUID.randomUUID().toString() },
                                courseId = cObj.optString("id"),
                                dayOfWeek = sObj.optInt("dayOfWeek", 1),
                                startSection = sObj.optInt("startSection", 1),
                                endSection = sObj.optInt("endSection", 2),
                                classroom = sObj.optString("classroom").ifEmpty { null },
                                teacher = sObj.optString("teacher").ifEmpty { null },
                                weekPattern = wp,
                                customWeeks = if (customWeeks.isNotEmpty()) customWeeks else (1..16).toList(),
                                customStartTime = customStart,
                                customEndTime = customEnd,
                                startWeek = startW,
                                endWeek = endW
                            )
                        )
                    }
                }

                courses.add(
                    Course(
                        id = cObj.optString("id").ifBlank { UUID.randomUUID().toString() },
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
            courses
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun ensureValidUuid(rawId: String?): String {
        if (rawId.isNullOrBlank()) return UUID.randomUUID().toString()
        return try {
            UUID.fromString(rawId).toString()
        } catch (_: Exception) {
            UUID.nameUUIDFromBytes(rawId.toByteArray(Charsets.UTF_8)).toString()
        }
    }

    fun serializeCourses(courses: List<Course>): String {
        val arr = JSONArray()
        for (c in courses) {
            val cObj = JSONObject()
            cObj.put("id", c.id)
            cObj.put("semesterId", c.semesterId)
            cObj.put("name", c.name)
            cObj.put("teacher", c.teacher ?: "")
            if (c.credit != null) cObj.put("credit", c.credit)
            cObj.put("color", c.color)
            cObj.put("notes", c.notes ?: "")

            val schedArr = JSONArray()
            for (s in c.schedules) {
                val sObj = JSONObject()
                sObj.put("id", s.id)
                sObj.put("dayOfWeek", s.dayOfWeek)
                sObj.put("startSection", s.startSection)
                sObj.put("endSection", s.endSection)
                sObj.put("classroom", s.classroom ?: "")
                sObj.put("teacher", s.teacher ?: "")

                // Schema compliant weekPattern object
                val wpObj = JSONObject()
                wpObj.put("type", s.weekPattern.name.lowercase())
                wpObj.put("startWeek", s.startWeek)
                wpObj.put("endWeek", s.endWeek)
                val weeksArr = JSONArray()
                s.customWeeks.forEach { weeksArr.put(it) }
                wpObj.put("customWeeks", weeksArr)
                sObj.put("weekPattern", wpObj)

                // Top-level customWeeks for legacy backwards compatibility
                sObj.put("customWeeks", weeksArr)

                if (s.customStartTime != null) sObj.put("customStartTime", s.customStartTime)
                if (s.customEndTime != null) sObj.put("customEndTime", s.customEndTime)
                schedArr.put(sObj)
            }
            cObj.put("schedules", schedArr)
            arr.put(cObj)
        }
        return arr.toString()
    }

    fun consolidateSchedules(schedules: List<CourseSchedule>): List<CourseSchedule> {
        if (schedules.size <= 1) return schedules
        val groupedByDay = schedules.groupBy { it.dayOfWeek }
        val result = mutableListOf<CourseSchedule>()

        for ((_, dayScheds) in groupedByDay) {
            val pending = dayScheds.sortedWith(compareBy({ it.startSection }, { it.endSection })).toMutableList()
            var i = 0
            while (i < pending.size) {
                var current = pending[i]
                var mergedAny = false
                var j = i + 1
                while (j < pending.size) {
                    val candidate = pending[j]
                    val overlaps = maxOf(current.startSection, candidate.startSection) <= minOf(current.endSection, candidate.endSection)
                    if (overlaps) {
                        val mergedWeeks = (current.customWeeks + candidate.customWeeks).distinct().sorted()
                        current = current.copy(
                            startSection = minOf(current.startSection, candidate.startSection),
                            endSection = maxOf(current.endSection, candidate.endSection),
                            classroom = if (current.classroom.isNullOrBlank()) candidate.classroom else current.classroom,
                            teacher = if (current.teacher.isNullOrBlank()) candidate.teacher else current.teacher,
                            customWeeks = mergedWeeks
                        )
                        pending.removeAt(j)
                        pending[i] = current
                        mergedAny = true
                    } else {
                        j++
                    }
                }
                if (!mergedAny) {
                    result.add(current)
                    i++
                }
            }
        }
        return result.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
    }

    fun consolidateCourses(courses: List<Course>): List<Course> {
        if (courses.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<String, MutableList<Course>>()
        for (c in courses) {
            val key = "${c.semesterId}___${c.name.trim().lowercase()}"
            grouped.getOrPut(key) { mutableListOf() }.add(c)
        }

        val result = mutableListOf<Course>()
        for ((_, group) in grouped) {
            if (group.size == 1) {
                val c = group.first()
                result.add(c.copy(schedules = consolidateSchedules(c.schedules)))
            } else {
                val first = group.first()
                val allSchedules = group.flatMap { it.schedules }
                val mergedTeacher = group.firstOrNull { !it.teacher.isNullOrBlank() }?.teacher ?: first.teacher
                val mergedCredit = group.firstOrNull { it.credit != null }?.credit ?: first.credit
                val mergedNotes = group.mapNotNull { it.notes?.ifBlank { null } }.distinct().joinToString("; ").ifBlank { first.notes }
                val mergedColor = group.firstOrNull { it.color.isNotBlank() }?.color ?: first.color
                result.add(
                    first.copy(
                        teacher = mergedTeacher,
                        credit = mergedCredit,
                        notes = mergedNotes,
                        color = mergedColor,
                        schedules = consolidateSchedules(allSchedules)
                    )
                )
            }
        }
        return result
    }

    fun loadCourses(context: Context): List<Course> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_COURSES)) {
            val defaults = defaultCourses()
            val tombstones = loadTombstones(context).map { it.entityId }.toSet()
            val filtered = defaults.filterNot { it.id in tombstones }
            saveCourses(context, filtered)
            return filtered
        }
        val jsonStr = prefs.getString(KEY_COURSES, "[]") ?: "[]"
        val courses = deserializeCourses(jsonStr)
        val consolidated = consolidateCourses(courses)
        if (consolidated != courses) {
            saveCourses(context, consolidated)
        }
        val tombstones = loadTombstones(context).map { it.entityId }.toSet()
        return if (tombstones.isEmpty()) consolidated else consolidated.filterNot { it.id in tombstones }
    }

    fun saveCourses(context: Context, courses: List<Course>) {
        val consolidated = consolidateCourses(courses)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COURSES, serializeCourses(consolidated)).apply()
    }

    // MARK: - Schedules (Calendar Events) Persistence & Serialization

    fun deserializeSchedules(jsonStr: String): List<CalendarEvent> {
        return try {
            val list = mutableListOf<CalendarEvent>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val daysList = mutableListOf<Int>()
                val daysArr = obj.optJSONArray("customDaysOfWeek")
                if (daysArr != null) {
                    for (d in 0 until daysArr.length()) daysList.add(daysArr.getInt(d))
                }
                val weeksList = mutableListOf<Int>()
                val weeksArr = obj.optJSONArray("customWeeks")
                if (weeksArr != null) {
                    for (w in 0 until weeksArr.length()) weeksList.add(weeksArr.getInt(w))
                }

                list.add(
                    CalendarEvent(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        calendarId = obj.optString("calendarId", UUID.randomUUID().toString()),
                        semesterId = obj.optString("semesterId").ifEmpty { null },
                        title = obj.optString("title", "未命名日程"),
                        startTime = obj.optString("startTime", "08:00"),
                        endTime = obj.optString("endTime", "09:00"),
                        isAllDay = obj.optBoolean("isAllDay", false),
                        location = obj.optString("location").ifEmpty { null },
                        description = obj.optString("description").ifEmpty { null },
                        color = obj.optString("color", "#039BE5"),
                        frequency = obj.optString("frequency", "每天"),
                        startSection = if (obj.has("startSection") && !obj.isNull("startSection")) obj.getInt("startSection") else null,
                        endSection = if (obj.has("endSection") && !obj.isNull("endSection")) obj.getInt("endSection") else null,
                        customDaysOfWeek = daysList,
                        customWeeks = weeksList,
                        endRepeatType = obj.optString("endRepeatType", "never"),
                        endRepeatUntil = obj.optString("endRepeatUntil").ifEmpty { null },
                        endRepeatCount = if (obj.has("endRepeatCount") && !obj.isNull("endRepeatCount")) obj.getInt("endRepeatCount") else null,
                        startDate = obj.optString("startDate").ifEmpty { null },
                        endDate = obj.optString("endDate").ifEmpty { null }
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serializeSchedules(schedules: List<CalendarEvent>): String {
        val arr = JSONArray()
        for (s in schedules) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("calendarId", s.calendarId)
            if (s.semesterId != null) obj.put("semesterId", s.semesterId)
            obj.put("title", s.title)
            obj.put("startTime", s.startTime)
            obj.put("endTime", s.endTime)
            obj.put("isAllDay", s.isAllDay)
            obj.put("location", s.location ?: "")
            obj.put("description", s.description ?: "")
            obj.put("color", s.color)
            obj.put("frequency", s.frequency)
            if (s.startSection != null) obj.put("startSection", s.startSection)
            if (s.endSection != null) obj.put("endSection", s.endSection)
            val daysArr = JSONArray()
            s.customDaysOfWeek.forEach { daysArr.put(it) }
            obj.put("customDaysOfWeek", daysArr)
            val weeksArr = JSONArray()
            s.customWeeks.forEach { weeksArr.put(it) }
            obj.put("customWeeks", weeksArr)
            obj.put("endRepeatType", s.endRepeatType)
            if (s.endRepeatUntil != null) obj.put("endRepeatUntil", s.endRepeatUntil)
            if (s.endRepeatCount != null) obj.put("endRepeatCount", s.endRepeatCount)
            if (s.startDate != null) obj.put("startDate", s.startDate)
            if (s.endDate != null) obj.put("endDate", s.endDate)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun consolidateCalendarEvents(events: List<CalendarEvent>): List<CalendarEvent> {
        if (events.isEmpty()) return emptyList()
        if (events.size == 1) return events.toList()
        val grouped = LinkedHashMap<String, MutableList<CalendarEvent>>()
        for (e in events) {
            val key = "${e.semesterId}___${e.title.trim().lowercase()}___${e.frequency}___${e.startTime}"
            grouped.getOrPut(key) { mutableListOf() }.add(e)
        }
        val result = mutableListOf<CalendarEvent>()
        for ((_, group) in grouped) {
            if (group.size == 1) {
                result.add(group.first())
            } else {
                val first = group.first()
                val mergedDays = group.flatMap { it.customDaysOfWeek }.distinct().sorted()
                val mergedWeeks = group.flatMap { it.customWeeks }.distinct().sorted()
                result.add(
                    first.copy(
                        customDaysOfWeek = mergedDays,
                        customWeeks = mergedWeeks,
                        location = group.firstOrNull { !it.location.isNullOrBlank() }?.location ?: first.location,
                        description = group.firstOrNull { !it.description.isNullOrBlank() }?.description ?: first.description
                    )
                )
            }
        }
        return result
    }

    fun loadSchedules(context: Context): List<CalendarEvent> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SCHEDULES)) {
            val defaults = defaultSchedules()
            val tombstones = loadTombstones(context).map { it.entityId }.toSet()
            val filtered = defaults.filterNot { it.id in tombstones }
            saveSchedules(context, filtered)
            return filtered
        }
        val jsonStr = prefs.getString(KEY_SCHEDULES, "[]") ?: "[]"
        val schedules = deserializeSchedules(jsonStr)
        val consolidated = consolidateCalendarEvents(schedules)
        if (consolidated != schedules) {
            saveSchedules(context, consolidated)
        }
        val tombstones = loadTombstones(context).map { it.entityId }.toSet()
        return if (tombstones.isEmpty()) consolidated else consolidated.filterNot { it.id in tombstones }
    }

    fun saveSchedules(context: Context, schedules: List<CalendarEvent>) {
        val consolidated = consolidateCalendarEvents(schedules)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SCHEDULES, serializeSchedules(consolidated)).apply()
    }

    // MARK: - Section Times Persistence & Serialization

    fun deserializeSectionTimes(jsonStr: String): List<SectionTime> {
        return try {
            val list = mutableListOf<SectionTime>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SectionTime(
                        section = obj.getInt("section"),
                        startTime = obj.getString("startTime"),
                        endTime = obj.getString("endTime")
                    )
                )
            }
            if (list.isEmpty()) defaultSectionTimes() else list
        } catch (_: Exception) {
            defaultSectionTimes()
        }
    }

    fun serializeSectionTimes(sections: List<SectionTime>): String {
        val arr = JSONArray()
        for (sec in sections) {
            val obj = JSONObject()
            obj.put("section", sec.section)
            obj.put("startTime", sec.startTime)
            obj.put("endTime", sec.endTime)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun loadSectionTimes(context: Context): List<SectionTime> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SECTIONS, null)
        if (jsonStr.isNullOrBlank()) {
            val defaults = defaultSectionTimes()
            saveSectionTimes(context, defaults)
            return defaults
        }
        return deserializeSectionTimes(jsonStr)
    }

    fun saveSectionTimes(context: Context, sections: List<SectionTime>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SECTIONS, serializeSectionTimes(sections)).apply()
    }

    // MARK: - Semesters Persistence & Serialization

    fun deserializeSemesters(jsonStr: String): List<Semester> {
        return try {
            val list = mutableListOf<Semester>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val rawId = obj.optString("id", "")
                val validId = if (rawId.isBlank()) UUID.randomUUID().toString() else rawId
                val secList = mutableListOf<SectionTime>()
                val secArr = obj.optJSONArray("sections")
                if (secArr != null) {
                    for (k in 0 until secArr.length()) {
                        val secObj = secArr.getJSONObject(k)
                        secList.add(
                            SectionTime(
                                section = secObj.optInt("section", k + 1),
                                startTime = secObj.optString("startTime", "08:00"),
                                endTime = secObj.optString("endTime", "08:45")
                            )
                        )
                    }
                }
                list.add(
                    Semester(
                        id = validId,
                        name = obj.optString("name", "新学期"),
                        startDate = obj.optString("startDate", "2026-09-01"),
                        endDate = obj.optString("endDate", "2027-01-20"),
                        totalWeeks = obj.optInt("totalWeeks", 16),
                        isCurrent = obj.optBoolean("isCurrent", false),
                        timeZone = obj.optString("timeZone", "Asia/Shanghai"),
                        sections = secList
                    )
                )
            }
            if (list.isEmpty()) defaultSemesters() else list
        } catch (_: Exception) {
            defaultSemesters()
        }
    }

    fun serializeSemesters(semesters: List<Semester>): String {
        val arr = JSONArray()
        for (s in semesters) {
            val obj = JSONObject()
            obj.put("id", s.id.ifBlank { UUID.randomUUID().toString() })
            obj.put("name", s.name)
            obj.put("startDate", s.startDate)
            obj.put("endDate", s.endDate)
            obj.put("totalWeeks", s.totalWeeks)
            obj.put("isCurrent", s.isCurrent)
            obj.put("timeZone", s.timeZone)
            val secArr = JSONArray()
            s.sections.forEach { sec ->
                val secObj = JSONObject()
                secObj.put("section", sec.section)
                secObj.put("startTime", sec.startTime)
                secObj.put("endTime", sec.endTime)
                secArr.put(secObj)
            }
            obj.put("sections", secArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun loadSemesters(context: Context): List<Semester> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SEMESTERS, null)
        if (jsonStr.isNullOrBlank()) {
            val defaults = defaultSemesters()
            saveSemesters(context, defaults)
            return defaults
        }
        return deserializeSemesters(jsonStr)
    }

    fun saveSemesters(context: Context, semesters: List<Semester>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SEMESTERS, serializeSemesters(semesters)).apply()
    }

    // MARK: - Top Course Overlap Selections

    fun loadTopCourseSelections(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TOP_COURSES, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getString(key)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveTopCourseSelections(context: Context, selections: Map<String, String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject()
        selections.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_TOP_COURSES, obj.toString()).apply()
    }

    // MARK: - Focus Records Persistence & Serialization

    fun deserializeFocusRecords(jsonStr: String): List<FocusRecord> {
        return try {
            val list = mutableListOf<FocusRecord>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FocusRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        taskTitle = obj.optString("taskTitle", "自由专注"),
                        courseName = obj.optString("courseName").ifEmpty { null },
                        durationMinutes = obj.optInt("durationMinutes", 25),
                        completedAt = obj.optString("completedAt", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            defaultFocusRecords()
        }
    }

    fun serializeFocusRecords(records: List<FocusRecord>): String {
        val arr = JSONArray()
        for (r in records) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("taskTitle", r.taskTitle)
            obj.put("courseName", r.courseName ?: "")
            obj.put("durationMinutes", r.durationMinutes)
            obj.put("completedAt", r.completedAt)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun loadFocusRecords(context: Context): List<FocusRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_FOCUS_RECORDS, null)
        if (jsonStr.isNullOrBlank()) {
            val defaults = defaultFocusRecords()
            saveFocusRecords(context, defaults)
            return defaults
        }
        return deserializeFocusRecords(jsonStr)
    }

    fun saveFocusRecords(context: Context, records: List<FocusRecord>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FOCUS_RECORDS, serializeFocusRecords(records)).apply()
    }

    // MARK: - Study Tasks Persistence & Serialization

    fun deserializeStudyTasks(jsonStr: String): List<StudyTask> {
        return try {
            val tasks = mutableListOf<StudyTask>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val daysArr = o.optJSONArray("customDaysOfWeek")
                val customDays = mutableListOf<Int>()
                if (daysArr != null) {
                    for (j in 0 until daysArr.length()) customDays.add(daysArr.getInt(j))
                }
                val weeksArr = o.optJSONArray("customWeeks")
                val customWeeks = mutableListOf<Int>()
                if (weeksArr != null) {
                    for (j in 0 until weeksArr.length()) customWeeks.add(weeksArr.getInt(j))
                }
                tasks.add(
                    StudyTask(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        semesterId = o.optString("semesterId").ifEmpty { null },
                        title = o.optString("title", "自习任务"),
                        courseName = o.optString("courseName").ifEmpty { null },
                        targetMinutes = o.optInt("targetMinutes", 25),
                        completedMinutes = o.optInt("completedMinutes", 0),
                        isCompleted = o.optBoolean("isCompleted", false),
                        startDate = o.optString("startDate").ifEmpty { null },
                        dueDate = o.optString("dueDate").ifEmpty { null },
                        frequency = o.optString("frequency", "none"),
                        customDaysOfWeek = customDays,
                        customWeeks = customWeeks,
                        endRepeatType = o.optString("endRepeatType", "never"),
                        endRepeatUntil = o.optString("endRepeatUntil").ifEmpty { null },
                        endRepeatCount = if (o.has("endRepeatCount")) o.optInt("endRepeatCount") else null,
                        priority = o.optString("priority", "medium"),
                        notes = o.optString("notes").ifEmpty { null }
                    )
                )
            }
            tasks
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serializeStudyTasks(tasks: List<StudyTask>): String {
        val arr = JSONArray()
        for (t in tasks) {
            val o = JSONObject()
            o.put("id", t.id)
            if (t.semesterId != null) o.put("semesterId", t.semesterId)
            o.put("title", t.title)
            o.put("courseName", t.courseName ?: "")
            o.put("targetMinutes", t.targetMinutes)
            o.put("completedMinutes", t.completedMinutes)
            o.put("isCompleted", t.isCompleted)
            if (t.startDate != null) o.put("startDate", t.startDate)
            if (t.dueDate != null) o.put("dueDate", t.dueDate)
            o.put("frequency", t.frequency)
            val daysArr = JSONArray()
            t.customDaysOfWeek.forEach { daysArr.put(it) }
            o.put("customDaysOfWeek", daysArr)
            val weeksArr = JSONArray()
            t.customWeeks.forEach { weeksArr.put(it) }
            o.put("customWeeks", weeksArr)
            o.put("endRepeatType", t.endRepeatType)
            if (t.endRepeatUntil != null) o.put("endRepeatUntil", t.endRepeatUntil)
            if (t.endRepeatCount != null) o.put("endRepeatCount", t.endRepeatCount)
            o.put("priority", t.priority)
            if (t.notes != null) o.put("notes", t.notes)
            arr.put(o)
        }
        return arr.toString()
    }

    fun defaultStudyTasks(): List<StudyTask> = emptyList()

    fun loadStudyTasks(context: Context): List<StudyTask> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_STUDY_TASKS)) {
            val defaults = defaultStudyTasks()
            val tombstones = loadTombstones(context).map { it.entityId }.toSet()
            val filtered = defaults.filterNot { it.id in tombstones }
            saveStudyTasks(context, filtered)
            return filtered
        }
        val jsonStr = prefs.getString(KEY_STUDY_TASKS, "[]") ?: "[]"
        val tasks = deserializeStudyTasks(jsonStr)
        val tombstones = loadTombstones(context).map { it.entityId }.toSet()
        return if (tombstones.isEmpty()) tasks else tasks.filterNot { it.id in tombstones }
    }

    fun saveStudyTasks(context: Context, tasks: List<StudyTask>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STUDY_TASKS, serializeStudyTasks(tasks)).apply()
    }

    // MARK: - Daily Focus Minutes Persistence & Serialization

    fun deserializeDailyMinutes(jsonStr: String): Map<String, Int> {
        return try {
            val map = mutableMapOf<String, Int>()
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getInt(k)
            }
            map
        } catch (_: Exception) {
            defaultDailyMinutes()
        }
    }

    fun serializeDailyMinutes(map: Map<String, Int>): String {
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k, v)
        }
        return obj.toString()
    }

    fun loadDailyMinutes(context: Context): Map<String, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_DAILY_MINUTES, null)
        if (jsonStr.isNullOrBlank()) {
            val defaults = defaultDailyMinutes()
            saveDailyMinutes(context, defaults)
            return defaults
        }
        return deserializeDailyMinutes(jsonStr)
    }

    fun saveDailyMinutes(context: Context, map: Map<String, Int>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DAILY_MINUTES, serializeDailyMinutes(map)).apply()
    }

    // MARK: - Schedule Exceptions Persistence & Serialization

    fun deserializeExceptions(jsonStr: String): List<ScheduleException> {
        return try {
            val list = mutableListOf<ScheduleException>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val typeRaw = obj.optString("type").ifEmpty { obj.optString("exceptionType", "CANCEL") }
                val type = try {
                    ExceptionType.valueOf(typeRaw.uppercase())
                } catch (_: Exception) {
                    ExceptionType.CANCEL
                }
                list.add(
                    ScheduleException(
                        id = obj.optString("id").ifEmpty { UUID.randomUUID().toString() },
                        scheduleId = obj.optString("scheduleId"),
                        originalDate = obj.optString("originalDate"),
                        type = type,
                        targetDate = obj.optString("targetDate").ifEmpty { null },
                        targetDayOfWeek = if (obj.has("targetDayOfWeek")) obj.optInt("targetDayOfWeek") else null,
                        targetStartSection = if (obj.has("targetStartSection")) obj.optInt("targetStartSection") else null,
                        targetEndSection = if (obj.has("targetEndSection")) obj.optInt("targetEndSection") else null,
                        targetClassroom = obj.optString("targetClassroom").ifEmpty { null },
                        reason = obj.optString("reason").ifEmpty { null }
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serializeExceptions(exceptions: List<ScheduleException>): String {
        val arr = JSONArray()
        for (e in exceptions) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("scheduleId", e.scheduleId)
            obj.put("originalDate", e.originalDate)
            obj.put("type", e.type.name)
            obj.put("exceptionType", e.type.name.lowercase())
            if (e.targetDate != null) obj.put("targetDate", e.targetDate)
            if (e.targetDayOfWeek != null) obj.put("targetDayOfWeek", e.targetDayOfWeek)
            if (e.targetStartSection != null) obj.put("targetStartSection", e.targetStartSection)
            if (e.targetEndSection != null) obj.put("targetEndSection", e.targetEndSection)
            if (e.targetClassroom != null) obj.put("targetClassroom", e.targetClassroom)
            if (e.reason != null) obj.put("reason", e.reason)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun loadExceptions(context: Context): List<ScheduleException> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_EXCEPTIONS, null)
        if (jsonStr.isNullOrBlank()) return emptyList()
        return deserializeExceptions(jsonStr)
    }

    fun saveExceptions(context: Context, exceptions: List<ScheduleException>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_EXCEPTIONS, serializeExceptions(exceptions)).apply()
    }

    // MARK: - Tombstones Persistence & Serialization

    fun deserializeTombstones(jsonStr: String): List<SyncTombstone> {
        return try {
            val list = mutableListOf<SyncTombstone>()
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SyncTombstone(
                        entityId = obj.optString("entityId"),
                        entityType = obj.optString("entityType"),
                        deletedAt = obj.optLong("deletedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    const val TOMBSTONE_RETENTION_DAYS = 90
    const val MAX_TOMBSTONES_COUNT = 1000

    fun garbageCollectTombstones(tombstones: List<SyncTombstone>, referenceTime: Long = System.currentTimeMillis()): List<SyncTombstone> {
        val cutoff = referenceTime - (TOMBSTONE_RETENTION_DAYS * 86400000L)
        val active = tombstones.filter { it.deletedAt >= cutoff }
        return if (active.size > MAX_TOMBSTONES_COUNT) {
            active.sortedByDescending { it.deletedAt }.take(MAX_TOMBSTONES_COUNT)
        } else {
            active
        }
    }

    fun serializeTombstones(tombstones: List<SyncTombstone>): String {
        val arr = JSONArray()
        for (t in tombstones) {
            val obj = JSONObject()
            obj.put("entityId", t.entityId)
            obj.put("entityType", t.entityType)
            obj.put("deletedAt", t.deletedAt)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun loadTombstones(context: Context): List<SyncTombstone> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TOMBSTONES, null)
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = deserializeTombstones(jsonStr)
        val gced = garbageCollectTombstones(list)
        if (gced.size != list.size) {
            prefs.edit().putString(KEY_TOMBSTONES, serializeTombstones(gced)).apply()
        }
        return gced
    }

    fun saveTombstones(context: Context, tombstones: List<SyncTombstone>) {
        val gced = garbageCollectTombstones(tombstones)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOMBSTONES, serializeTombstones(gced)).apply()
    }

    fun addTombstone(context: Context, entityId: String, entityType: String) {
        val current = loadTombstones(context).toMutableList()
        current.removeAll { it.entityId == entityId }
        current.add(SyncTombstone(entityId = entityId, entityType = entityType, deletedAt = System.currentTimeMillis()))
        saveTombstones(context, current)
    }

    // MARK: - Default Data Providers

    fun defaultCourses(): List<Course> = emptyList()

    fun defaultSchedules(): List<CalendarEvent> = emptyList()

    fun defaultSectionTimes(): List<SectionTime> {
        return listOf(
            SectionTime(1, "08:00", "08:45"),
            SectionTime(2, "08:50", "09:35"),
            SectionTime(3, "10:00", "10:45"),
            SectionTime(4, "10:50", "11:35"),
            SectionTime(5, "13:30", "14:15"),
            SectionTime(6, "14:20", "15:05"),
            SectionTime(7, "15:25", "16:10"),
            SectionTime(8, "16:15", "17:00"),
            SectionTime(9, "18:30", "19:15"),
            SectionTime(10, "19:20", "20:05"),
            SectionTime(11, "20:15", "21:00"),
            SectionTime(12, "21:05", "21:50")
        )
    }

    fun defaultSemesters(): List<Semester> {
        val mainId = "sem_main"
        return listOf(
            Semester(id = mainId, name = "2026-2027 秋季学期", startDate = "2026-09-01", endDate = "2027-01-20", totalWeeks = 16, isCurrent = true)
        )
    }

    fun defaultFocusRecords(): List<FocusRecord> = emptyList()

    fun defaultDailyMinutes(): Map<String, Int> = emptyMap()
}
