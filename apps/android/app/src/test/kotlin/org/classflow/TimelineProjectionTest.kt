package org.classflow

import org.classflow.data.local.TimelineProjection
import org.classflow.data.model.*
import org.junit.Assert.*
import org.junit.Test

class TimelineProjectionTest {

    @Test
    fun testAddedCourseOnSaturdayProjectsCorrectly() {
        val course = Course(
            id = "c_su",
            semesterId = "sem_1",
            name = "苏启杭",
            teacher = null,
            color = "#5B7FB5",
            schedules = listOf(
                CourseSchedule(
                    id = "s_1",
                    courseId = "c_su",
                    dayOfWeek = 6, // Saturday
                    startSection = 2,
                    endSection = 3,
                    classroom = "理学楼 302",
                    weekPattern = WeekPatternType.CUSTOM,
                    customWeeks = (1..16).toList()
                )
            )
        )

        val sections = listOf(
            SectionTime(1, "08:00", "08:45"),
            SectionTime(2, "08:50", "09:35"),
            SectionTime(3, "10:00", "10:45")
        )

        val occurrences = TimelineProjection.projectOccurrences(
            courses = listOf(course),
            schedules = emptyList(),
            selectedWeek = 1,
            currentWeek = 1,
            sectionTimes = sections,
            totalWeeks = 16,
            showNonCurrentWeekCourses = true
        )

        assertEquals(1, occurrences.size)
        val occ = occurrences.first()
        assertEquals("苏启杭", occ.title)
        assertEquals(6, occ.dayOfWeek)
        assertEquals(2, occ.startSection)
        assertEquals(3, occ.endSection)
        assertEquals("08:50", occ.startTime)
        assertEquals("10:45", occ.endTime)
        assertEquals("理学楼 302", occ.location)
        assertTrue("Course should be active in week 1", occ.isCurrentWeek)
    }

    @Test
    fun testNonCurrentWeekFilteringHidesOrDimsCards() {
        val oddWeekCourse = Course(
            id = "c_odd",
            semesterId = "sem_1",
            name = "单周课",
            color = "#4F46E5",
            schedules = listOf(
                CourseSchedule(
                    dayOfWeek = 1,
                    startSection = 1,
                    endSection = 2,
                    weekPattern = WeekPatternType.ODD
                )
            )
        )

        // Week 2 is even, so oddWeekCourse is inactive
        val occurrencesHidden = TimelineProjection.projectOccurrences(
            courses = listOf(oddWeekCourse),
            schedules = emptyList(),
            selectedWeek = 2,
            currentWeek = 2,
            sectionTimes = emptyList(),
            showNonCurrentWeekCourses = false
        )
        assertEquals(0, occurrencesHidden.size)

        val occurrencesShownAsGhost = TimelineProjection.projectOccurrences(
            courses = listOf(oddWeekCourse),
            schedules = emptyList(),
            selectedWeek = 2,
            currentWeek = 2,
            sectionTimes = emptyList(),
            showNonCurrentWeekCourses = true
        )
        assertEquals(1, occurrencesShownAsGhost.size)
        assertFalse("Inactive week course should not have isCurrentWeek = true", occurrencesShownAsGhost.first().isCurrentWeek)
    }

    @Test
    fun testCalendarEventProjection() {
        val event = CalendarEvent(
            id = "event_morning",
            title = "早读",
            startTime = "07:30",
            endTime = "08:00",
            color = "#039BE5",
            frequency = "每天",
            startSection = 1,
            endSection = 1,
            customDaysOfWeek = listOf(1, 2, 3, 4, 5)
        )

        val occurrences = TimelineProjection.projectOccurrences(
            courses = emptyList(),
            schedules = listOf(event),
            selectedWeek = 1,
            currentWeek = 1,
            sectionTimes = emptyList()
        )

        assertEquals(5, occurrences.size)
        assertTrue(occurrences.all { it.title == "早读" })
        assertEquals(listOf(1, 2, 3, 4, 5), occurrences.map { it.dayOfWeek })
    }

    @Test
    fun testCalculateCurrentWeek() {
        val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val startMon = "2026-09-07"

        val week1Date = df.parse("2026-09-07")!!
        val week2Date = df.parse("2026-09-14")!!
        val week3Date = df.parse("2026-09-23")!!

        assertEquals(1, TimelineProjection.calculateCurrentWeek(startMon, 16, week1Date))
        assertEquals(2, TimelineProjection.calculateCurrentWeek(startMon, 16, week2Date))
        assertEquals(3, TimelineProjection.calculateCurrentWeek(startMon, 16, week3Date))

        // Before start date should clamp to 1
        val beforeDate = df.parse("2026-08-01")!!
        assertEquals(1, TimelineProjection.calculateCurrentWeek(startMon, 16, beforeDate))

        // Beyond total weeks should clamp to totalWeeks
        val farDate = df.parse("2028-01-01")!!
        assertEquals(16, TimelineProjection.calculateCurrentWeek(startMon, 16, farDate))
    }

    @Test
    fun testCalculateDateString() {
        val startMon = "2026-09-07"
        val d1 = TimelineProjection.calculateDateString(startMon, selectedWeek = 1, dayOfWeek = 1)
        assertEquals("2026-09-07", d1)

        val d2 = TimelineProjection.calculateDateString(startMon, selectedWeek = 1, dayOfWeek = 5)
        assertEquals("2026-09-11", d2)

        val d3 = TimelineProjection.calculateDateString(startMon, selectedWeek = 2, dayOfWeek = 1)
        assertEquals("2026-09-14", d3)
    }

    @Test
    fun testConflictDetection() {
        val courseA = Course(
            id = "c_a",
            semesterId = "sem_1",
            name = "线性代数",
            schedules = listOf(
                CourseSchedule(id = "s_a", dayOfWeek = 1, startSection = 1, endSection = 2)
            )
        )
        val courseB = Course(
            id = "c_b",
            semesterId = "sem_1",
            name = "大学英语",
            schedules = listOf(
                CourseSchedule(id = "s_b", dayOfWeek = 1, startSection = 2, endSection = 3)
            )
        )

        val occurrences = TimelineProjection.projectOccurrences(
            courses = listOf(courseA, courseB),
            schedules = emptyList(),
            selectedWeek = 1,
            currentWeek = 1,
            sectionTimes = emptyList()
        )

        assertEquals(2, occurrences.size)
        assertTrue("Course A should have conflict flagged", occurrences[0].hasConflict)
        assertTrue("Course B should have conflict flagged", occurrences[1].hasConflict)
    }

    @Test
    fun testScheduleExceptionHandling() {
        val course = Course(
            id = "c_phy",
            semesterId = "sem_1",
            name = "普通物理",
            schedules = listOf(
                CourseSchedule(
                    id = "s_phy_1",
                    dayOfWeek = 1,
                    startSection = 1,
                    endSection = 2,
                    weekPattern = WeekPatternType.ALL
                )
            )
        )

        val exception = ScheduleException(
            id = "exc_cancel_1",
            scheduleId = "s_phy_1",
            originalDate = "2026-09-07",
            type = ExceptionType.CANCEL,
            reason = "中秋节放假停课"
        )

        val occurrences = TimelineProjection.projectOccurrences(
            courses = listOf(course),
            schedules = emptyList(),
            selectedWeek = 1,
            currentWeek = 1,
            sectionTimes = emptyList(),
            semesterStartDate = "2026-09-07",
            exceptions = listOf(exception)
        )

        assertEquals(1, occurrences.size)
        assertEquals(OccurrenceStatus.CANCELLED, occurrences.first().status)
    }

    @Test
    fun testSingleDeleteExceptionHidesOccurrenceCompletely() {
        val course = Course(
            id = "c_cs",
            semesterId = "sem_1",
            name = "操作系统",
            schedules = listOf(
                CourseSchedule(
                    id = "s_cs_1",
                    dayOfWeek = 1,
                    startSection = 1,
                    endSection = 2,
                    weekPattern = WeekPatternType.ALL
                )
            )
        )

        val singleDeleteEx = ScheduleException(
            id = "exc_del_1",
            scheduleId = "s_cs_1",
            originalDate = "2026-09-07",
            type = ExceptionType.CANCEL,
            reason = "单次删除"
        )

        val occurrences = TimelineProjection.projectOccurrences(
            courses = listOf(course),
            schedules = emptyList(),
            selectedWeek = 1,
            currentWeek = 1,
            sectionTimes = emptyList(),
            semesterStartDate = "2026-09-07",
            exceptions = listOf(singleDeleteEx)
        )

        // Single delete should make the occurrence completely vanish from the timeline (Google Calendar style)
        assertEquals(0, occurrences.size)
    }

    @Test
    fun testCalendarEventSingleDeletionAndCancellation() {
        val event = CalendarEvent(
            id = "evt_morning_read",
            title = "晨读早自习",
            startTime = "07:30",
            endTime = "08:15",
            frequency = "每天",
            startSection = 1,
            endSection = 1,
            customDaysOfWeek = listOf(1, 2, 3, 4, 5)
        )

        // Monday is 2026-09-07
        val singleDeleteEx = ScheduleException(
            id = "exc_cal_del",
            scheduleId = "evt_morning_read",
            originalDate = "2026-09-07",
            type = ExceptionType.CANCEL,
            reason = "单次删除"
        )

        val occurrences = TimelineProjection.projectOccurrences(
            courses = emptyList(),
            schedules = listOf(event),
            selectedWeek = 1,
            currentWeek = 1,
            sectionTimes = emptyList(),
            semesterStartDate = "2026-09-07",
            exceptions = listOf(singleDeleteEx)
        )

        // 5 days originally (Mon-Fri). Monday 2026-09-07 was deleted, 4 remain (Tue-Fri)
        assertEquals(4, occurrences.size)
        assertFalse(occurrences.any { it.date == "2026-09-07" })
        assertTrue(occurrences.all { it.scheduleId == "evt_morning_read" })
        assertTrue(occurrences.all { it.isRecurring })
    }

    @Test
    fun testStudyTaskGoogleTasksParityProperties() {
        val task = StudyTask(
            id = "task_algo",
            title = "复习动态规划算法",
            startDate = "2026-09-07",
            dueDate = "2026-09-10 23:59",
            frequency = "每周",
            isCompleted = false
        )

        assertEquals("task_algo", task.id)
        assertEquals("复习动态规划算法", task.title)
        assertEquals("2026-09-07", task.startDate)
        assertEquals("2026-09-10 23:59", task.dueDate)
        assertEquals("每周", task.frequency)
        assertFalse(task.isCompleted)
    }

    @Test
    fun testScheduleEventCrossWeekWidgetProjection() {
        // Schedule event "考试" starting on Week 2 Monday (2026-10-19)
        val scheduleExam = CalendarEvent(
            id = "evt_exam",
            title = "考试",
            startTime = "08:00",
            endTime = "09:35",
            startDate = "2026-10-19",
            startSection = 1,
            endSection = 2,
            frequency = "自定义每周某几天",
            customDaysOfWeek = listOf(1, 3, 5),
            customWeeks = listOf(2, 3),
            isTask = false,
            color = "#EF4444"
        )

        val sections = listOf(
            SectionTime(1, "08:00", "08:45"),
            SectionTime(2, "08:50", "09:35")
        )

        // Semester starts on Monday 2026-10-12 (Week 1 Monday: 2026-10-12, Sunday: 2026-10-18)
        val semesterStart = "2026-10-12"

        // 1. Project Week 1: "考试" has startDate = "2026-10-19", so Week 1 must NOT contain it
        val week1Occurrences = TimelineProjection.projectOccurrences(
            courses = emptyList(),
            schedules = listOf(scheduleExam),
            selectedWeek = 1,
            currentWeek = 1,
            sectionTimes = sections,
            totalWeeks = 20,
            semesterStartDate = semesterStart
        )
        assertFalse("Week 1 must not contain the exam since it starts on week 2", week1Occurrences.any { it.title == "考试" })

        // 2. Project Week 2: "考试" runs on Monday (2026-10-19), Wednesday (2026-10-21), Friday (2026-10-23)
        val week2Occurrences = TimelineProjection.projectOccurrences(
            courses = emptyList(),
            schedules = listOf(scheduleExam),
            selectedWeek = 2,
            currentWeek = 1,
            sectionTimes = sections,
            totalWeeks = 20,
            semesterStartDate = semesterStart
        )
        val mondayExam = week2Occurrences.firstOrNull { it.date == "2026-10-19" && it.title == "考试" }
        assertNotNull("Week 2 Monday must contain the schedule exam occurrence", mondayExam)
        assertEquals(1, mondayExam!!.dayOfWeek)
        assertEquals(1, mondayExam.startSection)
        assertEquals(2, mondayExam.endSection)
        assertEquals("08:00", mondayExam.startTime)
        assertEquals("09:35", mondayExam.endTime)
        assertTrue("Must be identified as schedule", mondayExam.calendarEventId != null)

        // 3. Combined projection for widget (Sunday Week 1 -> Monday Week 2 cross-boundary)
        val combined = week1Occurrences + week2Occurrences
        val tomorrowExam = combined.firstOrNull { it.date == "2026-10-19" && it.title == "考试" }
        assertNotNull("Widget tomorrow query on 2026-10-19 must successfully find the schedule exam", tomorrowExam)
    }

    @Test
    fun testWidgetTimeFilteringAndExpiredEventsExcluded() {
        // Test extractTime utility
        assertEquals("10:05", org.classflow.widgets.extractTime("10:05"))
        assertEquals("10:05", org.classflow.widgets.extractTime("10:05:00"))
        assertEquals("10:05", org.classflow.widgets.extractTime("2026-10-18 10:05"))
        assertEquals("10:05", org.classflow.widgets.extractTime("2026-10-18T10:05:00"))
        assertEquals("", org.classflow.widgets.extractTime(""))

        // When current time is 11:12, an event ending at 10:05 must be filtered out as expired
        val currentTimeStr = "11:12"
        val morningExam = org.classflow.data.local.WidgetOccurrence(
            id = "occ_1",
            title = "考试",
            teacher = null,
            classroom = null,
            dayOfWeek = 7,
            startSection = 1,
            endSection = 2,
            startTime = "08:30",
            endTime = "10:05",
            colorHex = "#EF4444",
            date = "2026-10-18",
            weekNumber = 1
        )
        val afternoonClass = org.classflow.data.local.WidgetOccurrence(
            id = "occ_2",
            title = "大学英语",
            teacher = "王老师",
            classroom = "教一101",
            dayOfWeek = 7,
            startSection = 5,
            endSection = 6,
            startTime = "13:30",
            endTime = "15:05",
            colorHex = "#4F46E5",
            date = "2026-10-18",
            weekNumber = 1
        )

        val todayItems = listOf(morningExam, afternoonClass)
        val upcomingItems = todayItems.filter { occ ->
            val endT = org.classflow.widgets.extractTime(occ.endTime)
            if (endT.isNotBlank()) endT > currentTimeStr else true
        }

        assertEquals(1, upcomingItems.size)
        assertEquals("大学英语", upcomingItems.first().title)
        assertFalse("Morning exam ending at 10:05 must be excluded at 11:12", upcomingItems.any { it.title == "考试" })
    }

    @Test
    fun testDateMatchesNeverFalseMatchOtherDays() {
        val widgetItems = listOf(
            org.classflow.data.local.WidgetOccurrence(
                id = "occ_mon",
                title = "考试",
                teacher = null,
                classroom = null,
                dayOfWeek = 1,
                startSection = 1,
                endSection = 2,
                startTime = "08:30",
                endTime = "10:05",
                colorHex = "#EF4444",
                date = "2026-10-19",
                weekNumber = 2
            )
        )

        // Querying for today (Sunday 2026-10-18) when cache has dates
        val todayDateStr = "2026-10-18"
        val hasDatesInCache = widgetItems.any { it.date.isNotBlank() }
        val dateMatches = widgetItems.filter { it.date == todayDateStr && it.isCurrentWeek }

        val todayItems = if (hasDatesInCache) {
            dateMatches
        } else {
            widgetItems.filter { it.dayOfWeek == 7 && it.isCurrentWeek }
        }

        assertTrue("Sunday Oct 18 must be completely empty when exam is on Monday Oct 19", todayItems.isEmpty())
    }

    @Test
    fun testDynamicTimetableTotalSectionsCalculation() {
        // 1. Configured with 8 sections (e.g. primary school or short schedule)
        val eightSections = (1..8).map { s -> SectionTime(s, "08:00", "08:45") }
        val maxConfig8 = if (eightSections.isNotEmpty()) maxOf(eightSections.size, eightSections.maxOf { it.section }) else 12
        val maxCourse0 = 0
        val total8 = maxOf(1, maxOf(maxConfig8, maxCourse0))
        assertEquals(8, total8)

        // 2. Configured with 14 sections (morning, afternoon, evening periods)
        val fourteenSections = (1..14).map { s -> SectionTime(s, "08:00", "08:45") }
        val maxConfig14 = if (fourteenSections.isNotEmpty()) maxOf(fourteenSections.size, fourteenSections.maxOf { it.section }) else 12
        val total14 = maxOf(1, maxOf(maxConfig14, maxCourse0))
        assertEquals(14, total14)

        // 3. Configured with 10 sections, but an occurrence ends at section 13
        val tenSections = (1..10).map { s -> SectionTime(s, "08:00", "08:45") }
        val maxConfig10 = if (tenSections.isNotEmpty()) maxOf(tenSections.size, tenSections.maxOf { it.section }) else 12
        val maxCourse13 = 13
        val totalExtended = maxOf(1, maxOf(maxConfig10, maxCourse13))
        assertEquals(13, totalExtended)

        // 4. Fallback when section times are empty
        val emptySections = emptyList<SectionTime>()
        val maxConfigEmpty = if (emptySections.isNotEmpty()) maxOf(emptySections.size, emptySections.maxOf { it.section }) else 12
        val totalFallback = maxOf(1, maxOf(maxConfigEmpty, maxCourse0))
        assertEquals(12, totalFallback)
    }
}

