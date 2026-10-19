package org.classflow.data.local

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.classflow.data.model.CalendarEvent
import org.classflow.data.model.Course
import org.classflow.data.model.ScheduleException
import org.classflow.data.model.SectionTime
import org.classflow.data.model.TimelineItem
import org.classflow.widgets.NextClassGlanceWidget
import org.classflow.widgets.TodayClassesGlanceWidget
import org.classflow.widgets.WeekTimetableGlanceWidget
import org.json.JSONArray
import org.json.JSONObject

data class WidgetOccurrence(
    val id: String,
    val title: String,
    val teacher: String?,
    val classroom: String?,
    val dayOfWeek: Int, // 1 (Mon) .. 7 (Sun)
    val startSection: Int,
    val endSection: Int,
    val startTime: String,
    val endTime: String,
    val colorHex: String,
    val isCurrentWeek: Boolean = true,
    val isSchedule: Boolean = false,
    val date: String = "",
    val weekNumber: Int = 1
)

data class WidgetData(
    val semesterName: String = "ClassFlow",
    val currentWeek: Int = 1,
    val isDark: Boolean = false,
    val themeMode: String = "system",
    val items: List<WidgetOccurrence> = emptyList(),
    val sectionTimes: List<Pair<String, String>> = emptyList()
)

object WidgetDataManager {
    private const val PREFS_NAME = "classflow_widget_prefs"
    private const val KEY_WIDGET_DATA = "widget_data_json"

    fun loadWidgetData(context: Context): WidgetData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_WIDGET_DATA, null)
        if (jsonStr == null) {
            val semesters = CourseDataManager.loadSemesters(context)
            val currentSem = semesters.find { it.isCurrent } ?: semesters.firstOrNull()
            val courses = CourseDataManager.loadCourses(context)
            val schedules = CourseDataManager.loadSchedules(context)
            val sections = CourseDataManager.loadSectionTimes(context)
            val exceptions = CourseDataManager.loadExceptions(context)
            val startDate = currentSem?.startDate
            val totalWeeks = currentSem?.totalWeeks ?: 20
            val curWeek = TimelineProjection.calculateCurrentWeek(startDate, totalWeeks)
            val semName = currentSem?.name ?: "ClassFlow"

            val projected = TimelineProjection.projectOccurrences(
                courses = courses,
                schedules = schedules,
                selectedWeek = curWeek,
                currentWeek = curWeek,
                sectionTimes = sections,
                totalWeeks = totalWeeks,
                showNonCurrentWeekCourses = true,
                semesterStartDate = startDate,
                exceptions = exceptions
            )
            saveAndUpdate(
                context = context,
                semesterName = semName,
                currentWeek = curWeek,
                isDark = false,
                themeMode = "system",
                courses = courses,
                occurrences = projected,
                schedules = schedules,
                sectionTimes = sections,
                semesterStartDate = startDate,
                totalWeeks = totalWeeks,
                exceptions = exceptions
            )
            val newJson = prefs.getString(KEY_WIDGET_DATA, null) ?: return WidgetData()
            return parseWidgetDataJson(newJson)
        }
        return parseWidgetDataJson(jsonStr)
    }

    private fun parseWidgetDataJson(jsonStr: String): WidgetData {
        return try {
            val root = JSONObject(jsonStr)
            val semesterName = root.optString("semesterName", "ClassFlow")
            val currentWeek = root.optInt("currentWeek", 1)
            val isDark = root.optBoolean("isDark", false)
            val themeMode = root.optString("themeMode", "system")

            val itemsList = mutableListOf<WidgetOccurrence>()
            val itemsArr = root.optJSONArray("items") ?: JSONArray()
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.getJSONObject(i)
                itemsList.add(
                    WidgetOccurrence(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        teacher = if (obj.has("teacher")) obj.optString("teacher") else null,
                        classroom = if (obj.has("classroom")) obj.optString("classroom") else null,
                        dayOfWeek = obj.optInt("dayOfWeek", 1),
                        startSection = obj.optInt("startSection", 1),
                        endSection = obj.optInt("endSection", 2),
                        startTime = obj.optString("startTime", ""),
                        endTime = obj.optString("endTime", ""),
                        colorHex = obj.optString("colorHex", "#4F46E5"),
                        isCurrentWeek = obj.optBoolean("isCurrentWeek", true),
                        isSchedule = obj.optBoolean("isSchedule", false),
                        date = obj.optString("date", ""),
                        weekNumber = obj.optInt("weekNumber", currentWeek)
                    )
                )
            }

            val secTimesList = mutableListOf<Pair<String, String>>()
            val secArr = root.optJSONArray("sectionTimes") ?: JSONArray()
            for (i in 0 until secArr.length()) {
                val obj = secArr.getJSONObject(i)
                secTimesList.add(obj.optString("sec") to obj.optString("time"))
            }

            WidgetData(
                semesterName = semesterName,
                currentWeek = currentWeek,
                isDark = isDark,
                themeMode = themeMode,
                items = itemsList,
                sectionTimes = secTimesList
            )
        } catch (_: Throwable) {
            WidgetData()
        }
    }

    fun saveAndUpdate(
        context: Context,
        semesterName: String,
        currentWeek: Int,
        isDark: Boolean,
        themeMode: String,
        courses: List<Course>,
        occurrences: List<TimelineItem>,
        schedules: List<CalendarEvent>,
        sectionTimes: List<SectionTime>,
        semesterStartDate: String? = null,
        totalWeeks: Int = 20,
        exceptions: List<ScheduleException> = emptyList()
    ) {
        val root = JSONObject()
        root.put("semesterName", semesterName)
        root.put("currentWeek", currentWeek)
        root.put("isDark", isDark)
        root.put("themeMode", themeMode)

        val itemsArr = JSONArray()
        val widgetItems = mutableListOf<WidgetOccurrence>()

        val currentWeekOccs = if (occurrences.isNotEmpty()) {
            occurrences
        } else {
            TimelineProjection.projectOccurrences(
                courses = courses,
                schedules = schedules,
                selectedWeek = currentWeek,
                currentWeek = currentWeek,
                sectionTimes = sectionTimes,
                totalWeeks = totalWeeks,
                showNonCurrentWeekCourses = true,
                semesterStartDate = semesterStartDate,
                exceptions = exceptions
            )
        }

        val allProjected = mutableListOf<TimelineItem>()
        allProjected.addAll(currentWeekOccs)

        // Also project next week so Sunday -> Monday cross-week queries for "明天" (tomorrow) have complete data
        val nextWeek = currentWeek + 1
        if (nextWeek <= totalWeeks) {
            val nextWeekOccs = TimelineProjection.projectOccurrences(
                courses = courses,
                schedules = schedules,
                selectedWeek = nextWeek,
                currentWeek = currentWeek,
                sectionTimes = sectionTimes,
                totalWeeks = totalWeeks,
                showNonCurrentWeekCourses = true,
                semesterStartDate = semesterStartDate,
                exceptions = exceptions
            )
            allProjected.addAll(nextWeekOccs)
        }

        for (occ in allProjected) {
            widgetItems.add(
                WidgetOccurrence(
                    id = occ.id,
                    title = occ.title,
                    teacher = occ.teacher,
                    classroom = occ.location,
                    dayOfWeek = occ.dayOfWeek,
                    startSection = occ.startSection,
                    endSection = occ.endSection,
                    startTime = occ.startTime ?: "",
                    endTime = occ.endTime ?: "",
                    colorHex = occ.color,
                    isCurrentWeek = occ.isCurrentWeek,
                    isSchedule = occ.calendarEventId != null || occ.isTask,
                    date = occ.date,
                    weekNumber = occ.weekNumber
                )
            )
        }

        for (item in widgetItems) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            if (item.teacher != null) obj.put("teacher", item.teacher)
            if (item.classroom != null) obj.put("classroom", item.classroom)
            obj.put("dayOfWeek", item.dayOfWeek)
            obj.put("startSection", item.startSection)
            obj.put("endSection", item.endSection)
            obj.put("startTime", item.startTime)
            obj.put("endTime", item.endTime)
            obj.put("colorHex", item.colorHex)
            obj.put("isCurrentWeek", item.isCurrentWeek)
            obj.put("isSchedule", item.isSchedule)
            obj.put("date", item.date)
            obj.put("weekNumber", item.weekNumber)
            itemsArr.put(obj)
        }
        root.put("items", itemsArr)

        val secArr = JSONArray()
        for (st in sectionTimes) {
            val obj = JSONObject()
            obj.put("sec", st.section.toString())
            obj.put("time", "${st.startTime}\n${st.endTime}")
            secArr.put(obj)
        }
        root.put("sectionTimes", secArr)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WIDGET_DATA, root.toString()).apply()

        triggerWidgetUpdate(context)
    }

    fun triggerWidgetUpdate(context: Context) {
        // Trigger Glance AppWidgets to update immediately
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WeekTimetableGlanceWidget().updateAll(context)
                NextClassGlanceWidget().updateAll(context)
                TodayClassesGlanceWidget().updateAll(context)
            } catch (_: Throwable) {}
        }
    }
}
