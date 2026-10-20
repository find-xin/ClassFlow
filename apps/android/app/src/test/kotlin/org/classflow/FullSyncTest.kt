package org.classflow

import org.classflow.data.model.*
import org.classflow.sync.FullSyncBundle
import org.classflow.sync.FullSyncManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FullSyncTest {

    @Test
    fun testFullSyncSerializationAndDeserialization() {
        val course = Course(
            id = "c_1",
            semesterId = "sem_1",
            name = "计算机操作系统",
            teacher = "张教授",
            color = "#4285F4",
            schedules = listOf(
                CourseSchedule(
                    id = "s_1",
                    dayOfWeek = 2,
                    startSection = 1,
                    endSection = 2,
                    classroom = "信息大楼 402",
                    weekPattern = WeekPatternType.ALL
                )
            )
        )

        val schedule = CalendarEvent(
            id = "sched_1",
            title = "学术研讨会",
            startTime = "14:00",
            endTime = "16:00",
            location = "学术交流中心",
            frequency = "每周"
        )

        val todo = CalendarEvent(
            id = "todo_1",
            title = "完成期中作业",
            isTask = true,
            isCompleted = false
        )

        val studyTask = StudyTask(
            id = "st_1",
            title = "阅读内核源码",
            courseName = "操作系统",
            targetMinutes = 60,
            completedMinutes = 30
        )

        val focusRecord = FocusRecord(
            id = "fr_1",
            taskTitle = "阅读内核源码",
            courseName = "操作系统",
            durationMinutes = 30,
            completedAt = "2026-10-16 10:30"
        )

        val settings = AppSettings(
            themeMode = "dark",
            enableStudyModule = true,
            cardOpacity = 0.85f,
            wallpaperPreset = "anime_blue",
            syncInterval = "30m",
            autoSyncEnabled = true
        )

        val bundle = FullSyncBundle(
            courses = listOf(course),
            schedules = listOf(schedule),
            todos = listOf(todo),
            studyTasks = listOf(studyTask),
            focusRecords = listOf(focusRecord),
            settings = settings
        )

        // 1. Serialize
        val json = FullSyncManager.serializeBundle(bundle)
        assertNotNull(json)
        assertTrue(json.contains("计算机操作系统"))
        assertTrue(json.contains("学术研讨会"))
        assertTrue(json.contains("完成期中作业"))
        assertTrue(json.contains("阅读内核源码"))
        assertTrue(json.contains("anime_blue"))
        assertTrue(json.contains("30m"))

        // 2. Deserialize
        val deserialized = FullSyncManager.deserializeBundle(json)
        assertEquals(1, deserialized.courses.size)
        assertEquals("计算机操作系统", deserialized.courses[0].name)
        assertEquals("信息大楼 402", deserialized.courses[0].schedules[0].classroom)

        assertEquals(1, deserialized.schedules.size)
        assertEquals("学术研讨会", deserialized.schedules[0].title)

        assertEquals(1, deserialized.todos.size)
        assertEquals("完成期中作业", deserialized.todos[0].title)

        assertEquals(1, deserialized.studyTasks.size)
        assertEquals(60, deserialized.studyTasks[0].targetMinutes)

        assertEquals(1, deserialized.focusRecords.size)
        assertEquals(30, deserialized.focusRecords[0].durationMinutes)

        assertEquals("dark", deserialized.settings.themeMode)
        assertEquals("anime_blue", deserialized.settings.wallpaperPreset)
        assertEquals("30m", deserialized.settings.syncInterval)
        assertTrue(deserialized.settings.autoSyncEnabled)
    }

    @Test
    fun testEmptyBundleHandling() {
        val emptyBundle = FullSyncBundle()
        val json = FullSyncManager.serializeBundle(emptyBundle)
        val deserialized = FullSyncManager.deserializeBundle(json)

        assertTrue(deserialized.courses.isEmpty())
        assertTrue(deserialized.schedules.isEmpty())
        assertTrue(deserialized.todos.isEmpty())
        assertTrue(deserialized.studyTasks.isEmpty())
        assertTrue(deserialized.focusRecords.isEmpty())
        assertEquals("system", deserialized.settings.themeMode)
    }

    @Test
    fun testPeerMergeCourseBlockedByTombstoneWhenAllCoursesDeleted() {
        // Local state: courses were deleted (empty), but schedule "晨读早自习" remains
        val localCourses = mutableListOf<Course>()
        val localSchedules = mutableListOf(
            CalendarEvent(id = "sched_morning", title = "晨读早自习", startTime = "07:00", endTime = "07:40")
        )
        val localExceptions = mutableListOf<ScheduleException>()
        val localSemesters = mutableListOf<Semester>()
        val localFocusRecords = mutableListOf<FocusRecord>()
        val localStudyTasks = mutableListOf<StudyTask>()

        // Local has a tombstone for peer course c_oop because it was deleted locally
        val localTombstones = mutableListOf(
            SyncTombstone(entityId = "c_oop", entityType = "course", deletedAt = System.currentTimeMillis() - 1000)
        )

        // Peer remote snapshot contains course c_oop
        val peerCourseJson = JSONObject().apply {
            put("id", "c_oop")
            put("semesterId", "sem_1")
            put("name", "面向对象程序设计")
            put("schedules", JSONArray())
        }
        val peerSnapJson = JSONObject().apply {
            put("courses", JSONArray().put(peerCourseJson))
        }

        val downloaded = FullSyncManager.mergePeerSnapshot(
            peerSnapJson = peerSnapJson,
            localCourses = localCourses,
            localSchedules = localSchedules,
            localExceptions = localExceptions,
            localSemesters = localSemesters,
            localFocusRecords = localFocusRecords,
            localStudyTasks = localStudyTasks,
            localTombstones = localTombstones
        )

        // Verifications:
        // 1. The deleted course c_oop MUST NOT be resurrected!
        assertTrue(localCourses.isEmpty())
        assertEquals(0, downloaded)

        // 2. The tombstone for c_oop MUST be preserved to propagate deletion across peers
        assertTrue(localTombstones.any { it.entityId == "c_oop" })

        // 3. The existing schedule "晨读早自习" remains intact
        assertEquals(1, localSchedules.size)
        assertEquals("sched_morning", localSchedules[0].id)
    }

    @Test
    fun testFreshDeviceWithNoTombstonesPullsPeerCoursesSuccessfully() {
        // Fresh install: empty courses and empty tombstones
        val localCourses = mutableListOf<Course>()
        val localSchedules = mutableListOf<CalendarEvent>()
        val localExceptions = mutableListOf<ScheduleException>()
        val localSemesters = mutableListOf<Semester>()
        val localFocusRecords = mutableListOf<FocusRecord>()
        val localStudyTasks = mutableListOf<StudyTask>()
        val localTombstones = mutableListOf<SyncTombstone>()

        // Peer remote snapshot contains course c_oop
        val peerCourseJson = JSONObject().apply {
            put("id", "c_oop")
            put("semesterId", "sem_1")
            put("name", "面向对象程序设计")
            put("schedules", JSONArray())
        }
        val peerSnapJson = JSONObject().apply {
            put("courses", JSONArray().put(peerCourseJson))
        }

        val downloaded = FullSyncManager.mergePeerSnapshot(
            peerSnapJson = peerSnapJson,
            localCourses = localCourses,
            localSchedules = localSchedules,
            localExceptions = localExceptions,
            localSemesters = localSemesters,
            localFocusRecords = localFocusRecords,
            localStudyTasks = localStudyTasks,
            localTombstones = localTombstones
        )

        // Fresh install cleanly downloads peer courses
        assertEquals(1, localCourses.size)
        assertEquals("c_oop", localCourses[0].id)
        assertEquals("面向对象程序设计", localCourses[0].name)
        assertEquals(1, downloaded)
        assertTrue(localTombstones.isEmpty())
    }
}
