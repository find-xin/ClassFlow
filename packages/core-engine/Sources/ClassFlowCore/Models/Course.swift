import Foundation

/// Represents an educational subject/course in a semester.
public struct Course: Codable, Sendable, Identifiable, Equatable, Hashable {
    public var id: UUID
    public var semesterId: UUID
    public var name: String
    public var teacher: String?
    /// Hex color string (e.g. "#4F46E5")
    public var color: String
    public var notes: String?
    /// Multiple recurring class sessions during the week
    public var schedules: [CourseSchedule]

    public init(
        id: UUID = UUID(),
        semesterId: UUID,
        name: String,
        teacher: String? = nil,
        color: String = "#4F46E5",
        notes: String? = nil,
        schedules: [CourseSchedule] = []
    ) {
        self.id = id
        self.semesterId = semesterId
        self.name = name
        self.teacher = teacher
        self.color = color
        self.notes = notes
        self.schedules = schedules
    }
}
