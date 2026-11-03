package org.classflow

import org.classflow.data.local.WidgetData
import org.classflow.data.local.WidgetOccurrence
import org.classflow.widgets.WidgetWeekLayout
import org.junit.Assert.*
import org.junit.Test

class WidgetWeekLayoutTest {
    private fun event(id: String, start: String, end: String, day: Int = 1, color: String = "#43B591") =
        WidgetOccurrence(id, "Title $id", null, null, day, 1, 4, start, end, color, weekNumber = 10)
    private fun layout(vararg events: WidgetOccurrence) = WidgetWeekLayout.build(WidgetData(currentWeek = 10,
        sectionTimes = listOf("1" to "08:30\n09:15", "2" to "09:20\n10:05", "3" to "10:15\n11:00", "4" to "11:05\n11:50"),
        items = events.toList()))

    @Test fun longOccurrenceIsNeverSplitAtSectionOrBandBoundaries() {
        val event = event("a", "09:00", "11:30")
        val plan = layout(event)
        val cards = plan.bands.flatMap { it.cards }
        assertEquals(1, cards.size)
        assertEquals(event, cards.single().occurrence)
        assertTrue(plan.bands.none { it.start > 540 && it.start < 690 })
    }
    @Test fun overlappingItemsUseHorizontalLanesRatherThanStackingStartTimes() {
        val plan = layout(event("a", "20:30", "21:30"), event("b", "20:30", "20:50"))
        val cards = plan.bands.flatMap { it.cards }
        assertEquals(2, cards.size)
        assertEquals(cards[0].start, cards[1].start)
        assertEquals(plan.y(cards[0].start), plan.y(cards[1].start), 0f)
        assertEquals(setOf(0, 1), cards.map { it.lane }.toSet())
        assertTrue(cards.all { it.laneCount == 2 })
    }
    @Test fun sameStartAcrossDaysUsesTheSameGlobalAxis() {
        val plan = layout(event("a", "20:30", "20:50", 1), event("b", "20:30", "21:00", 2), event("c", "20:20", "21:30", 2))
        val cards = plan.bands.flatMap { it.cards }.associateBy { it.occurrence.id }
        assertEquals(plan.y(cards.getValue("a").start), plan.y(cards.getValue("b").start), 0f)
    }
    @Test fun touchingIntervalsDoNotOverlapAndEachTitleOccursOnce() {
        val plan = layout(event("a", "08:30", "09:00"), event("b", "09:00", "11:30"))
        val cards = plan.bands.flatMap { it.cards }
        assertEquals(listOf("a", "b"), cards.map { it.occurrence.id })
        assertTrue(cards.all { it.laneCount == 1 })
    }
    @Test fun threeWayOverlapAllocatesLanesWithoutCollisions() {
        val cards = layout(event("a", "09:00", "11:30"), event("b", "09:30", "10:00"), event("c", "09:45", "10:15"), event("d", "10:00", "11:00"))
            .bands.flatMap { it.cards }
        cards.forEach { a -> cards.filter { it != a && it.start < a.end && a.start < it.end }.forEach { b ->
            assertNotEquals(a.lane, b.lane)
        } }
        assertTrue(cards.all { it.laneCount == 3 })
    }
    @Test fun eachCardRetainsItsOwnColorRegardlessOfOverlap() {
        val plan = layout(event("strength", "20:30", "21:30", color = "#43B591"), event("recovery", "20:30", "21:00", color = "#A1B2C3"))
        assertEquals(mapOf("strength" to "#43B591", "recovery" to "#A1B2C3"), plan.bands.flatMap { it.cards }.associate { it.occurrence.id to it.occurrence.colorHex })
    }
    @Test fun duplicateCacheEntriesDoNotDuplicateCardsButDistinctIdsArePreserved() {
        val a = event("a", "09:00", "11:00")
        val cards = layout(a, a, a.copy(id = "b")).bands.flatMap { it.cards }
        assertEquals(setOf("a", "b"), cards.map { it.occurrence.id }.toSet())
        assertEquals(2, cards.size)
    }
    @Test fun wrongWeekAndInvalidIntervalsAreExcluded() {
        val cards = layout(event("old", "09:00", "10:00").copy(weekNumber = 9), event("invalid", "10:00", "09:00"), event("broken", "garbage", "10:00").copy(startSection = 999))
            .bands.flatMap { it.cards }
        assertTrue(cards.isEmpty())
    }
    @Test fun courseWithoutExplicitClockTimeUsesConfiguredSections() {
        val plan = layout(event("course", "", "").copy(startSection = 2, endSection = 3))
        val card = plan.bands.flatMap { it.cards }.single()
        assertEquals(560, card.start)
        assertEquals(660, card.end)
    }
    @Test fun eveningAndMidnightRemainInsideScrollableExtent() {
        val plan = layout(event("sleep", "23:30", "24:00"))
        val card = plan.bands.flatMap { it.cards }.single()
        assertEquals(1440, card.end)
        assertTrue(plan.y(card.end) > plan.y(card.start))
        assertEquals(1440, plan.bands.last().end)
    }
    @Test fun denseWeekRetainsEveryIdentityWithNoBoundarySplits() {
        val events = (0 until 2000).map { index ->
            val start = 480 + index % 400
            fun clock(value: Int) = "%02d:%02d".format(java.util.Locale.ROOT, value / 60, value % 60)
            event("item-$index", clock(start), clock(start + 30), index % 7 + 1)
        }
        val plan = WidgetWeekLayout.build(WidgetData(currentWeek = 10, items = events))
        val cards = plan.bands.flatMap { it.cards }
        assertEquals(2000, cards.size)
        assertEquals(2000, cards.map { it.occurrence.id }.toSet().size)
        plan.bands.forEach { band -> assertTrue(band.cards.all { it.start >= band.start && it.end <= band.end }) }
    }
    @Test fun parseAcceptsIsoTimeAndRejectsInvalidValues() {
        assertEquals(1230, WidgetWeekLayout.minutes("2026-11-02T20:30:00+08:00"))
        assertEquals(1440, WidgetWeekLayout.minutes("24:00"))
        assertNull(WidgetWeekLayout.minutes("24:01"))
        assertNull(WidgetWeekLayout.minutes("12:99"))
    }
}
