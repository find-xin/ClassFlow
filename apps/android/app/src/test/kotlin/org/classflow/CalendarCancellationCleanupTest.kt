package org.classflow

import org.classflow.data.local.*
import org.classflow.data.model.*
import org.classflow.sync.FullSyncManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class CalendarCancellationCleanupTest {
    private val sections = listOf(SectionTime(1, "08:00", "08:45"), SectionTime(2, "09:00", "10:00"))
    private fun event() = CalendarEvent(id = "series", title = "Series", startTime = "2026-11-02T09:00:00", endTime = "2026-11-02T10:00:00", startDate = "2026-11-02", frequency = "daily", endRepeatType = "count", endRepeatCount = 3)
    private fun project(event: CalendarEvent, exceptions: List<ScheduleException> = emptyList(), week: Int = 1) = CalendarEventProjection.project(event, "2026-11-02", week, sections, exceptions)

    @Test fun migrationAndCleanupNeverReviveAndCountDoesNotExtend() {
        val series = event()
        val cancel = ScheduleException(scheduleId = series.id, originalDate = "2026-11-03", type = ExceptionType.CANCEL, reason = "单次删除")
        val migration = CalendarCancellationCleanup.preview(listOf(series), listOf(cancel, cancel.copy(id = "duplicate")), emptySet(), removeRedundant = false)
        assertTrue(series.excludedDates.isEmpty())
        assertEquals(1, migration.duplicateRecords)
        assertEquals(2, migration.exceptions.size)
        val cleanup = CalendarCancellationCleanup.preview(migration.events, migration.exceptions, emptySet())
        assertEquals(0, cleanup.migratedDates)
        assertEquals(2, cleanup.removed.size)
        assertEquals(project(series, listOf(cancel)), project(cleanup.events.single(), cleanup.exceptions))
        assertEquals(listOf("2026-11-02", "2026-11-04"), project(cleanup.events.single()).map { it.date })
        val reopened = CourseDataManager.deserializeSchedules(CourseDataManager.serializeSchedules(cleanup.events))
        assertEquals(cleanup.events, reopened)
        assertEquals(0, CalendarCancellationCleanup.preview(reopened, cleanup.exceptions, emptySet()).changedSeries)
    }
    @Test fun everyRecurrenceKeepsOtherDatesAndCaps() {
        for (frequency in listOf("daily", "weekly", "workdays", "customDays", "monthly", "biweekly", "weekends", "customWeeks")) {
            val series = event().copy(frequency = frequency)
            val before = (1..14).flatMap { project(series, week = it) }
            assertEquals(3, before.size)
            val cancel = ScheduleException(scheduleId = series.id, originalDate = before[1].date, type = ExceptionType.CANCEL, reason = "单次删除")
            val plan = CalendarCancellationCleanup.preview(listOf(series), listOf(cancel), emptySet())
            assertEquals(before.filterNot { it.date == cancel.originalDate }, (1..14).flatMap { project(plan.events.single(), week = it) })
        }
    }
    @Test fun protectsCourseMovesConflictsInvalidAndUnknownRecords() {
        val series = event()
        val records = listOf(
            ScheduleException(scheduleId = "course-slot", originalDate = "2026-11-03", type = ExceptionType.CANCEL),
            ScheduleException(scheduleId = "orphan", originalDate = "2026-11-03", type = ExceptionType.CANCEL),
            ScheduleException(scheduleId = "series", originalDate = "2026-11-03", type = ExceptionType.RESCHEDULE, targetDate = "2026-11-05"),
            ScheduleException(scheduleId = "series", originalDate = "2026-11-03", type = ExceptionType.CANCEL),
            ScheduleException(scheduleId = "series", originalDate = "2026-02-30", type = ExceptionType.CANCEL))
        val plan = CalendarCancellationCleanup.preview(listOf(series), records, setOf("course-slot"))
        assertEquals(records, plan.exceptions); assertEquals(listOf(series), plan.events)
        val unknown = CourseDataManager.deserializeExceptions("""[{"id":"unknown","scheduleId":"series","originalDate":"2026-11-04","type":"MAKEUP"}]""")
        assertEquals("MAKEUP", unknown.single().unrecognizedType)
        assertTrue(CalendarCancellationCleanup.preview(listOf(series), unknown, emptySet()).removed.isEmpty())
        assertEquals(unknown, CourseDataManager.deserializeExceptions(CourseDataManager.serializeExceptions(unknown)))
    }
    @Test fun multiDayOriginAndSelectedSeries() {
        val series = event().copy(startTime = "2026-11-01T09:00:00", startDate = "2026-11-01", endTime = "2026-11-03T10:00:00", frequency = "weekly", endRepeatCount = 1)
        assertEquals(listOf("2026-11-01", "2026-11-01"), project(series).map { it.recurrenceOriginDate })
        val cancel = ScheduleException(scheduleId = series.id, originalDate = "2026-11-01", type = ExceptionType.CANCEL)
        assertTrue(CalendarCancellationCleanup.preview(listOf(series), listOf(cancel), emptySet(), setOf("other")).removed.isEmpty())
        assertTrue(project(CalendarCancellationCleanup.preview(listOf(series), listOf(cancel), emptySet()).events.single()).isEmpty())
    }
    @Test fun archiveLegacyAndSharedSwiftFixture() {
        val json = File("../../../packages/fixtures/sample_calendar_exclusions.json").readText()
        val series = CourseDataManager.deserializeSchedules("[$json]").single()
        assertEquals(listOf("2026-11-02", "2026-11-04"), project(series).map { it.date })
        val legacy = JSONObject(json).apply { remove("excludedDates") }
        assertTrue(CourseDataManager.deserializeSchedules("[$legacy]").single().excludedDates.isEmpty())
        val cancel = ScheduleException(scheduleId = series.id, originalDate = "2026-11-03", type = ExceptionType.CANCEL)
        val bytes = ClassFlowBackupManager.exportPackageBytes(emptyList(), emptyList(), listOf(cancel), schedules = listOf(series))
        val result = ClassFlowBackupManager.importPackageZip(ByteArrayInputStream(bytes))
        assertEquals(listOf(series), result.schedules); assertEquals(listOf(cancel), result.exceptions)
        val ics = ClassFlowBackupManager.generateICSCalendar(project(series), listOf(series))
        assertFalse(ics.contains("DTSTART:20261103")); assertTrue(ics.contains("DTSTART:20261104"))
        val importedICS = ClassFlowBackupManager.importICS(ByteArrayInputStream(ics.toByteArray()))
        assertEquals(listOf(series), importedICS.schedules); assertTrue(importedICS.courses.isEmpty())
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> var entry = zip.nextEntry; while (entry != null) { files[entry.name] = zip.readBytes(); entry = zip.nextEntry } }
        files["calendarEvents.json"] = "[]".toByteArray()
        fun archive(): ByteArray { val stream = ByteArrayOutputStream(); ZipOutputStream(stream).use { out -> files.forEach { (name, data) -> out.putNextEntry(ZipEntry(name)); out.write(data); out.closeEntry() } }; return stream.toByteArray() }
        assertThrows(IllegalArgumentException::class.java) { ClassFlowBackupManager.importPackageZip(ByteArrayInputStream(archive())) }
        val manifest = JSONObject(String(files.getValue("manifest.json"))).put("formatVersion", 1)
        val payload = files.getValue("semesters.json") + files.getValue("courses.json") + files.getValue("exceptions.json")
        manifest.put("checksum", MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) })
        files["manifest.json"] = manifest.toString().toByteArray(); files.remove("calendarEvents.json")
        assertTrue(ClassFlowBackupManager.importPackageZip(ByteArrayInputStream(archive())).schedules.isEmpty())
    }
    @Test fun peerMergeUnionsExclusionsAndDoesNotConflateDifferentIds() {
        val series = event().copy(excludedDates = listOf("2026-11-03"))
        val local = mutableListOf(series)
        fun merge(peer: CalendarEvent) {
            FullSyncManager.mergePeerSnapshot(JSONObject().put("calendarEvents", JSONArray(CourseDataManager.serializeSchedules(listOf(peer)))),
                mutableListOf(), local, mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
        }
        merge(series.copy(excludedDates = listOf("2026-11-04")))
        merge(series.copy(excludedDates = emptyList()))
        assertEquals(listOf("2026-11-03", "2026-11-04"), local.single().excludedDates)
        merge(series.copy(id = "other", excludedDates = emptyList()))
        assertEquals(2, local.size)
    }
    @Test fun batchCleanupKeepsSeriesIdsWithoutIndependentReplacements() {
        val events = (0 until 1000).map { event().copy(id = "series-$it") }
        val records = events.flatMap { event -> listOf(
            ScheduleException(scheduleId = event.id, originalDate = "2026-11-03", type = ExceptionType.CANCEL),
            ScheduleException(scheduleId = event.id, originalDate = "2026-11-03", type = ExceptionType.CANCEL)) }
        val plan = CalendarCancellationCleanup.preview(events, records, emptySet())
        assertEquals(events.map { it.id }, plan.events.map { it.id })
        assertEquals(1000, plan.migratedDates); assertEquals(1000, plan.duplicateRecords); assertEquals(2000, plan.removed.size)
        assertEquals(2000, plan.events.flatMap { project(it) }.size)
    }

}
