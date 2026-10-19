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

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let directId = try? container.decode(UUID.self, forKey: .id) {
            self.id = directId
        } else if let idStr = try? container.decode(String.self, forKey: .id), let parsed = UUID(uuidString: idStr) {
            self.id = parsed
        } else {
            self.id = UUID()
        }

        if let directSemId = try? container.decode(UUID.self, forKey: .semesterId) {
            self.semesterId = directSemId
        } else if let semIdStr = try? container.decode(String.self, forKey: .semesterId), let parsed = UUID(uuidString: semIdStr) {
            self.semesterId = parsed
        } else if let semIdStr = try? container.decode(String.self, forKey: .semesterId), !semIdStr.isEmpty {
            self.semesterId = Semester.parseOrDeriveUUID(semIdStr)
        } else {
            self.semesterId = UUID()
        }

        self.name = try container.decode(String.self, forKey: .name)
        self.teacher = try container.decodeIfPresent(String.self, forKey: .teacher)
        self.color = (try? container.decodeIfPresent(String.self, forKey: .color)) ?? "#4F46E5"
        self.notes = try container.decodeIfPresent(String.self, forKey: .notes)
        self.schedules = (try? container.decodeIfPresent([CourseSchedule].self, forKey: .schedules)) ?? []
    }
}
