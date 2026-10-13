import Foundation

/// Pure utility engine for academic calendar date arithmetic and teaching week indexes.
public struct WeekCalculator: Sendable {
    public let timeZone: TimeZone
    public let calendar: Calendar

    public init(timeZoneId: String = "Asia/Shanghai") {
        let tz = TimeZone(identifier: timeZoneId) ?? TimeZone(secondsFromGMT: 8 * 3600)!
        self.timeZone = tz
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = tz
        // First weekday: Monday = 2 in Foundation Calendar (1 is Sunday), but we want ISO Monday
        cal.firstWeekday = 2 // Monday
        self.calendar = cal
    }

    private static let dateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        df.locale = Locale(identifier: "en_US_POSIX")
        return df
    }()

    /// Parses "YYYY-MM-DD" into Date at start of day in configured timezone.
    public func parseDate(_ dateString: String) -> Date? {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        df.timeZone = timeZone
        df.locale = Locale(identifier: "en_US_POSIX")
        return df.date(from: dateString)
    }

    /// Formats Date into "YYYY-MM-DD".
    public func formatDate(_ date: Date) -> String {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        df.timeZone = timeZone
        df.locale = Locale(identifier: "en_US_POSIX")
        return df.string(from: date)
    }

    /// Finds the Monday date of the week containing the semester start date.
    public func mondayOfFirstWeek(semesterStartDate: String) -> Date {
        guard let startDate = parseDate(semesterStartDate) else {
            fatalError("Invalid semester start date format: \(semesterStartDate)")
        }
        let weekday = dayOfWeek(for: startDate) // 1 (Mon) .. 7 (Sun)
        let daysToSubtract = weekday - 1
        return calendar.date(byAdding: .day, value: -daysToSubtract, to: startDate)!
    }

    /// Converts Date to ISO Day of Week: 1 = Monday, 2 = Tuesday, ..., 7 = Sunday.
    public func dayOfWeek(for date: Date) -> Int {
        // Foundation weekday: 1 = Sunday, 2 = Monday, ..., 7 = Saturday
        let fWeekday = calendar.component(.weekday, from: date)
        if fWeekday == 1 {
            return 7 // Sunday
        } else {
            return fWeekday - 1 // Monday = 1, etc.
        }
    }

    /// Calculates 1-indexed teaching week number for a target date relative to the semester start.
    public func weekIndex(for targetDate: Date, semesterStartDate: String) -> Int {
        let firstMonday = mondayOfFirstWeek(semesterStartDate: semesterStartDate)
        let targetWeekday = dayOfWeek(for: targetDate)
        let targetMonday = calendar.date(byAdding: .day, value: -(targetWeekday - 1), to: targetDate)!

        let components = calendar.dateComponents([.day], from: firstMonday, to: targetMonday)
        let daysDiff = components.day ?? 0
        return (daysDiff / 7) + 1
    }

    /// Calculates 1-indexed teaching week number for a target "YYYY-MM-DD" string.
    public func weekIndex(for targetDateString: String, semesterStartDate: String) -> Int? {
        guard let date = parseDate(targetDateString) else { return nil }
        return weekIndex(for: date, semesterStartDate: semesterStartDate)
    }

    /// Computes the exact date for a specific week number and day of week (1..7).
    public func date(forWeek weekNumber: Int, dayOfWeek: Int, semesterStartDate: String) -> Date {
        precondition(weekNumber >= 1, "weekNumber must be >= 1")
        precondition(dayOfWeek >= 1 && dayOfWeek <= 7, "dayOfWeek must be 1..7")
        let firstMonday = mondayOfFirstWeek(semesterStartDate: semesterStartDate)
        let totalDaysToAdd = (weekNumber - 1) * 7 + (dayOfWeek - 1)
        return calendar.date(byAdding: .day, value: totalDaysToAdd, to: firstMonday)!
    }

    /// Computes the "YYYY-MM-DD" string for a specific week number and day of week.
    public func dateString(forWeek weekNumber: Int, dayOfWeek: Int, semesterStartDate: String) -> String {
        formatDate(date(forWeek: weekNumber, dayOfWeek: dayOfWeek, semesterStartDate: semesterStartDate))
    }

    /// Returns the array of 7 date strings [Mon...Sun] for a given teaching week.
    public func dateStringsInWeek(weekNumber: Int, semesterStartDate: String) -> [String] {
        (1...7).map { dateString(forWeek: weekNumber, dayOfWeek: $0, semesterStartDate: semesterStartDate) }
    }
}
