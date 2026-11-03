package org.classflow.data.local

import android.content.Context
import android.net.Uri
import org.classflow.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Result data container from unpacking a .classflow package or .ics calendar.
 */
data class ClassFlowImportResult(
    val semesters: List<Semester> = emptyList(),
    val courses: List<Course> = emptyList(),
    val schedules: List<CalendarEvent> = emptyList(),
    val exceptions: List<ScheduleException> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

/**
 * Android parity service for packaging and unpacking .classflow PKZip archives
 * and RFC 5545 .ics calendars, matching packages/core-engine ClassFlowBackup.swift.
 */
object ClassFlowBackupManager {

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder()
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun getIso8601Timestamp(): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date())
    }

    // MARK: - Export .classflow ZIP Archive

    fun exportPackageBytes(
        semesters: List<Semester>,
        courses: List<Course>,
        exceptions: List<ScheduleException> = emptyList(),
        settings: Map<String, String> = emptyMap(),
        schedules: List<CalendarEvent> = emptyList()
    ): ByteArray {
        val semestersJson = CourseDataManager.serializeSemesters(semesters)
        val coursesJson = CourseDataManager.serializeCourses(courses)

        // Exceptions JSON
        val exceptionsJson = CourseDataManager.serializeExceptions(exceptions)

        // Settings JSON
        val setObj = JSONObject()
        settings.forEach { (k, v) -> setObj.put(k, v) }
        val settingsJson = setObj.toString()

        val semBytes = semestersJson.toByteArray(StandardCharsets.UTF_8)
        val crsBytes = coursesJson.toByteArray(StandardCharsets.UTF_8)
        val excBytes = exceptionsJson.toByteArray(StandardCharsets.UTF_8)
        val setBytes = settingsJson.toByteArray(StandardCharsets.UTF_8)
        val eventBytes = CourseDataManager.serializeSchedules(schedules).toByteArray(StandardCharsets.UTF_8)

        // Concatenate bytes to compute SHA-256 checksum matching ClassFlowBackup.swift
        val checksumHex = sha256Hex(semBytes + crsBytes + excBytes + eventBytes)

        // Manifest JSON
        val manifestObj = JSONObject()
        manifestObj.put("formatVersion", 2)
        manifestObj.put("appVersion", "1.0.0")
        manifestObj.put("exportedAt", getIso8601Timestamp())
        manifestObj.put("checksum", checksumHex)
        manifestObj.put("semestersCount", semesters.size)
        manifestObj.put("coursesCount", courses.size)
        manifestObj.put("exceptionsCount", exceptions.size)
        manifestObj.put("calendarEventsCount", schedules.size)
        val manifestBytes = manifestObj.toString(2).toByteArray(StandardCharsets.UTF_8)

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            fun writeEntry(name: String, data: ByteArray) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
            writeEntry("manifest.json", manifestBytes)
            writeEntry("semesters.json", semBytes)
            writeEntry("courses.json", crsBytes)
            writeEntry("exceptions.json", excBytes)
            writeEntry("settings.json", setBytes)
            writeEntry("calendarEvents.json", eventBytes)
        }
        return bos.toByteArray()
    }

    // MARK: - Import .classflow ZIP Archive

    fun importPackageZip(inputStream: InputStream): ClassFlowImportResult {
        val entryMap = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val entryBytes = zis.readBytes()
                entryMap[name] = entryBytes
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val manifestData = entryMap["manifest.json"]
            ?: throw IllegalArgumentException("缺失 manifest.json，非合法 ClassFlow 备份包")
        val manifestObj = JSONObject(String(manifestData, StandardCharsets.UTF_8))
        val version = manifestObj.getInt("formatVersion")
        require(version in 1..2) { "不支持的备份版本：$version" }
        val expectedChecksum = manifestObj.getString("checksum")
        val semBytes = requireNotNull(entryMap["semesters.json"]) { "缺失 semesters.json" }
        val crsBytes = requireNotNull(entryMap["courses.json"]) { "缺失 courses.json" }
        val excBytes = requireNotNull(entryMap["exceptions.json"]) { "缺失 exceptions.json" }
        val eventBytes = if (version == 2) requireNotNull(entryMap["calendarEvents.json"]) { "缺失 calendarEvents.json" } else "[]".toByteArray()
        val actualChecksum = sha256Hex(semBytes + crsBytes + excBytes + if (version == 2) eventBytes else byteArrayOf())
        require(expectedChecksum.equals(actualChecksum, ignoreCase = true)) { "备份校验失败，未导入数据" }

        val semesters = if (semBytes.isNotEmpty()) {
            CourseDataManager.deserializeSemesters(String(semBytes, StandardCharsets.UTF_8))
        } else emptyList()

        val courses = if (crsBytes.isNotEmpty()) {
            CourseDataManager.deserializeCourses(String(crsBytes, StandardCharsets.UTF_8))
        } else emptyList()

        val settings = mutableMapOf<String, String>()
        val setBytes = entryMap["settings.json"]
        if (setBytes != null && setBytes.isNotEmpty()) {
            try {
                val setObj = JSONObject(String(setBytes, StandardCharsets.UTF_8))
                val keys = setObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    settings[k] = setObj.optString(k)
                }
            } catch (_: Exception) {}
        }

        val calendarEvents = CourseDataManager.deserializeSchedules(String(eventBytes, StandardCharsets.UTF_8))
        val exceptions = CourseDataManager.deserializeExceptions(String(excBytes, StandardCharsets.UTF_8))
        require(calendarEvents.size == JSONArray(String(eventBytes, StandardCharsets.UTF_8)).length() &&
            exceptions.size == JSONArray(String(excBytes, StandardCharsets.UTF_8)).length()) { "备份业务数据无法完整读取" }
        require(version != 2 || manifestObj.optInt("calendarEventsCount", -1) == calendarEvents.size) { "日程系列数量不匹配" }
        return ClassFlowImportResult(
            semesters = semesters,
            courses = courses,
            schedules = calendarEvents,
            exceptions = exceptions,
            settings = settings
        )
    }

    // MARK: - Import RFC 5545 .ics Calendar

    fun importICS(inputStream: InputStream): ClassFlowImportResult {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val lines = reader.readLines()
        val schedules = CalendarEventICSMetadata.events(lines.joinToString("\n")).toMutableList()
        val courses = mutableListOf<Course>()

        var inVEvent = false
        var nativeCalendarEvent = false
        var summary = ""
        var location: String? = null
        var description: String? = null
        var dtStart = ""
        var dtEnd = ""
        var uid = ""

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.equals("BEGIN:VEVENT", ignoreCase = true)) {
                inVEvent = true
                nativeCalendarEvent = false
                summary = ""
                location = null
                description = null
                dtStart = ""
                dtEnd = ""
                uid = UUID.randomUUID().toString()
            } else if (line.equals("END:VEVENT", ignoreCase = true)) {
                if (inVEvent && !nativeCalendarEvent && summary.isNotEmpty()) {
                    // Extract start/end time
                    var startTime = "08:00"
                    var endTime = "09:35"
                    var eventDate: String? = null

                    if (dtStart.contains("T")) {
                        val parts = dtStart.substringAfter("T").replace("Z", "")
                        if (parts.length >= 4) {
                            startTime = "${parts.substring(0, 2)}:${parts.substring(2, 4)}"
                        }
                        val dPart = dtStart.substringBefore("T")
                        if (dPart.length == 8) {
                            eventDate = "${dPart.substring(0, 4)}-${dPart.substring(4, 6)}-${dPart.substring(6, 8)}"
                        }
                    }
                    if (dtEnd.contains("T")) {
                        val parts = dtEnd.substringAfter("T").replace("Z", "")
                        if (parts.length >= 4) {
                            endTime = "${parts.substring(0, 2)}:${parts.substring(2, 4)}"
                        }
                    }

                    // Determine if course or calendar event
                    if (description?.contains("任课教师") == true || summary.contains("课") || location != null) {
                        val courseId = "c_ics_${UUID.randomUUID().toString().take(8)}"
                        val schedId = "s_ics_${UUID.randomUUID().toString().take(8)}"
                        val teacherName = description?.substringAfter("任课教师：")?.substringBefore("\n")?.trim()

                        courses.add(
                            Course(
                                id = courseId,
                                semesterId = "sem_1",
                                name = summary,
                                teacher = teacherName,
                                color = "#4F46E5",
                                notes = description,
                                schedules = listOf(
                                    CourseSchedule(
                                        id = schedId,
                                        courseId = courseId,
                                        dayOfWeek = 1,
                                        startSection = 1,
                                        endSection = 2,
                                        classroom = location,
                                        teacher = teacherName,
                                        customStartTime = startTime,
                                        customEndTime = endTime
                                    )
                                )
                            )
                        )
                    } else {
                        schedules.add(
                            CalendarEvent(
                                id = uid,
                                title = summary,
                                startTime = startTime,
                                endTime = endTime,
                                location = location,
                                description = description,
                                startDate = eventDate,
                                color = "#039BE5"
                            )
                        )
                    }
                }
                inVEvent = false
            } else if (inVEvent) {
                when {
                    line == "X-CLASSFLOW-SOURCE:CALENDAR_EVENT" -> nativeCalendarEvent = true
                    line.startsWith("SUMMARY:", ignoreCase = true) -> {
                        summary = line.substring(8).trim()
                    }
                    line.startsWith("LOCATION:", ignoreCase = true) -> {
                        location = line.substring(9).trim().ifEmpty { null }
                    }
                    line.startsWith("DESCRIPTION:", ignoreCase = true) -> {
                        description = line.substring(12).trim().ifEmpty { null }
                    }
                    line.startsWith("UID:", ignoreCase = true) -> {
                        uid = line.substring(4).trim().substringBefore("@")
                    }
                    line.startsWith("DTSTART", ignoreCase = true) -> {
                        dtStart = line.substringAfter(":").trim()
                    }
                    line.startsWith("DTEND", ignoreCase = true) -> {
                        dtEnd = line.substringAfter(":").trim()
                    }
                }
            }
        }

        return ClassFlowImportResult(
            courses = courses,
            schedules = schedules
        )
    }

    // MARK: - Export RFC 5545 .ics Calendar

    fun generateICSCalendar(
        occurrences: List<TimelineItem>,
        schedules: List<CalendarEvent>
    ): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//ClassFlow//Native Timetable & Calendar//CN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        CalendarEventICSMetadata.lines(schedules).forEach { sb.append(it).append("\r\n") }

        val dfStamp = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        dfStamp.timeZone = TimeZone.getTimeZone("UTC")
        val dtStamp = dfStamp.format(Date())

        val eventById = schedules.associateBy { it.id.lowercase() }
        for (item in occurrences) {
            if (item.status == OccurrenceStatus.CANCELLED) continue
            val event = item.calendarEventId?.lowercase()?.let { eventById[it] }
            if (event != null && (item.recurrenceOriginDate ?: item.date) in event.excludedDates) continue
            sb.append("BEGIN:VEVENT\r\n")
            if (item.calendarEventId != null) sb.append("X-CLASSFLOW-SOURCE:CALENDAR_EVENT\r\n")
            sb.append("UID:${item.id}@classflow.org\r\n")
            sb.append("DTSTAMP:$dtStamp\r\n")
            sb.append("SUMMARY:${item.title}\r\n")
            item.location?.let { sb.append("LOCATION:$it\r\n") }
            val descParts = mutableListOf<String>()
            item.notes?.let { descParts.add(it) }
            item.teacher?.let { descParts.add("任课教师：$it") }
            if (item.credit != null && item.credit > 0) {
                val cr = if (item.credit % 1.0 == 0.0) "${item.credit.toInt()} 学分" else "${item.credit} 学分"
                descParts.add("学分：$cr")
            }
            if (descParts.isNotEmpty()) {
                sb.append("DESCRIPTION:${descParts.joinToString("\\n")}\r\n")
            }

            val dateClean = (item.date ?: "2026-10-14").replace("-", "")
            val startClean = (item.startTime ?: "08:00").replace(":", "") + "00"
            val endClean = (item.endTime ?: "09:35").replace(":", "") + "00"
            sb.append("DTSTART:${dateClean}T$startClean\r\n")
            sb.append("DTEND:${dateClean}T$endClean\r\n")
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }
}
