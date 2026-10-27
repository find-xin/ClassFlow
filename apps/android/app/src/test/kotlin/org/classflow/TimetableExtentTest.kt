package org.classflow

import org.classflow.data.model.SectionTime
import org.classflow.data.model.TimelineItem
import org.classflow.ui.timetable.buildEffectiveSectionTimes
import org.classflow.ui.timetable.clusterDayCourses
import org.classflow.ui.timetable.parseTimeToMinutes
import org.junit.Assert.*
import org.junit.Test

class TimetableExtentTest {
    @Test fun lateNightCardFitsInsideScrollableGrid() {
        for (end in listOf("23:40", "23:59", "24:00")) {
            val item = TimelineItem(id = "late", title = "Sleep", date = "2026-10-27",
                dayOfWeek = 2, startSection = 14, endSection = 14,
                startTime = "23:30", endTime = end)
            val sections = buildEffectiveSectionTimes(listOf(SectionTime(14, "22:30", "23:30")), listOf(item))
            assertTrue(parseTimeToMinutes(sections.last().endTime)!! >= parseTimeToMinutes(end)!!)
            assertTrue(parseTimeToMinutes(sections.last().endTime)!! > parseTimeToMinutes(sections.last().startTime)!!)
            val cluster = clusterDayCourses(listOf(item), 2, sections, 80f).single()
            assertTrue(cluster.topOffset + cluster.cardHeight <= sections.size * 80f)
        }
    }
}
