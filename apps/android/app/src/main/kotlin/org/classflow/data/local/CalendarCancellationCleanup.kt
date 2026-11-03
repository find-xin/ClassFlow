package org.classflow.data.local

import org.classflow.data.model.CalendarEvent
import org.classflow.data.model.ExceptionType
import org.classflow.data.model.ScheduleException
import java.time.LocalDate

/** Pure migration; persist the entire plan in one local transaction, never exceptions first. */
object CalendarCancellationCleanup {
    data class Plan(
        val sourceEvents: List<CalendarEvent>, val sourceExceptions: List<ScheduleException>,
        val courseScheduleIds: Set<String>, val events: List<CalendarEvent>,
        val exceptions: List<ScheduleException>, val removed: List<ScheduleException>,
        val migratedDates: Int, val duplicateRecords: Int, val skippedRecords: Int
    ) {
        val changedSeries: Int get() = sourceEvents.zip(events).count { (a, b) -> a != b }
        val affectedSeries: List<CalendarEvent> get() {
            val removedOwners = removed.map { it.scheduleId.lowercase() }.toSet()
            return sourceEvents.zip(events).filter { (before, after) -> before != after || after.id.lowercase() in removedOwners }.map { it.second }
        }
    }

    fun preview(events: List<CalendarEvent>, exceptions: List<ScheduleException>, courseScheduleIds: Set<String>, eventIds: Set<String>? = null, removeRedundant: Boolean = true): Plan {
        val owners = events.indices.groupBy { events[it].id.lowercase() }
        val groups = exceptions.indices.groupBy { exceptions[it].let { record -> record.scheduleId.lowercase() to record.originalDate } }
        val ids = exceptions.indices.groupBy { exceptions[it].id.lowercase() }
        val protected = courseScheduleIds.map { it.lowercase() }.toSet()
        val selected = eventIds?.map { it.lowercase() }?.toSet()
        val updated = events.toMutableList()
        val removedIndices = mutableSetOf<Int>()
        var migrated = 0; var duplicates = 0; var skipped = 0
        for (indices in groups.values) {
            val groupIndices = indices.toSet()
            val first = exceptions[indices.first()]
            if (selected != null && first.scheduleId.lowercase() !in selected) continue
            val cancellations = indices.filter { exceptions[it].type == ExceptionType.CANCEL && exceptions[it].unrecognizedType == null }
            if (cancellations.isEmpty()) continue
            val owner = owners[first.scheduleId.lowercase()]
            val validDate = runCatching { LocalDate.parse(first.originalDate).toString() == first.originalDate }.getOrDefault(false)
            if (owner?.size != 1 || first.scheduleId.lowercase() in protected || events[owner.first()].frequency in listOf("none", "不重复") ||
                cancellations.size != indices.size || !validDate || cancellations.any { index ->
                    val record = exceptions[index]
                    record.targetDate != null || record.targetDayOfWeek != null || record.targetStartSection != null || record.targetEndSection != null || record.targetClassroom != null ||
                        ids[record.id.lowercase()].orEmpty().any { it !in groupIndices }
                }) { skipped += cancellations.size; continue }
            val index = owner.first()
            if (first.originalDate !in updated[index].excludedDates) {
                updated[index] = updated[index].copy(excludedDates = (updated[index].excludedDates + first.originalDate).distinct().sorted())
                migrated++
            }
            duplicates += cancellations.size - 1
            if (removeRedundant) removedIndices.addAll(cancellations)
        }
        return Plan(events.toList(), exceptions.toList(), courseScheduleIds, updated.toList(), exceptions.filterIndexed { index, _ -> index !in removedIndices },
            exceptions.filterIndexed { index, _ -> index in removedIndices }, migrated, duplicates, skipped)
    }
}
