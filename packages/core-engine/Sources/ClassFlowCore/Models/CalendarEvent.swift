import Foundation

/// Forward-compatible domain entity for generic calendar events (meetings, exams, activities).
public struct CalendarEvent: Codable, Sendable, Identifiable, Equatable, Hashable {
    public var id: UUID
    public var calendarId: UUID
    public var semesterId: UUID?
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
    public var startDate: String?
    public var endDate: String?
    public var intervalWeeks: Int?
    public var endRepeatType: String?
    public var endRepeatUntil: String?
    public var endRepeatCount: Int?

    public init(
        id: UUID = UUID(),
        calendarId: UUID = UUID(),
        semesterId: UUID? = nil,
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
        customDaysOfWeek: Set<Int>? = nil,
        startDate: String? = nil,
        endDate: String? = nil,
        intervalWeeks: Int? = 1,
        endRepeatType: String? = "never",
        endRepeatUntil: String? = nil,
        endRepeatCount: Int? = nil
    ) {
        self.id = id
        self.calendarId = calendarId
        self.semesterId = semesterId
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
        self.startDate = startDate
        self.endDate = endDate
        self.intervalWeeks = intervalWeeks
        self.endRepeatType = endRepeatType
        self.endRepeatUntil = endRepeatUntil
        self.endRepeatCount = endRepeatCount
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let directId = try? container.decode(UUID.self, forKey: .id) {
            self.id = directId
        } else if let idStr = try? container.decode(String.self, forKey: .id), let parsed = UUID(uuidString: idStr) {
            self.id = parsed
        } else {
            self.id = UUID()
        }
        if let directCalId = try? container.decode(UUID.self, forKey: .calendarId) {
            self.calendarId = directCalId
        } else if let calIdStr = try? container.decode(String.self, forKey: .calendarId), let parsed = UUID(uuidString: calIdStr) {
            self.calendarId = parsed
        } else {
            self.calendarId = UUID()
        }
        if let directSemId = try? container.decode(UUID.self, forKey: .semesterId) {
            self.semesterId = directSemId
        } else if let semIdStr = try? container.decode(String.self, forKey: .semesterId), let parsed = UUID(uuidString: semIdStr) {
            self.semesterId = parsed
        } else if let semIdStr = try? container.decode(String.self, forKey: .semesterId), !semIdStr.isEmpty {
            self.semesterId = Semester.parseOrDeriveUUID(semIdStr)
        } else {
            self.semesterId = nil
        }
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
        self.startDate = try container.decodeIfPresent(String.self, forKey: .startDate)
        self.endDate = try container.decodeIfPresent(String.self, forKey: .endDate)
        self.intervalWeeks = try container.decodeIfPresent(Int.self, forKey: .intervalWeeks)
        self.endRepeatType = try container.decodeIfPresent(String.self, forKey: .endRepeatType)
        self.endRepeatUntil = try container.decodeIfPresent(String.self, forKey: .endRepeatUntil)
        self.endRepeatCount = try container.decodeIfPresent(Int.self, forKey: .endRepeatCount)
    }
}
