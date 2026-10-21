import Foundation

/// Recurrence and day clipping shared by MCP queries, native views, widgets and reminders.
public struct CalendarEventProjector: Sendable {
    public let timeZone: TimeZone
    public init(timeZone: TimeZone = .current) { self.timeZone = timeZone }
    private var calendar: Calendar { var c = Calendar(identifier: .gregorian); c.timeZone = timeZone; return c }
    private var formatter: DateFormatter {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = timeZone; f.dateFormat = "yyyy-MM-dd"; f.isLenient = false; return f
    }
    public static func normalizedFrequency(_ value: String?) -> String {
        let raw = value ?? "none"
        return ["不重复": "none", "每天": "daily", "工作日": "workdays", "周末": "weekends", "每周": "weekly", "每两周": "biweekly", "每月": "monthly", "自定义": "customDays", "自定义星期": "customDays", "自定义每周某几天": "customDays", "自定义周次": "customWeeks"][raw] ?? raw
    }
    public func occurrenceDates(_ event: CalendarEvent, semester: Semester, through: String) -> [String] {
        let f = formatter, cal = calendar
        let start = event.startDate ?? String(event.startTime.prefix(10))
        guard let first = f.date(from: start), let last = f.date(from: through), first <= last else { return [] }
        let frequency = Self.normalizedFrequency(event.frequency)
        if frequency == "none" { return [start] }
        let calculator = WeekCalculator(timeZoneId: timeZone.identifier)
        let baseWeek = calculator.weekIndex(for: start, semesterStartDate: semester.startDate) ?? 1
        let baseDay = (cal.component(.weekday, from: first) + 5) % 7 + 1
        var result: [String] = []
        var date = first
        while date <= last {
            let key = f.string(from: date)
            if let cap = event.endDate, key > cap { break }
            if event.endRepeatType == "untilDate", let cap = event.endRepeatUntil, key > cap { break }
            if event.endRepeatType == "count", result.count >= (event.endRepeatCount ?? 0) { break }
            let day = (cal.component(.weekday, from: date) + 5) % 7 + 1
            let week = calculator.weekIndex(for: key, semesterStartDate: semester.startDate) ?? 0
            let interval = frequency == "biweekly" ? 2 : max(1, event.intervalWeeks ?? 1)
            var matches: Bool
            switch frequency {
            case "daily": matches = true
            case "workdays": matches = day <= 5
            case "weekends": matches = day >= 6
            case "weekly", "biweekly", "customDays", "customWeeks":
                matches = event.customDaysOfWeek?.contains(day) ?? (day == baseDay)
            case "monthly": matches = cal.component(.day, from: date) == cal.component(.day, from: first)
            default: matches = false
            }
            if ["daily", "workdays", "weekends"].contains(frequency), let days = event.customDaysOfWeek { matches = days.contains(day) }
            if let weeks = event.customWeeks { matches = matches && weeks.contains(week) }
            if interval > 1 { matches = matches && (week - baseWeek) % interval == 0 }
            if matches { result.append(key) }
            guard let next = cal.date(byAdding: .day, value: 1, to: date) else { break }; date = next
        }
        return result
    }

    public func project(_ event: CalendarEvent, semester: Semester, week: Int, exceptions: [ScheduleException] = []) -> [TimelineItem] {
        let calculator = WeekCalculator(timeZoneId: timeZone.identifier)
        let dates = calculator.dateStringsInWeek(weekNumber: week, semesterStartDate: semester.startDate)
        guard let last = dates.last else { return [] }
        let f = formatter, cal = calendar
        let base = event.startDate ?? String(event.startTime.prefix(10))
        guard let baseDate = f.date(from: base) else { return [] }
        func time(_ text: String) -> String { text.count >= 16 ? String(text.dropFirst(11).prefix(5)) : String(text.prefix(5)) }
        let startClock = event.timeMode == "sections" ? semester.sectionTime(for: event.startSection ?? 1)?.startTime ?? time(event.startTime) : time(event.startTime)
        let endClock = event.timeMode == "sections" ? semester.sectionTime(for: event.endSection ?? event.startSection ?? 1)?.endTime ?? time(event.endTime) : time(event.endTime)
        let endKey = Self.normalizedFrequency(event.frequency) == "none" ? event.endDate ?? String(event.endTime.prefix(10)) : String(event.endTime.prefix(10))
        let durationStart = Self.normalizedFrequency(event.frequency) == "none" ? baseDate : f.date(from: String(event.startTime.prefix(10))) ?? baseDate
        let span = max(0, f.date(from: endKey).map { cal.dateComponents([.day], from: durationStart, to: $0).day ?? 0 } ?? 0)
        var items: [TimelineItem] = []
        for occurrence in occurrenceDates(event, semester: semester, through: last) {
            guard let origin = f.date(from: occurrence) else { continue }
            let exception = exceptions.first { $0.scheduleId == event.id && $0.originalDate == occurrence }
            if exception?.exceptionType == .cancel && exception?.reason == "单次删除" { continue }
            for offset in 0...span {
                guard let day = cal.date(byAdding: .day, value: offset, to: origin) else { continue }
                let date = f.string(from: day)
                guard let index = dates.firstIndex(of: date) else { continue }
                let start = event.isAllDay || offset > 0 ? "00:00" : startClock
                let end = event.isAllDay || offset < span ? "23:59" : endClock
                if !event.isAllDay && start >= end { continue }
                let startSection = event.timeMode == "sections" ? event.startSection : semester.sections.first { $0.endTime > start }?.section
                let endSection = event.timeMode == "sections" ? event.endSection : semester.sections.last { $0.startTime < end }?.section
                items.append(TimelineItem(id: "event_occ_\(event.id)_\(date)", sourceType: .calendarEvent,
                    calendarEventId: event.id, title: event.title, location: event.location, date: date, dayOfWeek: index + 1,
                    startSection: startSection ?? 1, endSection: max(startSection ?? 1, endSection ?? startSection ?? 1),
                    startTime: start, endTime: end, weekNumber: week, isCurrentWeek: true,
                    status: exception?.exceptionType == .cancel ? .cancelled : .normal,
                    color: event.color, notes: event.description, isAllDay: event.isAllDay, isRecurring: Self.normalizedFrequency(event.frequency) != "none"))
            }
        }
        return items
    }
}
