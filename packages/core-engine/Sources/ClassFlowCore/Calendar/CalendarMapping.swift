import Foundation

/// Persistent mapping record linking a ClassFlow course occurrence to a system calendar event.
/// Prevents duplicate entries and enables surgical updates/deletions.
public struct CalendarMapping: Codable, Sendable, Identifiable, Equatable, Hashable {
    public var id: UUID
    public var semesterId: UUID
    public var courseId: UUID
    public var scheduleId: UUID
    /// Target date of the occurrence in YYYY-MM-DD format
    public var occurrenceDate: String
    public var startSection: Int
    public var endSection: Int
    /// Platform-specific identifier: EKEvent.eventIdentifier on macOS, _id on Android
    public var externalEventId: String
    /// SHA-256 fingerprint of the exported event content (title, location, times, alarms)
    public var contentHash: String
    /// ISO 8601 timestamp of synchronization
    public var lastSyncedAt: String

    public init(
        id: UUID = UUID(),
        semesterId: UUID,
        courseId: UUID,
        scheduleId: UUID,
        occurrenceDate: String,
        startSection: Int,
        endSection: Int,
        externalEventId: String,
        contentHash: String,
        lastSyncedAt: String = ISO8601DateFormatter().string(from: Date())
    ) {
        self.id = id
        self.semesterId = semesterId
        self.courseId = courseId
        self.scheduleId = scheduleId
        self.occurrenceDate = occurrenceDate
        self.startSection = startSection
        self.endSection = endSection
        self.externalEventId = externalEventId
        self.contentHash = contentHash
        self.lastSyncedAt = lastSyncedAt
    }

    /// Unique logical key matching a single course period on a given date.
    public var logicalKey: String {
        "\(courseId.uuidString)_\(occurrenceDate)_\(startSection)_\(endSection)"
    }
}
