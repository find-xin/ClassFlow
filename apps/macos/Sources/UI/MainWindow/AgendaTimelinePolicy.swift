import Foundation
import ClassFlowCore

/// Agenda ordering uses concrete occurrence clocks, never timetable drawing coordinates.
enum AgendaTimelinePolicy {
    static func sorted(_ items: [TimelineItem]) -> [TimelineItem] {
        items.sorted {
            if $0.date != $1.date { return $0.date < $1.date }
            if $0.isAllDay != $1.isAllDay { return $0.isAllDay }
            if $0.startTime != $1.startTime { return ($0.startTime ?? "99:99") < ($1.startTime ?? "99:99") }
            if $0.endTime != $1.endTime { return ($0.endTime ?? "99:99") < ($1.endTime ?? "99:99") }
            return $0.id < $1.id
        }
    }

    static func upcoming(_ items: [TimelineItem], now: Date, calendar: Calendar = .current) -> TimelineItem? {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = calendar.timeZone
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        let today = formatter.string(from: now)
        let minutes = calendar.component(.hour, from: now) * 60 + calendar.component(.minute, from: now)
        return sorted(items).first { item in
            guard item.date == today, item.isCurrentWeek, item.status != .cancelled, !item.isAllDay,
                  let start = clockMinutes(item.startTime), let end = clockMinutes(item.endTime) else { return false }
            return end > start && end > minutes
        }
    }

    private static func clockMinutes(_ value: String?) -> Int? {
        guard let value else { return nil }
        let parts = value.split(separator: ":")
        guard parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]),
              (0...23).contains(hour), (0...59).contains(minute) else { return nil }
        return hour * 60 + minute
    }

    static func showsSections(_ item: TimelineItem, events: [CalendarEvent]) -> Bool {
        item.startSection > 0 && (item.sourceType == .course || events.contains {
            $0.id == item.calendarEventId && $0.timeMode == "sections"
        })
    }
}
