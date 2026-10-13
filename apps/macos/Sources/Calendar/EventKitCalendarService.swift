import Foundation
import EventKit
import ClassFlowCore
import AppKit

/// Native macOS calendar service managing synchronization of timetable occurrences with Apple Calendar.
public final class EventKitCalendarService: @unchecked Sendable, CalendarServiceProtocol {
    private let eventStore: EKEventStore

    public init(eventStore: EKEventStore = EKEventStore()) {
        self.eventStore = eventStore
    }

    /// Checks the current authorization status for EventKit calendar events.
    public func checkAccess() async -> CalendarAccessStatus {
        let status = EKEventStore.authorizationStatus(for: .event)
        switch status {
        case .authorized, .fullAccess:
            return .fullAccess
        case .denied:
            return .denied
        case .restricted:
            return .restricted
        case .notDetermined, .writeOnly:
            return .notDetermined
        @unknown default:
            return .notDetermined
        }
    }

    /// Requests full calendar access from the operating system.
    public func requestAccess() async throws -> Bool {
        if #available(macOS 14.0, *) {
            return try await eventStore.requestFullAccessToEvents()
        } else {
            return try await withCheckedThrowingContinuation { continuation in
                eventStore.requestAccess(to: .event) { granted, error in
                    if let error = error {
                        continuation.resume(throwing: error)
                    } else {
                        continuation.resume(returning: granted)
                    }
                }
            }
        }
    }

    /// Finds or creates a dedicated calendar for ClassFlow.
    public func getOrCreateCalendar(named title: String) throws -> EKCalendar {
        let calendars = eventStore.calendars(for: .event)
        if let existing = calendars.first(where: { $0.title == title }) {
            return existing
        }

        let newCal = EKCalendar(for: .event, eventStore: eventStore)
        newCal.title = title

        // Determine best source (iCloud or Local)
        let sources = eventStore.sources
        if let defaultSource = eventStore.defaultCalendarForNewEvents?.source {
            newCal.source = defaultSource
        } else if let localSource = sources.first(where: { $0.sourceType == .local }) {
            newCal.source = localSource
        } else if let firstSource = sources.first {
            newCal.source = firstSource
        }

        // Set calendar color to indigo
        newCal.color = NSColor.systemIndigo

        try eventStore.saveCalendar(newCal, commit: true)
        return newCal
    }

    /// Executes a calculated CalendarSyncPlan against Apple Calendar.
    public func executePlan(_ plan: CalendarSyncPlan, inCalendarNamed name: String) async throws -> CalendarSyncExecutionResult {
        let calendar = try getOrCreateCalendar(named: name)

        var createdCount = 0
        var updatedCount = 0
        var deletedCount = 0
        var newMappings: [CalendarMapping] = []
        var errors: [String] = []

        // 1. Process deletions
        for mapping in plan.toDelete {
            if let event = eventStore.event(withIdentifier: mapping.externalEventId) {
                do {
                    try eventStore.remove(event, span: .thisEvent, commit: false)
                    deletedCount += 1
                } catch {
                    errors.append("Failed to delete event \(mapping.externalEventId): \(error.localizedDescription)")
                }
            }
        }

        // 2. Process updates
        for update in plan.toUpdate {
            let candidate = update.candidate
            if let event = eventStore.event(withIdentifier: update.existingMapping.externalEventId) {
                event.title = candidate.title
                event.location = candidate.location
                event.notes = candidate.notes
                event.startDate = candidate.startDate
                event.endDate = candidate.endDate

                // Alarms
                event.alarms = nil
                if let reminderMinutes = candidate.reminderOffsetMinutes {
                    let alarm = EKAlarm(relativeOffset: -Double(reminderMinutes * 60))
                    event.addAlarm(alarm)
                }

                do {
                    try eventStore.save(event, span: .thisEvent, commit: false)
                    updatedCount += 1
                    var updatedMapping = update.existingMapping
                    updatedMapping.contentHash = candidate.contentHash
                    updatedMapping.lastSyncedAt = ISO8601DateFormatter().string(from: Date())
                    newMappings.append(updatedMapping)
                } catch {
                    errors.append("Failed to update event \(event.eventIdentifier ?? ""): \(error.localizedDescription)")
                }
            } else {
                // Event was missing from store, recreate as new
                let newEvent = EKEvent(eventStore: eventStore)
                newEvent.calendar = calendar
                newEvent.title = candidate.title
                newEvent.location = candidate.location
                newEvent.notes = candidate.notes
                newEvent.startDate = candidate.startDate
                newEvent.endDate = candidate.endDate
                if let reminderMinutes = candidate.reminderOffsetMinutes {
                    newEvent.addAlarm(EKAlarm(relativeOffset: -Double(reminderMinutes * 60)))
                }
                do {
                    try eventStore.save(newEvent, span: .thisEvent, commit: false)
                    createdCount += 1
                    let mapping = CalendarMapping(
                        semesterId: candidate.semesterId,
                        courseId: candidate.courseId,
                        scheduleId: candidate.scheduleId,
                        occurrenceDate: candidate.occurrenceDate,
                        startSection: candidate.startSection,
                        endSection: candidate.endSection,
                        externalEventId: newEvent.eventIdentifier,
                        contentHash: candidate.contentHash
                    )
                    newMappings.append(mapping)
                } catch {
                    errors.append("Failed to create recreated event: \(error.localizedDescription)")
                }
            }
        }

        // 3. Process creations
        for candidate in plan.toCreate {
            let newEvent = EKEvent(eventStore: eventStore)
            newEvent.calendar = calendar
            newEvent.title = candidate.title
            newEvent.location = candidate.location
            newEvent.notes = candidate.notes
            newEvent.startDate = candidate.startDate
            newEvent.endDate = candidate.endDate

            if let reminderMinutes = candidate.reminderOffsetMinutes {
                newEvent.addAlarm(EKAlarm(relativeOffset: -Double(reminderMinutes * 60)))
            }

            do {
                try eventStore.save(newEvent, span: .thisEvent, commit: false)
                createdCount += 1
                let mapping = CalendarMapping(
                    semesterId: candidate.semesterId,
                    courseId: candidate.courseId,
                    scheduleId: candidate.scheduleId,
                    occurrenceDate: candidate.occurrenceDate,
                    startSection: candidate.startSection,
                    endSection: candidate.endSection,
                    externalEventId: newEvent.eventIdentifier,
                    contentHash: candidate.contentHash
                )
                newMappings.append(mapping)
            } catch {
                errors.append("Failed to create event for \(candidate.title): \(error.localizedDescription)")
            }
        }

        // Batch commit all pending transactions
        try eventStore.commit()

        return CalendarSyncExecutionResult(
            createdCount: createdCount,
            updatedCount: updatedCount,
            deletedCount: deletedCount,
            newMappings: newMappings,
            errors: errors
        )
    }

    /// Removes the dedicated ClassFlow calendar and all associated events.
    public func clearCalendar(named name: String) throws -> Int {
        let calendars = eventStore.calendars(for: .event)
        guard let cal = calendars.first(where: { $0.title == name }) else {
            return 0
        }

        // Predicate for events in this calendar across a wide window (last 2 years to next 2 years)
        let twoYearsAgo = Calendar.current.date(byAdding: .year, value: -2, to: Date()) ?? Date()
        let twoYearsLater = Calendar.current.date(byAdding: .year, value: 2, to: Date()) ?? Date()
        let predicate = eventStore.predicateForEvents(withStart: twoYearsAgo, end: twoYearsLater, calendars: [cal])
        let events = eventStore.events(matching: predicate)
        let count = events.count

        try eventStore.removeCalendar(cal, commit: true)
        return count
    }
}
