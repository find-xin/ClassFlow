package org.classflow.notifications

import org.classflow.data.local.TimelineProjection
import org.classflow.data.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class PlannedReminder(val key: String, val title: String, val location: String?, val fireAt: Long)

object ReminderPlan {
    fun build(semester: Semester, courses: List<Course>, events: List<CalendarEvent>,
              sections: List<SectionTime>, exceptions: List<ScheduleException>,
              courseEnabled: Boolean, courseOffset: Int, now: Long, zone: ZoneId): List<PlannedReminder> {
        val firstDay = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val currentWeek = TimelineProjection.calculateCurrentWeek(semester.startDate, semester.totalWeeks, java.util.Date(now))
        val eventMap = events.associateBy { it.id }
        val plans = mutableListOf<PlannedReminder>()
        for (week in currentWeek..minOf(semester.totalWeeks, currentWeek + 2)) {
            // Project each entity independently: the screen projection merges similarly named
            // cards, which must never erase an independent reminder.
            val items = courses.flatMap { course ->
                TimelineProjection.projectOccurrences(listOf(course), emptyList(), week, currentWeek,
                    sections, semester.totalWeeks, false, semester.startDate, exceptions)
            } + events.flatMap { event ->
                TimelineProjection.projectOccurrences(emptyList(), listOf(event), week, currentWeek,
                    sections, semester.totalWeeks, false, semester.startDate, exceptions)
            }
            for (item in items) {
                if (!item.isCurrentWeek || item.status == OccurrenceStatus.CANCELLED || item.isAllDay) continue
                val day = try { LocalDate.parse(item.date) } catch (_: Exception) { continue }
                if (day < firstDay || day > firstDay.plusDays(7)) continue
                val offset = if (item.calendarEventId != null) eventMap[item.calendarEventId]?.alertMinutes
                             else if (item.courseId != null && courseEnabled) courseOffset else null
                if (offset == null || offset < 0) continue
                val start = item.startTime?.let { raw ->
                    try { LocalTime.parse(raw.take(5)) } catch (_: Exception) { null }
                } ?: continue
                val fire = LocalDateTime.of(day, start).atZone(zone).toInstant().toEpochMilli() - offset * 60_000L
                if (fire > now) plans.add(PlannedReminder(item.id, item.title, item.location, fire))
            }
        }
        return plans.distinctBy { it.key }.sortedBy { it.fireAt }
    }
}
