package org.classflow

import org.classflow.data.model.*
import org.classflow.notifications.ReminderPlan
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderPlanTest {
    @Test fun respectsCourseWeeksAndEventOffsets() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.parse("2026-10-27T07:00:00").atZone(zone).toInstant().toEpochMilli()
        val sem = Semester(id = "term", name = "Term", startDate = "2026-09-01", endDate = "2027-02-01", totalWeeks = 20)
        val inactive = Course(id = "c", semesterId = "term", name = "inactive", schedules = listOf(
            CourseSchedule(id = "s", dayOfWeek = 2, startSection = 1, endSection = 1, startWeek = 11, endWeek = 16)))
        val event = CalendarEvent(id = "e", semesterId = "term", title = "event", startDate = "2026-10-27", startTime = "08:30", endTime = "09:00", alertMinutes = 10)
        val none = event.copy(id = "nil", alertMinutes = null)
        val zero = event.copy(id = "zero", alertMinutes = 0)
        val plans = ReminderPlan.build(sem, listOf(inactive), listOf(event, none, zero),
            listOf(SectionTime(1, "08:30", "09:15")), emptyList(), true, 15, now, zone)
        assertEquals(2, plans.size)
        assertEquals(LocalDateTime.parse("2026-10-27T08:20:00").atZone(zone).toInstant().toEpochMilli(), plans.first { it.key.contains("event_occ_e_") }.fireAt)
        assertEquals(LocalDateTime.parse("2026-10-27T08:30:00").atZone(zone).toInstant().toEpochMilli(), plans.first { it.key.contains("event_occ_zero_") }.fireAt)
        assertTrue(plans.none { it.title == "inactive" })
        val cancellation = ScheduleException(scheduleId = "e", originalDate = "2026-10-27", type = ExceptionType.CANCEL)
        val cancelledPlans = ReminderPlan.build(sem, listOf(inactive), listOf(event, zero),
            listOf(SectionTime(1, "08:30", "09:15")), listOf(cancellation), true, 15, now, zone)
        assertEquals(1, cancelledPlans.size)
        assertTrue(cancelledPlans.single().key.contains("event_occ_zero_"))
    }
}
