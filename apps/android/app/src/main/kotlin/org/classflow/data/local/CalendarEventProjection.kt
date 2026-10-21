package org.classflow.data.local

import org.classflow.data.model.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Calendar recurrence parity with the shared Swift projector. No network or UI dependencies. */
object CalendarEventProjection {
    fun project(event: CalendarEvent, semesterStart: String?, week: Int, sections: List<SectionTime>, exceptions: List<ScheduleException>): List<TimelineItem> {
        val monday = LocalDate.parse(TimelineProjection.calculateDateString(semesterStart, week, 1))
        val baseMonday = LocalDate.parse(TimelineProjection.calculateDateString(semesterStart, 1, 1))
        fun date(value: String?): LocalDate? = runCatching { LocalDate.parse(value?.take(10)) }.getOrNull()
        val start = date(event.startDate) ?: date(event.startTime) ?: baseMonday
        val frequency = when (event.frequency) {
            "不重复" -> "none"; "每天" -> "daily"; "工作日" -> "workdays"; "周末" -> "weekends"
            "每周" -> "weekly"; "每两周" -> "biweekly"; "每月" -> "monthly"; "自定义每周某几天", "自定义", "自定义星期", "custom" -> "customDays"; "自定义周次" -> "customWeeks"
            else -> event.frequency
        }
        val cap = date(event.endDate)
        val until = if (event.endRepeatType == "untilDate") date(event.endRepeatUntil) else null
        val interval = if (frequency == "biweekly") 2 else event.intervalWeeks.coerceAtLeast(1)
        val baseWeek = ChronoUnit.WEEKS.between(baseMonday, start.minusDays((start.dayOfWeek.value - 1).toLong())).toInt() + 1
        val origins = mutableListOf<LocalDate>()
        var cursor = start
        while (cursor <= monday.plusDays(6)) {
            if (frequency == "none") { origins.add(start); break }
            if ((cap != null && cursor > cap) || (until != null && cursor > until)) break
            if (event.endRepeatType == "count" && origins.size >= (event.endRepeatCount ?: 0)) break
            val day = cursor.dayOfWeek.value
            val activeWeek = ChronoUnit.WEEKS.between(baseMonday, cursor.minusDays((day - 1).toLong())).toInt() + 1
            var matches = when (frequency) {
                "daily" -> true; "workdays" -> day <= 5; "weekends" -> day >= 6
                "weekly", "biweekly", "customDays", "customWeeks" -> if (event.customDaysOfWeek.isNotEmpty()) day in event.customDaysOfWeek else day == start.dayOfWeek.value
                "monthly" -> cursor.dayOfMonth == start.dayOfMonth
                else -> false
            }
            if (frequency in listOf("daily", "workdays", "weekends") && event.customDaysOfWeek.isNotEmpty()) matches = day in event.customDaysOfWeek
            if (event.customWeeks.isNotEmpty()) matches = matches && activeWeek in event.customWeeks
            if (interval > 1) matches = matches && (activeWeek - baseWeek) % interval == 0
            if (matches) origins.add(cursor)
            cursor = cursor.plusDays(1)
        }
        val end = if (frequency == "none") cap ?: date(event.endTime) ?: start else date(event.endTime) ?: start
        val durationStart = if (frequency == "none") start else date(event.startTime) ?: start
        val span = ChronoUnit.DAYS.between(durationStart, end).coerceAtLeast(0)
        fun clock(value: String, fallback: String) = when {
            value.length >= 16 -> value.substring(11, 16)
            value.length == 5 -> value
            else -> fallback
        }
        val startClock = if (event.timeMode == "sections") sections.firstOrNull { it.section == event.startSection }?.startTime ?: clock(event.startTime, "08:00") else clock(event.startTime, "08:00")
        val endClock = if (event.timeMode == "sections") sections.firstOrNull { it.section == event.endSection }?.endTime ?: clock(event.endTime, "09:00") else clock(event.endTime, "09:00")
        return origins.flatMap { origin ->
            val exception = exceptions.firstOrNull { it.scheduleId.equals(event.id, true) && it.originalDate == origin.toString() }
            if (exception?.type == ExceptionType.CANCEL && exception.reason == "单次删除") emptyList() else (0..span).mapNotNull { offset ->
                val current = origin.plusDays(offset)
                if (current < monday || current > monday.plusDays(6)) return@mapNotNull null
                val startTime = if (event.isAllDay || offset > 0) "00:00" else startClock
                val endTime = if (event.isAllDay || offset < span) "23:59" else endClock
                if (!event.isAllDay && startTime >= endTime) return@mapNotNull null
                val first = if (event.timeMode == "sections") event.startSection ?: 1 else sections.firstOrNull { it.endTime > startTime }?.section ?: 1
                val last = if (event.timeMode == "sections") event.endSection ?: first else sections.lastOrNull { it.startTime < endTime }?.section ?: first
                TimelineItem(id = "event_occ_${event.id}_$current", calendarEventId = event.id, scheduleId = event.id, title = event.title,
                    location = event.location, date = current.toString(), dayOfWeek = current.dayOfWeek.value, startSection = first, endSection = maxOf(first, last),
                    startTime = startTime, endTime = endTime, weekNumber = week, isCurrentWeek = true,
                    status = if (exception?.type == ExceptionType.CANCEL) OccurrenceStatus.CANCELLED else OccurrenceStatus.NORMAL,
                    color = event.color, notes = event.description, isAllDay = event.isAllDay, isTask = false, isRecurring = frequency != "none")
            }
        }
    }
}
