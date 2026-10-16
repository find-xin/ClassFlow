package org.classflow.data.model

import java.util.UUID

enum class WeekPatternType {
    ALL,
    ODD,
    EVEN,
    CUSTOM
}

data class SectionTime(
    val section: Int,
    val startTime: String,
    val endTime: String
)

data class Semester(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startDate: String,
    val endDate: String,
    val totalWeeks: Int,
    val isCurrent: Boolean = false,
    val timeZone: String = "Asia/Shanghai",
    val sections: List<SectionTime> = emptyList()
)

data class CourseSchedule(
    val id: String = UUID.randomUUID().toString(),
    val dayOfWeek: Int, // 1 (Mon) - 7 (Sun)
    val startSection: Int,
    val endSection: Int,
    val classroom: String? = null,
    val weekPattern: WeekPatternType = WeekPatternType.ALL,
    val customWeeks: List<Int> = emptyList()
)

data class Course(
    val id: String = UUID.randomUUID().toString(),
    val semesterId: String,
    val name: String,
    val teacher: String? = null,
    val color: String = "#4F46E5",
    val notes: String? = null,
    val schedules: List<CourseSchedule> = emptyList()
)

enum class ExceptionType {
    CANCEL,
    RESCHEDULE,
    ROOM_CHANGE
}

data class ScheduleException(
    val id: String = UUID.randomUUID().toString(),
    val scheduleId: String,
    val originalDate: String,
    val type: ExceptionType,
    val targetDate: String? = null,
    val targetDayOfWeek: Int? = null,
    val targetStartSection: Int? = null,
    val targetEndSection: Int? = null,
    val targetClassroom: String? = null,
    val reason: String? = null
)

enum class OccurrenceStatus {
    NORMAL,
    CANCELLED,
    RESCHEDULED,
    MAKEUP
}

data class TimelineItem(
    val id: String,
    val courseId: String? = null,
    val scheduleId: String? = null,
    val calendarEventId: String? = null,
    val title: String,
    val location: String? = null,
    val date: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val startTime: String? = null,
    val endTime: String? = null,
    val weekNumber: Int = 1,
    val isCurrentWeek: Boolean = true,
    val status: OccurrenceStatus = OccurrenceStatus.NORMAL,
    val color: String = "#4F46E5",
    val teacher: String? = null,
    val notes: String? = null,
    val hasConflict: Boolean = false,
    val isAllDay: Boolean = false,
    val isTask: Boolean = false,
    val isCompleted: Boolean = false
)

enum class EditingScope {
    THIS_ONLY,
    THIS_AND_FUTURE,
    ALL
}

data class CalendarMapping(
    val id: String = UUID.randomUUID().toString(),
    val semesterId: String,
    val courseId: String,
    val scheduleId: String,
    val occurrenceDate: String,
    val startSection: Int,
    val endSection: Int,
    val externalEventId: String,
    val contentHash: String,
    val lastSyncedAt: String
)

data class CalendarEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startTime: String = "08:00",
    val endTime: String = "09:00",
    val isAllDay: Boolean = false,
    val location: String? = null,
    val description: String? = null,
    val color: String = "#FF5A5F",
    val startSection: Int? = null,
    val endSection: Int? = null,
    val frequency: String = "none",
    val customDaysOfWeek: List<Int> = emptyList(),
    val isTask: Boolean = false,
    val isCompleted: Boolean = false
)

data class StudyTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val courseName: String? = null,
    val targetMinutes: Int = 45,
    val completedMinutes: Int = 0,
    val isCompleted: Boolean = false
)

data class FocusRecord(
    val id: String = UUID.randomUUID().toString(),
    val taskTitle: String,
    val courseName: String? = null,
    val durationMinutes: Int,
    val completedAt: String
)

data class AppSettings(
    val themeMode: String = "system", // "system", "light", "dark"
    val enableStudyModule: Boolean = true,
    val showWeekends: Boolean = true,
    val showNonCurrentWeekCourses: Boolean = true,
    val cardOpacity: Float = 0.88f,
    val wallpaperPreset: String = "default", // "default", "aurora", "midnight", "warm_cream", "mint", "sky"
    val customWallpaperUri: String? = null,
    val usePureColorBackground: Boolean = false,
    val headerColorHex: String = "#1F1F1F",
    val showGridGuideLines: Boolean = false,
    val showSectionTimeInAxis: Boolean = true,
    val showSaturday: Boolean = true,
    val showSunday: Boolean = true,
    val cardTextColorMode: String = "auto", // "auto", "light", "dark"
    val cardBorderColorMode: String = "none", // "none", "cardColor", "subtle"
    val gridHeight: Float = 64f,
    val cardCornerRadius: Float = 6f,
    val nonCurrentWeekOpacityRatio: Float = 0.50f,
    val cardFontSize: Float = 13f,
    val showTimeInCard: Boolean = false,
    val showLocationInCard: Boolean = true,
    val showAtPrefixForRoom: Boolean = true,
    val showTeacherInCard: Boolean = true,
    val showNotesInCard: Boolean = false,
    val cardTextHorizontalCenter: Boolean = false,
    val cardTextVerticalCenter: Boolean = false,
    val cardUseDottedBorder: Boolean = false,
    val cardBorderUseCardColor: Boolean = true,
    val cardTextBlendCardColor: Boolean = false,
    val syncInterval: String = "manual", // "manual", "on_launch", "15m", "30m", "1h", "6h", "daily"
    val autoSyncEnabled: Boolean = false,
    val lastSyncedTime: String? = null,
    val calendarName: String = "ClassFlow",
    val calendarReminderMinutes: Int? = 15,
    val calendarIncludeNotes: Boolean = true
)

