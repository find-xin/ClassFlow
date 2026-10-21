package org.classflow

import org.classflow.data.local.CalendarEventProjection
import org.classflow.data.local.CourseDataManager
import org.classflow.data.model.*
import org.classflow.sync.FullSyncBundle
import org.classflow.sync.FullSyncManager
import org.junit.Assert.*
import org.junit.Test

class CompleteEditingParityTest {
    private val sections = listOf(SectionTime(1, "08:00", "08:45"), SectionTime(2, "08:50", "09:35"))
    @Test fun countAndSectionTimeFollowTimetable() {
        val event = CalendarEvent(title = "Count", startDate = "2026-10-19", startTime = "2026-10-19T14:00:00", endTime = "2026-10-19T15:00:00", frequency = "daily", endRepeatType = "count", endRepeatCount = 3, timeMode = "sections", startSection = 1, endSection = 2)
        val items = CalendarEventProjection.project(event, "2026-10-19", 1, sections, emptyList())
        assertEquals(3, items.size)
        assertEquals("08:00", items.first().startTime)
        assertTrue(CalendarEventProjection.project(event, "2026-10-19", 2, sections, emptyList()).isEmpty())
    }
    @Test fun crossDayAndMonthlyProjection() {
        val event = CalendarEvent(title = "Trip", startDate = "2026-10-20", endDate = "2026-10-22", startTime = "2026-10-20T16:00:00", endTime = "2026-10-22T10:00:00")
        val items = CalendarEventProjection.project(event, "2026-10-19", 1, sections, emptyList())
        assertEquals(3, items.size)
        assertEquals("00:00", items[1].startTime)
        assertEquals("10:00", items.last().endTime)
        val monthly = event.copy(startDate = "2026-10-19", endDate = null, startTime = "2026-10-19T09:00:00", endTime = "2026-10-19T10:00:00", frequency = "monthly", endRepeatType = "count", endRepeatCount = 2)
        assertEquals(1, CalendarEventProjection.project(monthly, "2026-10-19", 5, sections, emptyList()).size)
    }
    @Test fun completeFieldsSurvivePersistence() {
        val event = CalendarEvent(title = "Test", timeMode = "sections", alertMinutes = 15, intervalWeeks = 2, customDaysOfWeek = listOf(1,3), customWeeks = listOf(2,4), frequency = "customWeeks", endRepeatType = "count", endRepeatCount = 4, startSection = 1, endSection = 2, startDate = "2026-10-19")
        assertEquals(event, CourseDataManager.deserializeSchedules(CourseDataManager.serializeSchedules(listOf(event))).single())
        val task = StudyTask(title = "Task", courseId = "course", targetMinutes = 45, dueDate = "2026-10-21 18:00", frequency = "daily", notes = "Notes", endRepeatCount = 3)
        assertEquals(task, CourseDataManager.deserializeStudyTasks(CourseDataManager.serializeStudyTasks(listOf(task))).single())
        val todo = event.copy(title = "Todo", isTask = true, isCompleted = true, courseId = "course", dueDate = "2026-10-21 18:00")
        val snapshot = FullSyncManager.deserializeBundle(FullSyncManager.serializeBundle(FullSyncBundle(schedules = listOf(event), todos = listOf(todo), studyTasks = listOf(task))))
        assertEquals(event, snapshot.schedules.single())
        assertEquals(todo, snapshot.todos.single())
        assertEquals(task, snapshot.studyTasks.single())
    }
}
