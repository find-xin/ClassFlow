package org.classflow.data.local

import org.classflow.data.model.CalendarEvent
import org.json.JSONArray
import java.util.Base64

/** X-properties keep native series, UUIDs and exclusions; ordinary clients see filtered VEVENTs. */
object CalendarEventICSMetadata {
    private const val PREFIX = "X-CLASSFLOW-CALENDAR-EVENT:"
    fun lines(events: List<CalendarEvent>): List<String> = events.flatMap { event ->
        val json = JSONArray(CourseDataManager.serializeSchedules(listOf(event))).getJSONObject(0).toString()
        (PREFIX + Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8)))
            .chunked(72).mapIndexed { index, chunk -> (if (index == 0) "" else " ") + chunk }
    }
    fun events(content: String): List<CalendarEvent> {
        val unfolded = mutableListOf<String>()
        for (line in content.lineSequence()) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && unfolded.isNotEmpty()) unfolded[unfolded.lastIndex] += line.drop(1)
            else unfolded.add(line)
        }
        val result = linkedMapOf<String, CalendarEvent>()
        for (line in unfolded.filter { it.startsWith(PREFIX) }) {
            val json = String(Base64.getDecoder().decode(line.removePrefix(PREFIX)), Charsets.UTF_8)
            val parsed = CourseDataManager.deserializeSchedules("[$json]")
            require(parsed.size == 1) { "日程系列元数据无效，未导入" }
            val event = parsed.single()
            val previous = result[event.id.lowercase()]
            result[event.id.lowercase()] = (previous ?: event).copy(excludedDates = (event.excludedDates + previous?.excludedDates.orEmpty()).distinct().sorted())
        }
        require(result.isNotEmpty() || "X-CLASSFLOW-SOURCE:CALENDAR_EVENT" !in unfolded) { "缺失日程系列元数据，未导入" }
        return result.values.toList()
    }
}
