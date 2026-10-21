import Foundation
import ClassFlowCore

/// Pure reminder plan, independently testable without requesting OS notification permission.
struct CalendarAlertPlan: Equatable {
    var id: String
    var title: String
    var location: String?
    var fireDate: Date

    static func make(events: [CalendarEvent], semesters: [Semester], exceptions: [ScheduleException], now: Date = Date(), limit: Int = 30) -> [CalendarAlertPlan] {
        let calendar = Calendar.current
        let formatter = DateFormatter(); formatter.locale = Locale(identifier: "en_US_POSIX"); formatter.dateFormat = "yyyy-MM-dd HH:mm"
        var plans: [CalendarAlertPlan] = []
        for event in events {
            guard let offset = event.alertMinutesBefore, offset >= 0,
                  let semester = semesters.first(where: { $0.id == event.semesterId }) ?? semesters.first(where: { $0.isCurrent }) ?? semesters.first,
                  let horizon = calendar.date(byAdding: .day, value: max(8, offset / 1440 + 8), to: now) else { continue }
            let calculator = WeekCalculator(timeZoneId: TimeZone.current.identifier)
            let firstWeek = calculator.weekIndex(for: calculator.formatDate(now), semesterStartDate: semester.startDate) ?? 1
            let lastWeek = calculator.weekIndex(for: calculator.formatDate(horizon), semesterStartDate: semester.startDate) ?? firstWeek
            var seen = Set<String>()
            let base = event.startDate ?? String(event.startTime.prefix(10))
            let validDates = Set(CalendarEventProjector().occurrenceDates(event, semester: semester, through: calculator.formatDate(horizon)))
            for week in firstWeek...max(firstWeek, lastWeek) {
                for item in CalendarEventProjector().project(event, semester: semester, week: week, exceptions: exceptions) where item.status != .cancelled {
                    // Only the first day of a multi-day occurrence receives its reminder.
                    guard validDates.contains(item.date), (event.frequency ?? "none") != "none" || item.date == base,
                          seen.insert(item.date).inserted,
                          let start = formatter.date(from: "\(item.date) \(item.startTime ?? "00:00")") else { continue }
                    let fire = start.addingTimeInterval(-Double(offset * 60))
                    guard fire > now else { continue }
                    plans.append(CalendarAlertPlan(id: "classflow_event_\(event.id)_\(item.date)", title: event.title, location: event.location, fireDate: fire))
                }
            }
        }
        return Array(plans.sorted { $0.fireDate < $1.fireDate }.prefix(max(0, limit)))
    }
}
