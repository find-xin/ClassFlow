package org.classflow

import org.classflow.data.local.TimelineProjection
import org.classflow.data.model.*
import org.junit.Assert.*
import org.junit.Test

class WidgetProjectionTest {
    @Test fun sameNameSeriesWithDifferentDatesRemainVisible() {
        val sections = listOf(SectionTime(1, "08:30", "09:15"))
        val event = CalendarEvent(id = "first", title = "Study", startTime = "09:00", endTime = "10:00",
            frequency = "daily", startDate = "2026-10-22", endRepeatType = "untilDate", endRepeatUntil = "2026-10-31")
        val second = event.copy(id = "second", startDate = "2026-11-01", endRepeatUntil = "2026-11-09")
        val items = TimelineProjection.projectOccurrences(emptyList(), listOf(event, second), 10, 10,
            sections, 20, false, "2026-09-01")
        assertTrue(items.any { it.calendarEventId == "second" && it.date == "2026-11-02" })
        assertFalse(items.any { it.calendarEventId == "first" && it.date == "2026-11-02" })
    }
    @Test fun cardColorsUseLegibleContrastAndRealEventColors() {
        assertEquals(0xE27878, org.classflow.widgets.WidgetCardStyle.rgb("#E27878"))
        assertTrue(org.classflow.widgets.WidgetCardStyle.useDarkText(0xE27878))
        assertFalse(org.classflow.widgets.WidgetCardStyle.useDarkText(0x201A14))
        assertEquals(0x4F46E5, org.classflow.widgets.WidgetCardStyle.rgb("bad"))
    }
    @Test fun simultaneousSameTitleOccurrencesKeepTheirIdentity() {
        val one = TimelineItem(id = "one", title = "Study", date = "2026-11-02", dayOfWeek = 1, startSection = 1, endSection = 2)
        val two = one.copy(id = "two")
        assertEquals(listOf("one", "two"), TimelineProjection.consolidateOccurrences(listOf(one, two)).map { it.id })
    }
}
