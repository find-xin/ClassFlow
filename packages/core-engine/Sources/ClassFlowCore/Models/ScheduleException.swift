import Foundation

/// Represents a single-date or temporary deviation from a recurring course schedule.
public struct ScheduleException: Codable, Sendable, Identifiable, Equatable, Hashable {
    public enum ExceptionType: String, Codable, Sendable, CaseIterable {
        /// Single-day class cancellation (停课)
        case cancel
        /// Moved to a different date or section (调课)
        case reschedule
        /// Added makeup class session (补课)
        case makeup
        /// Classroom relocation with same time slot (教室变更)
        case room_change
    }

    public var id: UUID
    public var scheduleId: UUID
    /// Original ISO date ("YYYY-MM-DD") the exception applies to
    public var originalDate: String
    /// Type of schedule override
    public var exceptionType: ExceptionType

    /// Target ISO date if moved ("YYYY-MM-DD")
    public var targetDate: String?
    /// Target day of week (1..7) if moved
    public var targetDayOfWeek: Int?
    /// Target starting section if time changed
    public var targetStartSection: Int?
    /// Target ending section if time changed
    public var targetEndSection: Int?
    /// Target room if classroom changed
    public var targetClassroom: String?
    /// Reason / explanation for the adjustment
    public var reason: String?

    public init(
        id: UUID = UUID(),
        scheduleId: UUID,
        originalDate: String,
        exceptionType: ExceptionType,
        targetDate: String? = nil,
        targetDayOfWeek: Int? = nil,
        targetStartSection: Int? = nil,
        targetEndSection: Int? = nil,
        targetClassroom: String? = nil,
        reason: String? = nil
    ) {
        self.id = id
        self.scheduleId = scheduleId
        self.originalDate = originalDate
        self.exceptionType = exceptionType
        self.targetDate = targetDate
        self.targetDayOfWeek = targetDayOfWeek
        self.targetStartSection = targetStartSection
        self.targetEndSection = targetEndSection
        self.targetClassroom = targetClassroom
        self.reason = reason
    }

    enum CodingKeys: String, CodingKey {
        case id
        case scheduleId
        case originalDate
        case exceptionType
        case type
        case targetDate
        case targetDayOfWeek
        case targetStartSection
        case targetEndSection
        case targetClassroom
        case reason
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        if let directId = try? container.decode(UUID.self, forKey: .id) {
            self.id = directId
        } else if let strId = try? container.decode(String.self, forKey: .id), let parsed = UUID(uuidString: strId) {
            self.id = parsed
        } else {
            self.id = UUID()
        }

        if let directSchedId = try? container.decode(UUID.self, forKey: .scheduleId) {
            self.scheduleId = directSchedId
        } else if let strSchedId = try? container.decode(String.self, forKey: .scheduleId), let parsed = UUID(uuidString: strSchedId) {
            self.scheduleId = parsed
        } else {
            self.scheduleId = UUID()
        }

        self.originalDate = try container.decode(String.self, forKey: .originalDate)

        if let directType = try? container.decode(ExceptionType.self, forKey: .exceptionType) {
            self.exceptionType = directType
        } else if let typeStr = try? container.decode(String.self, forKey: .exceptionType) {
            self.exceptionType = ExceptionType(rawValue: typeStr.lowercased()) ?? .cancel
        } else if let typeStr = try? container.decode(String.self, forKey: .type) {
            self.exceptionType = ExceptionType(rawValue: typeStr.lowercased()) ?? .cancel
        } else {
            self.exceptionType = .cancel
        }

        self.targetDate = try container.decodeIfPresent(String.self, forKey: .targetDate)
        self.targetDayOfWeek = try container.decodeIfPresent(Int.self, forKey: .targetDayOfWeek)
        self.targetStartSection = try container.decodeIfPresent(Int.self, forKey: .targetStartSection)
        self.targetEndSection = try container.decodeIfPresent(Int.self, forKey: .targetEndSection)
        self.targetClassroom = try container.decodeIfPresent(String.self, forKey: .targetClassroom)
        self.reason = try container.decodeIfPresent(String.self, forKey: .reason)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(scheduleId, forKey: .scheduleId)
        try container.encode(originalDate, forKey: .originalDate)
        try container.encode(exceptionType, forKey: .exceptionType)
        try container.encode(exceptionType.rawValue.uppercased(), forKey: .type)
        try container.encodeIfPresent(targetDate, forKey: .targetDate)
        try container.encodeIfPresent(targetDayOfWeek, forKey: .targetDayOfWeek)
        try container.encodeIfPresent(targetStartSection, forKey: .targetStartSection)
        try container.encodeIfPresent(targetEndSection, forKey: .targetEndSection)
        try container.encodeIfPresent(targetClassroom, forKey: .targetClassroom)
        try container.encodeIfPresent(reason, forKey: .reason)
    }
}
