import Foundation

/// Defines a regular recurring weekly class session for a course.
public struct CourseSchedule: Codable, Sendable, Identifiable, Equatable, Hashable {
    public var id: UUID
    public var courseId: UUID
    /// 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
    public var dayOfWeek: Int
    /// Starting section period (e.g. 1)
    public var startSection: Int
    /// Ending section period (e.g. 2 for a double period)
    public var endSection: Int
    /// Classroom location or lab name
    public var classroom: String?
    /// Weekly recurrence rule (all, odd, even, custom)
    public var weekPattern: WeekPattern

    public init(
        id: UUID = UUID(),
        courseId: UUID = UUID(),
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        classroom: String? = nil,
        weekPattern: WeekPattern = .all
    ) {
        precondition(dayOfWeek >= 1 && dayOfWeek <= 7, "dayOfWeek must be between 1 (Monday) and 7 (Sunday)")
        precondition(startSection >= 1, "startSection must be >= 1")
        precondition(endSection >= startSection, "endSection must be >= startSection")
        self.id = id
        self.courseId = courseId
        self.dayOfWeek = dayOfWeek
        self.startSection = startSection
        self.endSection = endSection
        self.classroom = classroom
        self.weekPattern = weekPattern
    }

    /// Number of continuous periods this session occupies.
    public var sectionSpan: Int {
        endSection - startSection + 1
    }
}
