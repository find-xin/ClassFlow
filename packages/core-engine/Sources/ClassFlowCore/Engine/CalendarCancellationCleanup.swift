import Foundation

/// Pure, conservative migration. Callers must commit events, exceptions and tombstones atomically.
/// Course exceptions, unknown owners and dates with any non-CANCEL override are never changed.
public enum CalendarCancellationCleanup {
    public struct Plan: Sendable, Equatable {
        public let sourceEvents: [CalendarEvent]
        public let sourceExceptions: [ScheduleException]
        public let courseScheduleIDs: Set<UUID>
        public let events: [CalendarEvent]
        public let exceptions: [ScheduleException]
        public let removed: [ScheduleException]
        public let migratedDates: Int
        public let duplicateRecords: Int
        public let skippedRecords: Int
        public var changedSeries: Int { zip(sourceEvents, events).filter { $0 != $1 }.count }
        public var affectedSeries: [CalendarEvent] {
            let removedOwners = Set(removed.map(\.scheduleId))
            return zip(sourceEvents, events).filter { $0 != $1 || removedOwners.contains($1.id) }.map { $0.1 }
        }
    }

    public static func preview(events: [CalendarEvent], exceptions: [ScheduleException], courseScheduleIDs: Set<UUID>, eventIDs: Set<UUID>? = nil, removeRedundant: Bool = true) -> Plan {
        let owners = Dictionary(grouping: events.indices, by: { events[$0].id })
        let groups = Dictionary(grouping: exceptions.indices, by: { "\(exceptions[$0].scheduleId)/\(exceptions[$0].originalDate)" })
        let ids = Dictionary(grouping: exceptions.indices, by: { exceptions[$0].id })
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        var updated = events
        var removedIndices = Set<Int>()
        var migrated = 0, duplicates = 0, skipped = 0
        for indices in groups.values {
            let groupIndices = Set(indices)
            let first = exceptions[indices[0]]
            guard eventIDs == nil || eventIDs!.contains(first.scheduleId) else { continue }
            let cancellations = indices.filter { exceptions[$0].exceptionType == .cancel && exceptions[$0].unrecognizedType == nil }
            guard !cancellations.isEmpty else { continue }
            guard let owner = owners[first.scheduleId], owner.count == 1,
                  !courseScheduleIDs.contains(first.scheduleId),
                  CalendarEventProjector.normalizedFrequency(events[owner[0]].frequency) != "none",
                  cancellations.count == indices.count,
                  let date = formatter.date(from: first.originalDate), formatter.string(from: date) == first.originalDate,
                  cancellations.allSatisfy({ index in
                      let record = exceptions[index]
                      return record.targetDate == nil && record.targetDayOfWeek == nil && record.targetStartSection == nil && record.targetEndSection == nil && record.targetClassroom == nil &&
                          (ids[record.id] ?? []).allSatisfy { groupIndices.contains($0) }
                  }) else { skipped += cancellations.count; continue }
            let index = owner[0]
            if !updated[index].excludedDates.contains(first.originalDate) {
                updated[index].excludedDates = Array(Set(updated[index].excludedDates + [first.originalDate])).sorted()
                migrated += 1
            }
            duplicates += cancellations.count - 1
            if removeRedundant { removedIndices.formUnion(cancellations) }
        }
        return Plan(sourceEvents: events, sourceExceptions: exceptions, courseScheduleIDs: courseScheduleIDs,
                    events: updated, exceptions: exceptions.enumerated().filter { !removedIndices.contains($0.offset) }.map(\.element),
                    removed: exceptions.enumerated().filter { removedIndices.contains($0.offset) }.map(\.element),
                    migratedDates: migrated, duplicateRecords: duplicates, skippedRecords: skipped)
    }
}
