package org.classflow

import org.classflow.data.local.ClassFlowBackupManager
import org.classflow.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ClassFlowBackupManagerTest {

    @Test
    fun testExportAndImportClassFlowZipPackage() {
        val semester = Semester(
            id = "sem_2026_fall",
            name = "2026年秋季学期",
            startDate = "2026-09-07",
            endDate = "2027-01-17",
            totalWeeks = 19,
            isCurrent = true
        )

        val course = Course(
            id = "c_math_101",
            semesterId = "sem_2026_fall",
            name = "高等数学(A)上",
            teacher = "陈讲师",
            color = "#4F46E5",
            schedules = listOf(
                CourseSchedule(
                    id = "s_math_mon",
                    courseId = "c_math_101",
                    dayOfWeek = 1,
                    startSection = 1,
                    endSection = 2,
                    classroom = "第三教学楼 101",
                    weekPattern = WeekPatternType.ALL,
                    customWeeks = (1..19).toList()
                )
            )
        )

        val settings = mapOf(
            "themeMode" to "dark",
            "showWeekends" to "true",
            "cardOpacity" to "0.9"
        )

        val zipBytes = ClassFlowBackupManager.exportPackageBytes(
            semesters = listOf(semester),
            courses = listOf(course),
            exceptions = emptyList(),
            settings = settings
        )

        assertTrue("ZIP bytes should not be empty", zipBytes.isNotEmpty())

        // Verify ZIP entries directly
        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue("Must contain manifest.json", entryNames.contains("manifest.json"))
        assertTrue("Must contain semesters.json", entryNames.contains("semesters.json"))
        assertTrue("Must contain courses.json", entryNames.contains("courses.json"))
        assertTrue("Must contain exceptions.json", entryNames.contains("exceptions.json"))
        assertTrue("Must contain settings.json", entryNames.contains("settings.json"))

        // Now import through ClassFlowBackupManager
        val result = ClassFlowBackupManager.importPackageZip(ByteArrayInputStream(zipBytes))
        assertEquals(1, result.semesters.size)
        assertEquals("2026年秋季学期", result.semesters.first().name)
        assertEquals(19, result.semesters.first().totalWeeks)

        assertEquals(1, result.courses.size)
        val importedCourse = result.courses.first()
        assertEquals("c_math_101", importedCourse.id)
        assertEquals("高等数学(A)上", importedCourse.name)
        assertEquals("陈讲师", importedCourse.teacher)
        assertEquals(1, importedCourse.schedules.size)
        assertEquals("第三教学楼 101", importedCourse.schedules.first().classroom)

        assertEquals("dark", result.settings["themeMode"])
        assertEquals("true", result.settings["showWeekends"])
    }

    @Test
    fun testGenerateAndImportICSCalendar() {
        val occurrence = TimelineItem(
            id = "occ_cs_1",
            courseId = "c_cs_1",
            scheduleId = "s_cs_1",
            title = "数据结构与算法",
            location = "信息楼 205",
            teacher = "张教授",
            date = "2026-10-12",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            startTime = "10:00",
            endTime = "11:35",
            weekNumber = 6,
            color = "#10B981"
        )

        val scheduleEvent = CalendarEvent(
            id = "event_team_sync",
            title = "毕业设计小组会",
            startTime = "14:00",
            endTime = "15:30",
            location = "研讨室 302",
            description = "讨论开题报告",
            color = "#F59E0B",
            customDaysOfWeek = listOf(1)
        )

        val icsText = ClassFlowBackupManager.generateICSCalendar(
            occurrences = listOf(occurrence),
            schedules = listOf(scheduleEvent)
        )

        assertTrue(icsText.startsWith("BEGIN:VCALENDAR"))
        assertTrue(icsText.contains("SUMMARY:数据结构与算法"))
        assertTrue(icsText.contains("LOCATION:信息楼 205"))
        assertTrue(icsText.contains("SUMMARY:毕业设计小组会"))
        assertTrue(icsText.contains("LOCATION:研讨室 302"))
        assertTrue(icsText.contains("DESCRIPTION:讨论开题报告"))
        assertTrue(icsText.trim().endsWith("END:VCALENDAR"))

        // Test parsing generated ICS
        val importResult = ClassFlowBackupManager.importICS(ByteArrayInputStream(icsText.toByteArray(Charsets.UTF_8)))
        val allTitles = (importResult.courses.map { it.name } + importResult.schedules.map { it.title })
        assertTrue("Should parse 数据结构与算法", allTitles.contains("数据结构与算法"))
        assertTrue("Should parse 毕业设计小组会", allTitles.contains("毕业设计小组会"))
    }
}
