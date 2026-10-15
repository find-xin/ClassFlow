package org.classflow.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import org.classflow.data.model.CalendarMapping
import org.classflow.data.model.TimelineItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Android system calendar synchronization engine using CalendarContract.
 * Maps timetable occurrences to system events and manages duplicate prevention.
 */
class AndroidCalendarSyncService(private val context: Context) {
    companion object {
        const val CALENDAR_NAME = "ClassFlow 课程表"
        const val ACCOUNT_NAME = "ClassFlow"
        const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
    }

    data class SyncResult(
        val createdCount: Int,
        val updatedCount: Int,
        val deletedCount: Int,
        val newMappings: List<CalendarMapping>
    )

    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Finds or creates a dedicated ClassFlow calendar in the Android CalendarProvider.
     */
    fun getOrCreateCalendarId(): Long? {
        if (!hasCalendarPermission()) return null

        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.NAME)
        val uri = CalendarContract.Calendars.CONTENT_URI
        val selection = "${CalendarContract.Calendars.NAME} = ?"
        val selectionArgs = arrayOf(CALENDAR_NAME)

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }

        // Create new local calendar
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4F46E5.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }

        val syncUri = uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

        val insertUri = context.contentResolver.insert(syncUri, values)
        return insertUri?.lastPathSegment?.toLongOrNull()
    }

    /**
     * Synchronizes a list of timetable occurrences to Android CalendarProvider.
     */
    fun syncOccurrences(
        occurrences: List<TimelineItem>,
        semesterId: String,
        existingMappings: List<CalendarMapping>,
        reminderMinutes: Int = 15
    ): SyncResult {
        val calendarId = getOrCreateCalendarId() ?: return SyncResult(0, 0, 0, emptyList())

        val mappingMap = existingMappings.associateBy {
            "${it.courseId}_${it.occurrenceDate}_${it.startSection}_${it.endSection}"
        }.toMutableMap()

        val activeItems = occurrences.filter { it.status != org.classflow.data.model.OccurrenceStatus.CANCELLED }
        val newMappings = mutableListOf<CalendarMapping>()
        var created = 0
        var updated = 0
        var deleted = 0

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        for (item in activeItems) {
            val key = "${item.courseId}_${item.date}_${item.startSection}_${item.endSection}"
            val startDate = sdf.parse("${item.date} ${item.startTime}")?.time ?: continue
            val endDate = sdf.parse("${item.date} ${item.endTime}")?.time ?: continue

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startDate)
                put(CalendarContract.Events.DTEND, endDate)
                put(CalendarContract.Events.TITLE, item.title)
                put(CalendarContract.Events.EVENT_LOCATION, item.location ?: "")
                put(CalendarContract.Events.DESCRIPTION, "任课教师：${item.teacher ?: "暂无"}\n节次：第 ${item.startSection}-${item.endSection} 节")
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, if (reminderMinutes > 0) 1 else 0)
            }

            val existing = mappingMap[key]
            if (existing != null) {
                // Update
                val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing.externalEventId.toLongOrNull() ?: 0)
                context.contentResolver.update(eventUri, values, null, null)
                updated++
                mappingMap.remove(key)
                newMappings.add(existing)
            } else {
                // Insert
                val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                val eventId = eventUri?.lastPathSegment ?: UUID.randomUUID().toString()
                created++

                // Add reminder
                if (reminderMinutes > 0 && eventUri != null) {
                    val reminderValues = ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                }

                newMappings.add(
                    CalendarMapping(
                        semesterId = semesterId,
                        courseId = item.courseId ?: "",
                        scheduleId = item.scheduleId ?: "",
                        occurrenceDate = item.date,
                        startSection = item.startSection,
                        endSection = item.endSection,
                        externalEventId = eventId,
                        contentHash = "",
                        lastSyncedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                    )
                )
            }
        }

        // Prune removed/cancelled mappings
        for ((_, mapping) in mappingMap) {
            val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, mapping.externalEventId.toLongOrNull() ?: 0)
            context.contentResolver.delete(eventUri, null, null)
            deleted++
        }

        return SyncResult(created, updated, deleted, newMappings)
    }
}
