package org.classflow.widgets

import org.classflow.data.local.WidgetData
import org.classflow.data.local.WidgetOccurrence

/** A single occurrence keeps one rectangle, one title and one colour throughout the week grid. */
object WidgetWeekLayout {
    private val clockPattern = Regex("\\d{2}:\\d{2}")
    data class Card(
        val occurrence: WidgetOccurrence,
        val start: Int,
        val end: Int,
        val lane: Int,
        val laneCount: Int
    )
    data class Tick(val minute: Int, val label: String)
    data class Band(val start: Int, val end: Int, val cards: List<Card>)
    data class Layout(val bands: List<Band>, val ticks: List<Tick>, val offsets: Map<Int, Float>) {
        fun y(minute: Int): Float = offsets.getValue(minute)
        fun height(band: Band): Float = y(band.end) - y(band.start)
    }

    fun minutes(value: String): Int? {
        val time = value.trim().substringAfterLast('T').substringAfterLast(' ').take(5)
        if (!clockPattern.matches(time)) return null
        val hour = time.take(2).toInt()
        val minute = time.takeLast(2).toInt()
        if (hour == 24 && minute == 0) return 1440
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }

    fun build(data: WidgetData): Layout {
        val sections = data.sectionTimes.mapNotNull { (label, times) ->
            val parts = times.lines()
            val start = parts.firstOrNull()?.let(::minutes) ?: return@mapNotNull null
            val end = parts.getOrNull(1)?.let(::minutes) ?: return@mapNotNull null
            if (end <= start) null else Triple(label, start, end)
        }
        val timed = data.items.asSequence()
            .filter { it.dayOfWeek in 1..7 && (it.weekNumber == data.currentWeek || it.weekNumber == 0) }
            .distinctBy { Triple(it.id, it.dayOfWeek, it.date) }
            .mapNotNull { item ->
                val start = minutes(item.startTime) ?: sections.firstOrNull { it.first.toIntOrNull() == item.startSection }?.second
                val end = minutes(item.endTime) ?: sections.firstOrNull { it.first.toIntOrNull() == item.endSection }?.third
                if (start == null || end == null || end <= start) null else Card(item, start, end, 0, 1)
            }.toList()
        val cards = timed.groupBy { it.occurrence.dayOfWeek }.values.flatMap(::assignLanes)
        val points = (sections.flatMap { listOf(it.second, it.third) } + cards.flatMap { listOf(it.start, it.end) })
            .distinct().sorted().ifEmpty { listOf(480, 1320) }
        val offsets = linkedMapOf(points.first() to 0f)
        points.zipWithNext().forEach { (start, end) ->
            // Keep every shared timestamp at exactly the same Y, including short appointments.
            offsets[end] = offsets.getValue(start) + maxOf(26f, (end - start) * 0.65f)
        }
        // Only cut where no card crosses the boundary. A card is never split to fit a list row.
        val sortedCards = cards.sortedBy { it.start }
        var cardIndex = 0
        var coveredUntil = -1
        val cuts = points.filter { minute ->
            while (cardIndex < sortedCards.size && sortedCards[cardIndex].start < minute) {
                coveredUntil = maxOf(coveredUntil, sortedCards[cardIndex].end)
                cardIndex++
            }
            coveredUntil <= minute
        }
        val cardsByBand = cards.groupBy { card ->
            val position = cuts.binarySearch(card.start)
            if (position >= 0) position else -position - 2
        }
        val bands = cuts.zipWithNext().mapIndexed { index, (start, end) ->
            Band(start, end, cardsByBand[index].orEmpty())
        }
        return Layout(bands, sections.map { Tick(it.second, "${it.first}\n${formatTime(it.second)}") }.distinctBy { it.minute }, offsets)
    }

    private fun assignLanes(items: List<Card>): List<Card> {
        val sorted = items.sortedWith(compareBy<Card> { it.start }.thenBy { it.end }.thenBy { it.occurrence.id })
        val result = mutableListOf<Card>()
        var cluster = mutableListOf<Card>()
        var clusterEnd = -1
        fun flush() {
            val laneEnds = mutableListOf<Int>()
            val assigned = cluster.map { card ->
                val free = laneEnds.indexOfFirst { it <= card.start }
                val lane = if (free >= 0) free else laneEnds.size.also { laneEnds.add(0) }
                laneEnds[lane] = card.end
                card.copy(lane = lane)
            }
            result += assigned.map { it.copy(laneCount = laneEnds.size.coerceAtLeast(1)) }
            cluster = mutableListOf()
        }
        sorted.forEach { card ->
            if (cluster.isNotEmpty() && card.start >= clusterEnd) flush()
            if (cluster.isEmpty()) clusterEnd = card.end else clusterEnd = maxOf(clusterEnd, card.end)
            cluster += card
        }
        if (cluster.isNotEmpty()) flush()
        return result
    }

    private fun formatTime(minute: Int): String = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)
}
