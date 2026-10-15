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
    /// Teacher specifically assigned to this session (overrides course default teacher if present)
    public var teacher: String?
    /// Weekly recurrence rule (all, odd, even, custom)
    public var weekPattern: WeekPattern
    /// Optional custom start time override (e.g. "08:30")
    public var customStartTime: String?
    /// Optional custom end time override (e.g. "10:15")
    public var customEndTime: String?

    public init(
        id: UUID = UUID(),
        courseId: UUID = UUID(),
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        classroom: String? = nil,
        teacher: String? = nil,
        weekPattern: WeekPattern = .all,
        customStartTime: String? = nil,
        customEndTime: String? = nil
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
        self.teacher = teacher
        self.weekPattern = weekPattern
        self.customStartTime = customStartTime
        self.customEndTime = customEndTime
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.courseId = try container.decode(UUID.self, forKey: .courseId)
        self.dayOfWeek = try container.decode(Int.self, forKey: .dayOfWeek)
        self.startSection = try container.decode(Int.self, forKey: .startSection)
        self.endSection = try container.decode(Int.self, forKey: .endSection)
        self.classroom = try container.decodeIfPresent(String.self, forKey: .classroom)
        self.teacher = try container.decodeIfPresent(String.self, forKey: .teacher)
        self.weekPattern = try container.decode(WeekPattern.self, forKey: .weekPattern)
        self.customStartTime = try container.decodeIfPresent(String.self, forKey: .customStartTime)
        self.customEndTime = try container.decodeIfPresent(String.self, forKey: .customEndTime)
    }

    /// Number of continuous periods this session occupies.
    public var sectionSpan: Int {
        endSection - startSection + 1
    }
}
