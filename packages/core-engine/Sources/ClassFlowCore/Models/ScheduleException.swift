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
}
