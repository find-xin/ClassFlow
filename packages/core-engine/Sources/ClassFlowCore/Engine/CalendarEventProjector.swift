import Foundation

/// Recurrence and day clipping shared by MCP queries, native views, widgets and reminders.
public struct CalendarEventProjector: Sendable {
    public let timeZone: TimeZone
    public init(timeZone: TimeZone = .current) {
        self.timeZone = timeZone
    }

    var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar
    }
    var formatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.isLenient = false
        return formatter
    }

    public func project(
        _ event: CalendarEvent, semester: Semester, week: Int, exceptions: [ScheduleException] = []
    ) -> [TimelineItem] {
        project(events: [event], semester: semester, week: week, exceptions: exceptions)
    }

    /// Projects one data snapshot, preserving input event order and occurrence identity.
    /// Date resources and exception indexes live only for this call and are safe across callers.
    public func project(
        events: [CalendarEvent], semester: Semester, week: Int,
        exceptions: [ScheduleException] = []
    ) -> [TimelineItem] {
        guard !events.isEmpty else { return [] }
        let calculator = WeekCalculator(timeZoneId: timeZone.identifier)
        let dates = calculator.dateStringsInWeek(weekNumber: week, semesterStartDate: semester.startDate)
        let dateFormatter = formatter
        let calendar = self.calendar
        guard let first = dates.first.flatMap({ dateFormatter.date(from: $0) }),
            let last = dates.last.flatMap({ dateFormatter.date(from: $0) })
        else { return [] }
        let dayIndices = Dictionary(uniqueKeysWithValues: dates.enumerated().map { ($0.element, $0.offset) })
        var exceptionIndex: [UUID: [String: ScheduleException]] = [:]
        for exception in exceptions {
            // Preserve the original first-match behavior for duplicate legacy exceptions.
            if exceptionIndex[exception.scheduleId]?[exception.originalDate] == nil {
                exceptionIndex[exception.scheduleId, default: [:]][exception.originalDate] = exception
            }
        }
        let context = WeekContext(
            first: first, last: last, lastKey: dates[dates.count - 1],
            dayIndices: dayIndices, exceptions: exceptionIndex,
            formatter: dateFormatter, calendar: calendar)
        return events.flatMap { project($0, semester: semester, week: week, context: context) }
    }

    /// Transient resources for one synchronous batch; never retained or shared between threads.
    private struct WeekContext {
        let first: Date
        let last: Date
        let lastKey: String
        let dayIndices: [String: Int]
        let exceptions: [UUID: [String: ScheduleException]]
        let formatter: DateFormatter
        let calendar: Calendar
    }

    private func project(
        _ event: CalendarEvent, semester: Semester, week: Int,
        context: WeekContext
    ) -> [TimelineItem] {
        let dateFormatter = context.formatter
        let calendar = context.calendar
        let base = event.startDate ?? String(event.startTime.prefix(10))
        guard let baseDate = dateFormatter.date(from: base) else { return [] }
        func time(_ text: String) -> String {
            text.count >= 16 ? String(text.dropFirst(11).prefix(5)) : String(text.prefix(5))
        }
        let startClock =
            event.timeMode == "sections"
            ? semester.sectionTime(for: event.startSection ?? 1)?.startTime ?? time(event.startTime)
            : time(event.startTime)
        let endClock =
            event.timeMode == "sections"
            ? semester.sectionTime(for: event.endSection ?? event.startSection ?? 1)?.endTime
                ?? time(event.endTime) : time(event.endTime)
        let frequency = Self.normalizedFrequency(event.frequency)
        let endKey =
            frequency == "none"
            ? event.endDate ?? String(event.endTime.prefix(10)) : String(event.endTime.prefix(10))
        let durationStart =
            frequency == "none"
            ? baseDate : dateFormatter.date(from: String(event.startTime.prefix(10))) ?? baseDate
        let span = max(
            0,
            dateFormatter.date(from: endKey).map {
                calendar.dateComponents([.day], from: durationStart, to: $0).day ?? 0
            } ?? 0)
        var items: [TimelineItem] = []
        for occurrence in occurrenceDates(
            event, semester: semester, through: context.lastKey, formatter: dateFormatter, calendar: calendar)
        {
            guard let origin = dateFormatter.date(from: occurrence) else { continue }
            let exception = context.exceptions[event.id]?[occurrence]
            if exception?.exceptionType == .cancel && exception?.reason == "单次删除" { continue }
            // Visit at most seven visible days even for a years-long event. Calendar
            // arithmetic preserves local day boundaries through daylight-saving changes.
            let firstOffset = max(
                0, calendar.dateComponents([.day], from: origin, to: context.first).day ?? 0)
            let lastOffset = min(
                span, calendar.dateComponents([.day], from: origin, to: context.last).day ?? 0)
            guard firstOffset <= lastOffset else { continue }
            for offset in firstOffset...lastOffset {
                guard let day = calendar.date(byAdding: .day, value: offset, to: origin) else { continue }
                let date = dateFormatter.string(from: day)
                guard let index = context.dayIndices[date] else { continue }
                let start = event.isAllDay || offset > 0 ? "00:00" : startClock
                let end = event.isAllDay || offset < span ? "23:59" : endClock
                if !event.isAllDay && start >= end { continue }
                let startSection =
                    event.timeMode == "sections"
                    ? event.startSection : semester.sections.first { $0.endTime > start }?.section
                let endSection =
                    event.timeMode == "sections"
                    ? event.endSection : semester.sections.last { $0.startTime < end }?.section
                items.append(
                    TimelineItem(
                        id: "event_occ_\(event.id)_\(date)", sourceType: .calendarEvent,
                        calendarEventId: event.id, title: event.title, location: event.location, date: date,
                        dayOfWeek: index + 1,
                        startSection: startSection ?? 1,
                        endSection: max(startSection ?? 1, endSection ?? startSection ?? 1),
                        startTime: start, endTime: end, weekNumber: week, isCurrentWeek: true,
                        status: exception?.exceptionType == .cancel ? .cancelled : .normal,
                        color: event.color, notes: event.description, isAllDay: event.isAllDay,
                        isRecurring: frequency != "none"))
            }
        }
        return items
    }
}
