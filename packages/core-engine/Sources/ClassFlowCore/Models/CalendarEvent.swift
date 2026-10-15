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
    /// Visual theme hex color
    public var color: String
    /// Optional section start
    public var startSection: Int?
    /// Optional section end
    public var endSection: Int?
    /// Minutes before start to notify user
    public var alertMinutesBefore: Int?
    /// Recurrence pattern (none, daily, weekly, biweekly, workdays, monthly, customWeeks)
    public var frequency: String?
    /// Specific semester teaching weeks if frequency == "customWeeks"
    public var customWeeks: Set<Int>?
    /// Specific days of week if frequency == "customDays" (1 = Monday ... 7 = Sunday)
    public var customDaysOfWeek: Set<Int>?

    public init(
        id: UUID = UUID(),
        calendarId: UUID = UUID(),
        title: String,
        startTime: String,
        endTime: String,
        isAllDay: Bool = false,
        location: String? = nil,
        description: String? = nil,
        recurrenceRule: String? = nil,
        color: String = "#FF5A5F",
        startSection: Int? = nil,
        endSection: Int? = nil,
        alertMinutesBefore: Int? = nil,
        frequency: String? = "none",
        customWeeks: Set<Int>? = nil,
        customDaysOfWeek: Set<Int>? = nil
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
        self.color = color
        self.startSection = startSection
        self.endSection = endSection
        self.alertMinutesBefore = alertMinutesBefore
        self.frequency = frequency
        self.customWeeks = customWeeks
        self.customDaysOfWeek = customDaysOfWeek
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.calendarId = try container.decode(UUID.self, forKey: .calendarId)
        self.title = try container.decode(String.self, forKey: .title)
        self.startTime = try container.decode(String.self, forKey: .startTime)
        self.endTime = try container.decode(String.self, forKey: .endTime)
        self.isAllDay = try container.decode(Bool.self, forKey: .isAllDay)
        self.location = try container.decodeIfPresent(String.self, forKey: .location)
        self.description = try container.decodeIfPresent(String.self, forKey: .description)
        self.recurrenceRule = try container.decodeIfPresent(String.self, forKey: .recurrenceRule)
        self.color = try container.decodeIfPresent(String.self, forKey: .color) ?? "#FF5A5F"
        self.startSection = try container.decodeIfPresent(Int.self, forKey: .startSection)
        self.endSection = try container.decodeIfPresent(Int.self, forKey: .endSection)
        self.alertMinutesBefore = try container.decodeIfPresent(Int.self, forKey: .alertMinutesBefore)
        self.frequency = try container.decodeIfPresent(String.self, forKey: .frequency) ?? "none"
        self.customWeeks = try container.decodeIfPresent(Set<Int>.self, forKey: .customWeeks)
        self.customDaysOfWeek = try container.decodeIfPresent(Set<Int>.self, forKey: .customDaysOfWeek)
    }
}
