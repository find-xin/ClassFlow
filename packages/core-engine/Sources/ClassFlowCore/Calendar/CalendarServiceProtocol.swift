import Foundation

/// System calendar authorization status.
public enum CalendarAccessStatus: String, Sendable, Codable {
    case notDetermined
    case restricted
    case denied
    case fullAccess

    public var isAuthorized: Bool {
        self == .fullAccess
    }
}

/// Execution outcome returned by platform calendar providers.
public struct CalendarSyncExecutionResult: Sendable {
    public var createdCount: Int
    public var updatedCount: Int
    public var deletedCount: Int
    public var newMappings: [CalendarMapping]
    public var errors: [String]

    public init(
        createdCount: Int = 0,
        updatedCount: Int = 0,
        deletedCount: Int = 0,
        newMappings: [CalendarMapping] = [],
        errors: [String] = []
    ) {
        self.createdCount = createdCount
        self.updatedCount = updatedCount
        self.deletedCount = deletedCount
        self.newMappings = newMappings
        self.errors = errors
    }
}

/// Abstract contract for platform-specific calendar providers (EventKit on macOS, CalendarContract on Android).
public protocol CalendarServiceProtocol: Sendable {
    func requestAccess() async throws -> Bool
    func checkAccess() async -> CalendarAccessStatus
    func executePlan(_ plan: CalendarSyncPlan, inCalendarNamed name: String) async throws -> CalendarSyncExecutionResult
    func clearCalendar(named name: String) async throws -> Int
}
