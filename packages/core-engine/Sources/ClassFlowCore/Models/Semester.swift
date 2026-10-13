import Foundation

/// Represents an academic semester term governing teaching weeks and daily section times.
public struct Semester: Codable, Sendable, Identifiable, Equatable, Hashable {
    public var id: UUID
    public var name: String
    /// ISO 8601 date string ("YYYY-MM-DD") representing the Monday of the first teaching week.
    public var startDate: String
    /// ISO 8601 date string ("YYYY-MM-DD") representing the end date of the semester term.
    public var endDate: String
    /// Total teaching weeks in this semester (typically 16-24 weeks).
    public var totalWeeks: Int
    /// Whether this is the currently active semester in the user's timetable view.
    public var isCurrent: Bool
    /// IANA TimeZone identifier (e.g. "Asia/Shanghai").
    public var timeZone: String
    /// Daily section time definitions.
    public var sections: [SectionTime]

    public init(
        id: UUID = UUID(),
        name: String,
        startDate: String,
        endDate: String,
        totalWeeks: Int = 20,
        isCurrent: Bool = false,
        timeZone: String = "Asia/Shanghai",
        sections: [SectionTime] = []
    ) {
        self.id = id
        self.name = name
        self.startDate = startDate
        self.endDate = endDate
        self.totalWeeks = totalWeeks
        self.isCurrent = isCurrent
        self.timeZone = timeZone
        self.sections = sections
    }

    /// Helper to lookup start and end times for a specific period number.
    public func sectionTime(for period: Int) -> SectionTime? {
        sections.first(where: { $0.section == period })
    }
}
