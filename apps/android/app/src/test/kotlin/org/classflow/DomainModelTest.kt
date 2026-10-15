package org.classflow

import org.classflow.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DomainModelTest {

    @Test
    fun testSemesterCreationAndSections() {
        val semester = Semester(
            name = "2026年秋季学期",
            startDate = "2026-09-07",
            endDate = "2027-01-10",
            totalWeeks = 16,
            isCurrent = true,
            sections = listOf(
                SectionTime(1, "08:00", "08:45"),
                SectionTime(2, "08:50", "09:35")
            )
        )

        assertEquals("2026年秋季学期", semester.name)
        assertEquals(2, semester.sections.size)
        assertEquals("08:00", semester.sections[0].startTime)
    }

    @Test
    fun testCourseAndScheduleAssociations() {
        val schedule = CourseSchedule(
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            classroom = "东上 101",
            weekPattern = WeekPatternType.ALL
        )

        val course = Course(
            semesterId = "sem_1",
            name = "高等数学",
            teacher = "张教授",
            color = "#4A90E2",
            schedules = listOf(schedule)
        )

        assertEquals(1, course.schedules.size)
        assertEquals("东上 101", course.schedules[0].classroom)
        assertEquals(WeekPatternType.ALL, course.schedules[0].weekPattern)
    }

    @Test
    fun testScheduleExceptionProperties() {
        val exception = ScheduleException(
            scheduleId = "sched_1",
            originalDate = "2026-09-08",
            type = ExceptionType.RESCHEDULE,
            targetDate = "2026-09-09",
            targetDayOfWeek = 3,
            targetStartSection = 3,
            targetEndSection = 4,
            targetClassroom = "西教 204"
        )

        assertEquals(ExceptionType.RESCHEDULE, exception.type)
        assertEquals("西教 204", exception.targetClassroom)
        assertEquals(3, exception.targetDayOfWeek)
    }

    @Test
    fun testCalendarEventAndTaskProperties() {
        val event = CalendarEvent(
            title = "系学术前沿讲座",
            startTime = "14:00",
            endTime = "15:30",
            location = "报告厅 101",
            frequency = "每周",
            isTask = false
        )

        assertEquals("系学术前沿讲座", event.title)
        assertEquals("14:00", event.startTime)
        assertEquals("每周", event.frequency)
        assertEquals(false, event.isTask)

        val task = CalendarEvent(
            title = "完成高数复习",
            isTask = true,
            isCompleted = false
        )
        assertEquals(true, task.isTask)
        assertEquals(false, task.isCompleted)
    }

    @Test
    fun testStudyTaskAndAppSettingsProperties() {
        val studyTask = StudyTask(
            title = "大学英语六级听力",
            courseName = "大学英语",
            targetMinutes = 45,
            completedMinutes = 30
        )

        assertEquals("大学英语六级听力", studyTask.title)
        assertEquals(45, studyTask.targetMinutes)
        assertEquals(30, studyTask.completedMinutes)

        val settings = AppSettings(
            themeMode = "dark",
            enableStudyModule = false,
            showWeekends = false
        )
        assertEquals("dark", settings.themeMode)
        assertEquals(false, settings.enableStudyModule)
        assertEquals(false, settings.showWeekends)
    }
}

