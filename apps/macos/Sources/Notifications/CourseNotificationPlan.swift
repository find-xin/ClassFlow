import Foundation
import ClassFlowCore

/// Notification eligibility is stricter than the timetable's optional ghost-course display.
enum CourseNotificationPlan {
    static func eligible(_ items: [TimelineItem]) -> [TimelineItem] {
        items.filter { $0.sourceType == .course && $0.isCurrentWeek && $0.status != .cancelled }
            .sorted { ($0.date, $0.startTime ?? "99:99", $0.id) < ($1.date, $1.startTime ?? "99:99", $1.id) }
    }

    struct Briefing {
        let id: String
        let date: Date
        let items: [TimelineItem]
    }

    static func briefings(_ items: [TimelineItem], hour: Int, minute: Int, now: Date = Date(), calendar: Calendar = .current) -> [Briefing] {
        guard (0...23).contains(hour), (0...59).contains(minute) else { return [] }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = calendar
        formatter.timeZone = calendar.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        let active = eligible(items)
        return (0..<7).compactMap { offset in
            guard let day = calendar.date(byAdding: .day, value: offset, to: now),
                  let fire = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day), fire > now else { return nil }
            let key = formatter.string(from: day)
            return Briefing(id: "classflow_daily_briefing_" + key, date: fire, items: active.filter { $0.date == key })
        }
    }
}
