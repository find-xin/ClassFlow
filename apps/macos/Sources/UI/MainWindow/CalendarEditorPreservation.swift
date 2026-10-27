import Foundation
import ClassFlowCore

/// Hidden or unchanged editor fields must not be replaced with form defaults.
enum CalendarEditorPreservation {
    static func apply(original: CalendarEvent, draft: CalendarEvent) -> CalendarEvent {
        var result = draft
        func equivalentClock(_ lhs: String, _ rhs: String) -> Bool {
            lhs.replacingOccurrences(of: "T", with: " ").prefix(16) == rhs.replacingOccurrences(of: "T", with: " ").prefix(16)
        }
        if equivalentClock(original.startTime, draft.startTime) { result.startTime = original.startTime }
        if equivalentClock(original.endTime, draft.endTime) { result.endTime = original.endTime }
        if (original.location ?? "") == (draft.location ?? "") { result.location = original.location }
        if (original.description ?? "") == (draft.description ?? "") { result.description = original.description }
        if original.timeMode != "sections" && draft.timeMode != "sections" {
            result.startSection = original.startSection; result.endSection = original.endSection
        }
        if original.frequency == draft.frequency {
            if draft.frequency != "customWeeks" { result.customWeeks = original.customWeeks }
            if draft.frequency != "customDays" { result.customDaysOfWeek = original.customDaysOfWeek }
            if draft.frequency != "none" { result.endDate = original.endDate }
        }
        if original.endRepeatType == draft.endRepeatType {
            if draft.endRepeatType != "count" { result.endRepeatCount = original.endRepeatCount }
            if draft.endRepeatType != "untilDate" { result.endRepeatUntil = original.endRepeatUntil }
        }
        return result
    }
}
