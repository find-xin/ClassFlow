package org.classflow

import org.classflow.data.local.CourseDataManager
import org.classflow.data.model.*
import org.junit.Assert.*
import org.junit.Test

class CourseDataManagerTest {

    @Test
    fun testCourseSerializationAndDeserialization() {
        val originalCourses = listOf(
            Course(
                id = "c_algo",
                semesterId = "sem_1",
                name = "高级算法与数据结构",
                teacher = "王教授",
                color = "#4F46E5",
                notes = "考试重点在图论和动态规划",
                schedules = listOf(
                    CourseSchedule(
                        id = "sched_algo_1",
                        courseId = "c_algo",
                        dayOfWeek = 6, // Saturday
                        startSection = 3,
                        endSection = 4,
                        classroom = "计网楼 201",
                        weekPattern = WeekPatternType.CUSTOM,
                        customWeeks = listOf(1, 3, 5, 7, 9)
                    )
                )
            )
        )

        val json = CourseDataManager.serializeCourses(originalCourses)
        assertNotNull(json)
        assertTrue(json.contains("高级算法与数据结构"))
        assertTrue(json.contains("计网楼 201"))

        val restoredCourses = CourseDataManager.deserializeCourses(json)
        assertEquals(1, restoredCourses.size)
        val c = restoredCourses.first()
        assertEquals("c_algo", c.id)
        assertEquals("高级算法与数据结构", c.name)
        assertEquals("王教授", c.teacher)
        assertEquals(1, c.schedules.size)
        val s = c.schedules.first()
        assertEquals(6, s.dayOfWeek)
        assertEquals(3, s.startSection)
        assertEquals(4, s.endSection)
        assertEquals(listOf(1, 3, 5, 7, 9), s.customWeeks)
    }

    @Test
    fun testSemesterSerializationAndDeserialization() {
        val originalSemesters = listOf(
            Semester(
                id = "sem_2026_fall",
                name = "2026-2027 秋季学期",
                startDate = "2026-09-01",
                endDate = "2027-01-20",
                totalWeeks = 16,
                isCurrent = true,
                timeZone = "Asia/Shanghai"
            ),
            Semester(
                id = "sem_2027_spring",
                name = "2026-2027 春季学期",
                startDate = "2027-02-25",
                endDate = "2027-07-10",
                totalWeeks = 18,
                isCurrent = false,
                timeZone = "Asia/Shanghai"
            )
        )

        val json = CourseDataManager.serializeSemesters(originalSemesters)
        assertNotNull(json)
        assertTrue(json.contains("2026-2027 秋季学期"))

        val restored = CourseDataManager.deserializeSemesters(json)
        assertEquals(2, restored.size)
        assertEquals("sem_2026_fall", restored[0].id)
        assertEquals("2026-2027 秋季学期", restored[0].name)
        assertTrue(restored[0].isCurrent)
        assertEquals(16, restored[0].totalWeeks)

        assertEquals("sem_2027_spring", restored[1].id)
        assertFalse(restored[1].isCurrent)
        assertEquals(18, restored[1].totalWeeks)
    }

    @Test
    fun testFocusRecordSerializationAndDeserialization() {
        val originalRecords = listOf(
            FocusRecord(
                id = "fr_math",
                taskTitle = "高等数学复习",
                courseName = "高等数学",
                durationMinutes = 45,
                completedAt = "今日 14:30"
            ),
            FocusRecord(
                id = "fr_free",
                taskTitle = "自由专注",
                courseName = null,
                durationMinutes = 25,
                completedAt = "今日 16:00"
            )
        )

        val json = CourseDataManager.serializeFocusRecords(originalRecords)
        assertNotNull(json)
        assertTrue(json.contains("高等数学复习"))
        assertTrue(json.contains("自由专注"))

        val restored = CourseDataManager.deserializeFocusRecords(json)
        assertEquals(2, restored.size)
        assertEquals("fr_math", restored[0].id)
        assertEquals("高等数学复习", restored[0].taskTitle)
        assertEquals("高等数学", restored[0].courseName)
        assertEquals(45, restored[0].durationMinutes)

        assertEquals("fr_free", restored[1].id)
        assertNull(restored[1].courseName)
        assertEquals(25, restored[1].durationMinutes)
    }

    @Test
    fun testDailyMinutesSerializationAndDeserialization() {
        val originalMap = mapOf(
            "2026-10-16" to 50,
            "2026-10-15" to 45,
            "2026-10-14" to 90
        )

        val json = CourseDataManager.serializeDailyMinutes(originalMap)
        assertNotNull(json)

        val restored = CourseDataManager.deserializeDailyMinutes(json)
        assertEquals(3, restored.size)
        assertEquals(50, restored["2026-10-16"])
        assertEquals(45, restored["2026-10-15"])
        assertEquals(90, restored["2026-10-14"])
    }

    @Test
    fun testScheduleSerializationAndDeserialization() {
        val originalEvents = listOf(
            CalendarEvent(
                id = "evt_lab",
                title = "实验室组会",
                startTime = "15:00",
                endTime = "17:00",
                location = "工科实验大楼 402",
                color = "#8E24AA",
                frequency = "每周",
                customDaysOfWeek = listOf(2, 4),
                customWeeks = listOf(1, 2, 3, 4, 5, 6, 7, 8)
            )
        )

        val json = CourseDataManager.serializeSchedules(originalEvents)
        val restored = CourseDataManager.deserializeSchedules(json)
        assertEquals(1, restored.size)
        val ev = restored.first()
        assertEquals("evt_lab", ev.id)
        assertEquals("实验室组会", ev.title)
        assertEquals("15:00", ev.startTime)
        assertEquals("17:00", ev.endTime)
        assertEquals(listOf(2, 4), ev.customDaysOfWeek)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), ev.customWeeks)
    }

    @Test
    fun testScheduleExceptionSerializationAndDeserialization() {
        val originalExceptions = listOf(
            ScheduleException(
                id = "ex_cancel_1",
                scheduleId = "sched_1",
                originalDate = "2026-10-15",
                type = ExceptionType.CANCEL,
                reason = "国庆节停课"
            ),
            ScheduleException(
                id = "ex_room_1",
                scheduleId = "sched_2",
                originalDate = "2026-10-16",
                type = ExceptionType.ROOM_CHANGE,
                targetClassroom = "阶梯教室 A102",
                reason = "原教室维修"
            )
        )

        val json = CourseDataManager.serializeExceptions(originalExceptions)
        assertNotNull(json)
        assertTrue(json.contains("ex_cancel_1"))
        assertTrue(json.contains("阶梯教室 A102"))

        val restored = CourseDataManager.deserializeExceptions(json)
        assertEquals(2, restored.size)
        assertEquals("ex_cancel_1", restored[0].id)
        assertEquals(ExceptionType.CANCEL, restored[0].type)
        assertEquals("2026-10-15", restored[0].originalDate)
        assertEquals("国庆节停课", restored[0].reason)

        assertEquals("ex_room_1", restored[1].id)
        assertEquals(ExceptionType.ROOM_CHANGE, restored[1].type)
        assertEquals("阶梯教室 A102", restored[1].targetClassroom)
        assertEquals("原教室维修", restored[1].reason)
    }

    @Test
    fun testTombstoneSerializationAndGarbageCollection() {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        val tombstones = listOf(
            SyncTombstone(entityId = "c_fresh", entityType = "course", deletedAt = now - dayMs),
            SyncTombstone(entityId = "c_expired", entityType = "course", deletedAt = now - (95 * dayMs))
        )

        val json = CourseDataManager.serializeTombstones(tombstones)
        assertTrue(json.contains("c_fresh"))
        assertTrue(json.contains("c_expired"))

        val restored = CourseDataManager.deserializeTombstones(json)
        assertEquals(2, restored.size)

        // GC test
        val gced = CourseDataManager.garbageCollectTombstones(restored, referenceTime = now)
        assertEquals(1, gced.size)
        assertEquals("c_fresh", gced[0].entityId)

        // Capacity cap test
        val many = (0 until 1100).map { i ->
            SyncTombstone(entityId = "item_$i", entityType = "course", deletedAt = now - (i * 1000L))
        }
        val capped = CourseDataManager.garbageCollectTombstones(many, referenceTime = now)
        assertEquals(1000, capped.size)
        assertEquals("item_0", capped[0].entityId)
    }

    @Test
    fun testStudyTaskRecurrenceSerialization() {
        val originalTasks = listOf(
            StudyTask(
                id = "task_recur_1",
                title = "背英语单词",
                courseName = "大学英语",
                targetMinutes = 45,
                frequency = "每周",
                startDate = "2026-10-18",
                dueDate = "2026-12-31",
                customDaysOfWeek = listOf(1, 3, 5),
                customWeeks = listOf(1, 2, 3, 4, 5, 6, 7, 8),
                endRepeatType = "byDate",
                endRepeatUntil = "2026-12-31",
                endRepeatCount = 10
            )
        )

        val json = CourseDataManager.serializeStudyTasks(originalTasks)
        assertNotNull(json)
        assertTrue(json.contains("task_recur_1"))
        assertTrue(json.contains("customDaysOfWeek"))
        assertTrue(json.contains("customWeeks"))
        assertTrue(json.contains("byDate"))

        val restored = CourseDataManager.deserializeStudyTasks(json)
        assertEquals(1, restored.size)
        val t = restored[0]
        assertEquals("task_recur_1", t.id)
        assertEquals("背英语单词", t.title)
        assertEquals("每周", t.frequency)
        assertEquals(listOf(1, 3, 5), t.customDaysOfWeek)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), t.customWeeks)
        assertEquals("byDate", t.endRepeatType)
        assertEquals("2026-12-31", t.endRepeatUntil)
        assertEquals(10, t.endRepeatCount)
    }
}
