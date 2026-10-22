import Foundation

// Recurrence enumeration is separate from visible-week clipping. Counts always start
// at the series origin, including occurrences outside the displayed week.
extension CalendarEventProjector {
    private static let frequencyAliases: [String: String] = [
        "不重复": "none", "每天": "daily", "工作日": "workdays", "周末": "weekends", "每周": "weekly", "每两周": "biweekly",
        "每月": "monthly", "自定义": "customDays", "自定义星期": "customDays", "自定义每周某几天": "customDays",
        "自定义周次": "customWeeks",
    ]

    public static func normalizedFrequency(_ value: String?) -> String {
        let raw = value ?? "none"
        return Self.frequencyAliases[raw] ?? raw
    }
    public func occurrenceDates(_ event: CalendarEvent, semester: Semester, through: String) -> [String] {
        occurrenceDates(event, semester: semester, through: through, formatter: formatter, calendar: calendar)
    }

    // A projection batch owns its formatter; no shared mutable formatter or global lock.
    func occurrenceDates(
        _ event: CalendarEvent, semester: Semester, through: String,
        formatter dateFormatter: DateFormatter, calendar: Calendar
    ) -> [String] {
        let start = event.startDate ?? String(event.startTime.prefix(10))
        guard let first = dateFormatter.date(from: start), let last = dateFormatter.date(from: through),
            first <= last
        else { return [] }
        let frequency = Self.normalizedFrequency(event.frequency)
        if frequency == "none" { return [start] }
        let calculator = WeekCalculator(timeZoneId: timeZone.identifier)
        let baseWeek = calculator.weekIndex(for: start, semesterStartDate: semester.startDate) ?? 1
        let baseDay = (calendar.component(.weekday, from: first) + 5) % 7 + 1
        let interval = frequency == "biweekly" ? 2 : max(1, event.intervalWeeks ?? 1)
        let monthlyDay = calendar.component(.day, from: first)
        var result: [String] = []
        var date = first
        while date <= last {
            let key = dateFormatter.string(from: date)
            if let cap = event.endDate, key > cap { break }
            if event.endRepeatType == "untilDate", let cap = event.endRepeatUntil, key > cap { break }
            if event.endRepeatType == "count", result.count >= (event.endRepeatCount ?? 0) { break }
            let day = (calendar.component(.weekday, from: date) + 5) % 7 + 1
            let week = calculator.weekIndex(for: key, semesterStartDate: semester.startDate) ?? 0
            var matches: Bool
            switch frequency {
            case "daily": matches = true
            case "workdays": matches = day <= 5
            case "weekends": matches = day >= 6
            case "weekly", "biweekly", "customDays", "customWeeks":
                if let days = event.customDaysOfWeek, !days.isEmpty {
                    matches = days.contains(day)
                } else {
                    matches = day == baseDay
                }
            case "monthly": matches = calendar.component(.day, from: date) == monthlyDay
            default: matches = false
            }
            // Android/legacy payloads encode unused filters as [] rather than null.
            // Empty filters add no restriction; nonempty filters retain their original semantics.
            if ["daily", "workdays", "weekends"].contains(frequency),
                let days = event.customDaysOfWeek, !days.isEmpty
            {
                matches = days.contains(day)
            }
            if let weeks = event.customWeeks, !weeks.isEmpty { matches = matches && weeks.contains(week) }
            if interval > 1 { matches = matches && (week - baseWeek) % interval == 0 }
            if matches { result.append(key) }
            guard let next = calendar.date(byAdding: .day, value: 1, to: date) else { break }
            date = next
        }
        return result
    }

}
