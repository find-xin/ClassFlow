import Foundation

/// Forward-compatible domain entity for generic calendar events (meetings, exams, activities).
public struct CalendarEvent: Codable, Sendable, Identifiable, Equatable, Hashable {
    public var id: UUID
    public var calendarId: UUID
    public var title: String
    /// ISO 8601 DateTime string
    public var startTime: String
    /// ISO 8601 DateTime string
    public var endTime: String
    public var isAllDay: Bool
    public var location: String?
    public var description: String?
    /// RFC 5545 RRULE string if recurring
    public var recurrenceRule: String?

    public init(
        id: UUID = UUID(),
        calendarId: UUID = UUID(),
        title: String,
        startTime: String,
        endTime: String,
        isAllDay: Bool = false,
        location: String? = nil,
        description: String? = nil,
        recurrenceRule: String? = nil
    ) {
        self.id = id
        self.calendarId = calendarId
        self.title = title
        self.startTime = startTime
        self.endTime = endTime
        self.isAllDay = isAllDay
        self.location = location
        self.description = description
        self.recurrenceRule = recurrenceRule
    }
}
