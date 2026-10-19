package org.classflow.data.local

import org.classflow.data.model.*

/**
 * Unified Timeline Projection Engine for Android:
 * Mirrors macOS TimelineEngine parity. Dynamically projects raw domain courses and
 * calendar events into TimelineItem occurrences for any selected week.
 *
 * Rules:
 * 1. Courses is the single source of truth.
 * 2. An occurrence is generated for each schedule slot matching the week's criteria.
 * 3. Supports WeekPatternType (ALL, SINGLE, DOUBLE, CUSTOM) and customWeeks matrix.
 * 4. Respects showNonCurrentWeekCourses setting (whether to show ghost cards or hide them).
 */
object TimelineProjection {

    fun calculateDateString(semesterStartDate: String?, selectedWeek: Int, dayOfWeek: Int): String {
        return try {
            val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val baseDate = if (!semesterStartDate.isNullOrBlank()) {
                df.parse(semesterStartDate) ?: java.util.Date()
            } else {
                java.util.Date()
            }
            val cal = java.util.Calendar.getInstance()
            cal.time = baseDate
            val startDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
            val daysFromMon = if (startDayOfWeek == java.util.Calendar.SUNDAY) 6 else startDayOfWeek - java.util.Calendar.MONDAY
            cal.add(java.util.Calendar.DAY_OF_YEAR, -daysFromMon)
            cal.add(java.util.Calendar.DAY_OF_YEAR, (selectedWeek - 1) * 7 + (dayOfWeek - 1))
            df.format(cal.time)
        } catch (_: Exception) {
            val cal = java.util.Calendar.getInstance()
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)
        }
    }

    fun calculateCurrentWeek(
        semesterStartDate: String?,
        totalWeeks: Int = 20,
        targetDate: java.util.Date = java.util.Date()
    ): Int {
        if (semesterStartDate.isNullOrBlank()) return 1
        return try {
            val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val startDate = df.parse(semesterStartDate) ?: return 1
            val cal = java.util.Calendar.getInstance()
            cal.time = startDate
            val startDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
            val daysFromMon = if (startDayOfWeek == java.util.Calendar.SUNDAY) 6 else startDayOfWeek - java.util.Calendar.MONDAY
            cal.add(java.util.Calendar.DAY_OF_YEAR, -daysFromMon)
            val firstMonday = cal.timeInMillis

            val targetCal = java.util.Calendar.getInstance()
            targetCal.time = targetDate
            val targetDayOfWeek = targetCal.get(java.util.Calendar.DAY_OF_WEEK)
            val targetDaysFromMon = if (targetDayOfWeek == java.util.Calendar.SUNDAY) 6 else targetDayOfWeek - java.util.Calendar.MONDAY
            targetCal.add(java.util.Calendar.DAY_OF_YEAR, -targetDaysFromMon)
            val targetMonday = targetCal.timeInMillis

            val diffDays = (targetMonday - firstMonday) / (1000L * 60 * 60 * 24)
            val weekIndex = (diffDays / 7).toInt() + 1
            maxOf(1, minOf(weekIndex, totalWeeks))
        } catch (_: Exception) {
            1
        }
    }

    fun projectOccurrences(
        courses: List<Course>,
        schedules: List<CalendarEvent>,
        selectedWeek: Int,
        currentWeek: Int,
        sectionTimes: List<SectionTime>,
        totalWeeks: Int = 20,
        showNonCurrentWeekCourses: Boolean = true,
        semesterStartDate: String? = null,
        exceptions: List<ScheduleException> = emptyList()
    ): List<TimelineItem> {
        val safeCourses = CourseDataManager.consolidateCourses(courses)
        val safeSchedules = CourseDataManager.consolidateCalendarEvents(schedules)
        val results = mutableListOf<TimelineItem>()

        // 1. Project Course Schedules
        for (course in safeCourses) {
            for (sched in course.schedules) {
                // Determine whether this schedule applies to selectedWeek
                val isWeekActive = when (sched.weekPattern) {
                    WeekPatternType.ALL -> {
                        if (sched.customWeeks.isNotEmpty()) sched.customWeeks.contains(selectedWeek) else true
                    }
                    WeekPatternType.ODD -> {
                        if (sched.customWeeks.isNotEmpty()) {
                            sched.customWeeks.contains(selectedWeek) && (selectedWeek % 2 == 1)
                        } else {
                            selectedWeek % 2 == 1
                        }
                    }
                    WeekPatternType.EVEN -> {
                        if (sched.customWeeks.isNotEmpty()) {
                            sched.customWeeks.contains(selectedWeek) && (selectedWeek % 2 == 0)
                        } else {
                            selectedWeek % 2 == 0
                        }
                    }
                    WeekPatternType.CUSTOM -> {
                        if (sched.customWeeks.isNotEmpty()) sched.customWeeks.contains(selectedWeek) else true
                    }
                }

                // If non-current week courses are disabled and this course is inactive this week, skip
                if (!isWeekActive) {
                    if (!showNonCurrentWeekCourses) {
                        continue
                    }
                    // A course schedule that has custom weeks (e.g. truncated via thisAndFuture deletion)
                    // must not linger as a ghost card in weeks past its last active custom week.
                    if (sched.customWeeks.isNotEmpty()) {
                        val maxCustom = sched.customWeeks.maxOrNull() ?: sched.endWeek
                        if (selectedWeek > maxCustom) {
                            continue
                        }
                    }
                }

                val secStart = sched.startSection
                val secEnd = maxOf(sched.startSection, sched.endSection)
                val startT = sched.customStartTime
                    ?: sectionTimes.find { it.section == secStart }?.startTime
                    ?: String.format("%02d:00", 7 + secStart)
                val endT = sched.customEndTime
                    ?: sectionTimes.find { it.section == secEnd }?.endTime
                    ?: String.format("%02d:45", 7 + secEnd)

                val dateStr = calculateDateString(semesterStartDate, selectedWeek, sched.dayOfWeek)
                var status = OccurrenceStatus.NORMAL
                var effectiveLocation = sched.classroom
                var effectiveNotes = course.notes

                // Apply matching schedule exceptions
                var shouldSkipCourse = false
                val ex = exceptions.firstOrNull { it.scheduleId == sched.id && it.originalDate == dateStr }
                if (ex != null) {
                    when (ex.type) {
                        ExceptionType.CANCEL -> {
                            if (ex.reason == "单次删除") {
                                shouldSkipCourse = true
                            } else {
                                status = OccurrenceStatus.CANCELLED
                            }
                        }
                        ExceptionType.RESCHEDULE -> {
                            status = OccurrenceStatus.RESCHEDULED
                            if (!ex.reason.isNullOrBlank()) {
                                effectiveNotes = if (effectiveNotes.isNullOrBlank()) ex.reason else "$effectiveNotes (${ex.reason})"
                            }
                        }
                        ExceptionType.ROOM_CHANGE -> {
                            if (!ex.targetClassroom.isNullOrBlank()) {
                                effectiveLocation = ex.targetClassroom
                            }
                        }
                    }
                }
                if (shouldSkipCourse) continue

                results.add(
                    TimelineItem(
                        id = "occ_${course.id}_${sched.id}_$selectedWeek",
                        courseId = course.id,
                        scheduleId = sched.id,
                        title = course.name,
                        location = effectiveLocation,
                        date = dateStr,
                        dayOfWeek = sched.dayOfWeek,
                        startSection = secStart,
                        endSection = secEnd,
                        startTime = startT,
                        endTime = endT,
                        weekNumber = selectedWeek,
                        isCurrentWeek = isWeekActive, // Parity with macOS: true if course runs in this selectedWeek
                        status = status,
                        color = course.color,
                        teacher = sched.teacher ?: course.teacher,
                        notes = effectiveNotes,
                        isRecurring = true
                    )
                )
            }
        }

        // 2. Project Calendar Events / Agenda Schedules
        for (event in safeSchedules) {
            // Check week matrix filtering: if customWeeks is configured, must contain selectedWeek
            if (event.customWeeks.isNotEmpty() && !event.customWeeks.contains(selectedWeek)) {
                continue
            }

            val normFreq = normalizeFrequency(event.frequency)

            for (day in 1..7) {
                val dateStr = calculateDateString(semesterStartDate, selectedWeek, day)

                // Check event startDate bounds (safe 10-char comparison to prevent time suffix mismatches)
                val sDate = event.startDate?.take(10)
                if (!sDate.isNullOrBlank() && dateStr < sDate) {
                    continue
                }

                val isRecurring = normFreq != "none"
                if (!isRecurring) {
                    // Check event endDate bounds for single event
                    val eDate = event.endDate?.take(10)
                    if (!eDate.isNullOrBlank() && dateStr > eDate) {
                        continue
                    }
                } else {
                    // Check recurrence endRepeatUntil bounds
                    if (event.endRepeatType == "untilDate" && !event.endRepeatUntil.isNullOrBlank()) {
                        val uDate = event.endRepeatUntil.take(10)
                        if (dateStr > uDate) {
                            continue
                        }
                    }
                    val eDate = event.endDate?.take(10)
                    if (!eDate.isNullOrBlank() && dateStr > eDate) {
                        continue
                    }
                }

                var shouldInclude = false
                when (normFreq) {
                    "daily" -> {
                        shouldInclude = if (event.customDaysOfWeek.isNotEmpty()) event.customDaysOfWeek.contains(day) else true
                    }
                    "workdays" -> {
                        shouldInclude = if (event.customDaysOfWeek.isNotEmpty()) event.customDaysOfWeek.contains(day) else (day in 1..5)
                    }
                    "weekends" -> {
                        shouldInclude = if (event.customDaysOfWeek.isNotEmpty()) event.customDaysOfWeek.contains(day) else (day in 6..7)
                    }
                    "weekly" -> {
                        if (event.customDaysOfWeek.isNotEmpty()) {
                            shouldInclude = event.customDaysOfWeek.contains(day)
                        } else if (!sDate.isNullOrBlank()) {
                            shouldInclude = (day == getDayOfWeekFromDateString(sDate))
                        } else if (event.startTime.length >= 10 && event.startTime.contains("-")) {
                            shouldInclude = (day == getDayOfWeekFromDateString(event.startTime.substring(0, 10)))
                        } else {
                            shouldInclude = (day == 1)
                        }
                    }
                    "customDays", "custom" -> {
                        shouldInclude = if (event.customDaysOfWeek.isNotEmpty()) event.customDaysOfWeek.contains(day) else (day == 1)
                    }
                    "customWeeks" -> {
                        if (event.customDaysOfWeek.isNotEmpty()) {
                            shouldInclude = event.customDaysOfWeek.contains(day)
                        } else if (!sDate.isNullOrBlank()) {
                            shouldInclude = (day == getDayOfWeekFromDateString(sDate))
                        } else if (event.startTime.length >= 10 && event.startTime.contains("-")) {
                            shouldInclude = (day == getDayOfWeekFromDateString(event.startTime.substring(0, 10)))
                        } else {
                            shouldInclude = (day == 1)
                        }
                    }
                    else -> { // "none"
                        if (event.customDaysOfWeek.isNotEmpty()) {
                            shouldInclude = event.customDaysOfWeek.contains(day)
                        } else if (!sDate.isNullOrBlank()) {
                            shouldInclude = (sDate == dateStr)
                        } else if (event.startTime.startsWith(dateStr)) {
                            shouldInclude = true
                        } else {
                            shouldInclude = (day == 1 && selectedWeek == currentWeek)
                        }
                    }
                }

                if (!shouldInclude) continue

                // Check exception cancellation for this calendar event occurrence
                val ex = exceptions.firstOrNull { it.scheduleId == event.id && it.originalDate == dateStr }
                var status = OccurrenceStatus.NORMAL
                if (ex != null && ex.type == ExceptionType.CANCEL) {
                    if (ex.reason == "单次删除") {
                        continue
                    }
                    status = OccurrenceStatus.CANCELLED
                }

                val secStart = event.startSection ?: 1
                val secEnd = event.endSection ?: secStart

                // Strip full ISO timestamps if present
                val rawStartT = event.startTime
                val startT = when {
                    rawStartT.contains("T") -> rawStartT.substringAfter("T").take(5)
                    rawStartT.contains(" ") -> rawStartT.substringAfter(" ").take(5)
                    rawStartT.isNotBlank() -> rawStartT
                    else -> sectionTimes.find { it.section == secStart }?.startTime ?: "08:00"
                }

                val rawEndT = event.endTime
                val endT = when {
                    rawEndT.contains("T") -> rawEndT.substringAfter("T").take(5)
                    rawEndT.contains(" ") -> rawEndT.substringAfter(" ").take(5)
                    rawEndT.isNotBlank() -> rawEndT
                    else -> sectionTimes.find { it.section == secEnd }?.endTime ?: "09:00"
                }

                val isRec = normFreq != "none" || event.customWeeks.isNotEmpty() || event.customDaysOfWeek.size > 1

                results.add(
                    TimelineItem(
                        id = "event_occ_${event.id}_${selectedWeek}_$day",
                        calendarEventId = event.id,
                        scheduleId = event.id,
                        title = event.title,
                        location = event.location,
                        date = dateStr,
                        dayOfWeek = day,
                        startSection = secStart,
                        endSection = secEnd,
                        startTime = startT,
                        endTime = endT,
                        weekNumber = selectedWeek,
                        isCurrentWeek = true,
                        status = status,
                        color = event.color,
                        teacher = null,
                        notes = event.description,
                        isAllDay = event.isAllDay,
                        isTask = false,
                        isRecurring = isRec
                    )
                )
            }
        }

        // 3. Annotate conflicts across active occurrences on the same day
        val activeItems = results.filter { it.status != OccurrenceStatus.CANCELLED && it.isCurrentWeek }
        val conflictedIds = mutableSetOf<String>()
        for (i in 0 until activeItems.size) {
            val a = activeItems[i]
            for (j in i + 1 until activeItems.size) {
                val b = activeItems[j]
                if (a.dayOfWeek == b.dayOfWeek && maxOf(a.startSection, b.startSection) <= minOf(a.endSection, b.endSection)) {
                    conflictedIds.add(a.id)
                    conflictedIds.add(b.id)
                }
            }
        }

        val deduplicatedResults = consolidateOccurrences(results)
        return if (conflictedIds.isEmpty()) {
            deduplicatedResults
        } else {
            deduplicatedResults.map { if (conflictedIds.contains(it.id)) it.copy(hasConflict = true) else it }
        }
    }

    fun consolidateOccurrences(occurrences: List<TimelineItem>): List<TimelineItem> {
        if (occurrences.size <= 1) return occurrences
        val grouped = occurrences.groupBy { "${it.dayOfWeek}___${it.title.trim().lowercase()}" }
        val output = mutableListOf<TimelineItem>()
        for ((_, group) in grouped) {
            if (group.size == 1) {
                output.add(group.first())
            } else {
                val sorted = group.sortedWith(compareBy({ it.startSection }, { it.endSection })).toMutableList()
                var i = 0
                while (i < sorted.size) {
                    var current = sorted[i]
                    var mergedAny = false
                    var j = i + 1
                    while (j < sorted.size) {
                        val other = sorted[j]
                        val overlaps = maxOf(current.startSection, other.startSection) <= minOf(current.endSection, other.endSection)
                        if (overlaps) {
                            current = current.copy(
                                startSection = minOf(current.startSection, other.startSection),
                                endSection = maxOf(current.endSection, other.endSection),
                                isCurrentWeek = current.isCurrentWeek || other.isCurrentWeek,
                                location = if (current.location.isNullOrBlank()) other.location else current.location,
                                teacher = if (current.teacher.isNullOrBlank()) other.teacher else current.teacher
                            )
                            sorted.removeAt(j)
                            sorted[i] = current
                            mergedAny = true
                        } else {
                            j++
                        }
                    }
                    if (!mergedAny) {
                        output.add(current)
                        i++
                    }
                }
            }
        }
        return output
    }

    fun normalizeFrequency(freq: String?): String {
        if (freq.isNullOrBlank()) return "none"
        val lower = freq.trim().lowercase()
        return when {
            lower.contains("none") || lower.contains("不重复") || lower.contains("一次性") -> "none"
            lower.contains("workday") || lower.contains("工作日") -> "workdays"
            lower.contains("weekend") || lower.contains("周末") -> "weekends"
            lower.contains("daily") || lower.contains("每天") || lower == "日" -> "daily"
            lower.contains("customdays") || lower.contains("自定义每周某几天") -> "customDays"
            lower.contains("customweeks") || lower.contains("自定义周数") || lower.contains("按周次重复") || lower.contains("周次") -> "customWeeks"
            lower.contains("custom") || lower.contains("自定义") -> "custom"
            lower.contains("weekly") || lower.contains("每周") || lower == "周" -> "weekly"
            lower.contains("monthly") || lower.contains("每月") || lower == "月" -> "monthly"
            else -> "none"
        }
    }

    fun getDayOfWeekFromDateString(dateStr: String): Int {
        return try {
            val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val date = df.parse(dateStr) ?: return 1
            val cal = java.util.Calendar.getInstance()
            cal.time = date
            val w = cal.get(java.util.Calendar.DAY_OF_WEEK)
            if (w == java.util.Calendar.SUNDAY) 7 else w - 1
        } catch (_: Exception) {
            1
        }
    }
}
