import Foundation

/// Supported focus modes matching the Pomodoro ToDo (番茄ToDo) specification.
public enum FocusMode: String, Codable, Sendable, CaseIterable {
    case pomodoro = "番茄钟"
    case countUp = "正向计时"
    case countdown = "定目标"

    public var icon: String {
        switch self {
        case .pomodoro: return "timer"
        case .countUp: return "stopwatch"
        case .countdown: return "hourglass"
        }
    }
}

/// Task urgency / priority classification.
public enum TaskPriority: String, Codable, Sendable, CaseIterable {
    case high = "重要且紧急"
    case medium = "日常重要"
    case low = "普通备忘"

    public var colorHex: String {
        switch self {
        case .high: return "#FF5A5F"
        case .medium: return "#E8A87C"
        case .low: return "#56A3A6"
        }
    }
}

/// A structured study task that can be scheduled, linked to timetable courses,
/// and tracked with completed pomodoros.
public struct PomodoroTask: Identifiable, Codable, Sendable, Equatable {
    public let id: UUID
    public var semesterId: UUID?
    public var title: String
    public var courseId: UUID?
    public var courseName: String?
    public var estimatedPomodoros: Int
    public var targetMinutes: Int?
    public var completedPomodoros: Int
    public var totalFocusMinutes: Int
    public var isCompleted: Bool
    public var priority: TaskPriority
    public var notes: String?
    public var startDate: String?
    public var dueDate: String?
    public var frequency: String?
    public var customDaysOfWeek: Set<Int>?
    public var customWeeks: Set<Int>?
    public var endRepeatType: String?
    public var endRepeatUntil: String?
    public var endRepeatCount: Int?
    public var createdAt: Date
    public var completedAt: Date?

    public var effectiveTargetMinutes: Int {
        if let targetMinutes, targetMinutes > 0 {
            return targetMinutes
        }
        return max(15, estimatedPomodoros * 25)
    }

    public init(
        id: UUID = UUID(),
        semesterId: UUID? = nil,
        title: String,
        courseId: UUID? = nil,
        courseName: String? = nil,
        estimatedPomodoros: Int = 2,
        targetMinutes: Int? = nil,
        completedPomodoros: Int = 0,
        totalFocusMinutes: Int = 0,
        isCompleted: Bool = false,
        priority: TaskPriority = .medium,
        notes: String? = nil,
        startDate: String? = nil,
        dueDate: String? = nil,
        frequency: String? = "none",
        customDaysOfWeek: Set<Int>? = nil,
        customWeeks: Set<Int>? = nil,
        endRepeatType: String? = "never",
        endRepeatUntil: String? = nil,
        endRepeatCount: Int? = nil,
        createdAt: Date = Date(),
        completedAt: Date? = nil
    ) {
        self.id = id
        self.semesterId = semesterId
        self.title = title
        self.courseId = courseId
        self.courseName = courseName
        self.targetMinutes = targetMinutes
        if let targetMinutes, targetMinutes > 0 {
            self.estimatedPomodoros = max(1, (targetMinutes + 24) / 25)
        } else {
            self.estimatedPomodoros = max(1, estimatedPomodoros)
        }
        self.completedPomodoros = completedPomodoros
        self.totalFocusMinutes = totalFocusMinutes
        self.isCompleted = isCompleted
        self.priority = priority
        self.notes = notes
        self.startDate = startDate
        self.dueDate = dueDate
        self.frequency = frequency ?? "none"
        self.customDaysOfWeek = customDaysOfWeek
        self.customWeeks = customWeeks
        self.endRepeatType = endRepeatType
        self.endRepeatUntil = endRepeatUntil
        self.endRepeatCount = endRepeatCount
        self.createdAt = createdAt
        self.completedAt = completedAt
    }

    enum CodingKeys: String, CodingKey {
        case id
        case semesterId
        case title
        case courseId
        case courseName
        case estimatedPomodoros
        case targetMinutes
        case completedPomodoros
        case totalFocusMinutes
        case completedMinutes
        case isCompleted
        case priority
        case notes
        case startDate
        case dueDate
        case frequency
        case customDaysOfWeek
        case customWeeks
        case endRepeatType
        case endRepeatUntil
        case endRepeatCount
        case createdAt
        case completedAt
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

        if let directSId = try? container.decode(UUID.self, forKey: .semesterId) {
            self.semesterId = directSId
        } else if let strSId = try? container.decode(String.self, forKey: .semesterId), let parsed = UUID(uuidString: strSId) {
            self.semesterId = parsed
        } else if let strSId = try? container.decode(String.self, forKey: .semesterId), !strSId.isEmpty {
            self.semesterId = Semester.parseOrDeriveUUID(strSId)
        } else {
            self.semesterId = nil
        }

        self.title = try container.decode(String.self, forKey: .title)

        if let directCId = try? container.decode(UUID.self, forKey: .courseId) {
            self.courseId = directCId
        } else if let strCId = try? container.decode(String.self, forKey: .courseId), let parsed = UUID(uuidString: strCId) {
            self.courseId = parsed
        } else {
            self.courseId = nil
        }

        self.courseName = try container.decodeIfPresent(String.self, forKey: .courseName)
        let estPom = try container.decodeIfPresent(Int.self, forKey: .estimatedPomodoros) ?? 2
        self.targetMinutes = try container.decodeIfPresent(Int.self, forKey: .targetMinutes)
        self.estimatedPomodoros = max(1, estPom)
        self.completedPomodoros = try container.decodeIfPresent(Int.self, forKey: .completedPomodoros) ?? 0

        let tfm = try container.decodeIfPresent(Int.self, forKey: .totalFocusMinutes)
        let cm = try container.decodeIfPresent(Int.self, forKey: .completedMinutes)
        self.totalFocusMinutes = tfm ?? cm ?? 0

        self.isCompleted = try container.decodeIfPresent(Bool.self, forKey: .isCompleted) ?? false

        if let pri = try? container.decode(TaskPriority.self, forKey: .priority) {
            self.priority = pri
        } else if let strPri = try? container.decode(String.self, forKey: .priority) {
            switch strPri.lowercased() {
            case "high", "重要且紧急", "高": self.priority = .high
            case "low", "普通备忘", "低": self.priority = .low
            default: self.priority = .medium
            }
        } else {
            self.priority = .medium
        }

        self.notes = try container.decodeIfPresent(String.self, forKey: .notes)
        self.startDate = try container.decodeIfPresent(String.self, forKey: .startDate)
        self.dueDate = try container.decodeIfPresent(String.self, forKey: .dueDate)
        self.frequency = try container.decodeIfPresent(String.self, forKey: .frequency) ?? "none"
        self.customDaysOfWeek = try container.decodeIfPresent(Set<Int>.self, forKey: .customDaysOfWeek)
        self.customWeeks = try container.decodeIfPresent(Set<Int>.self, forKey: .customWeeks)
        self.endRepeatType = try container.decodeIfPresent(String.self, forKey: .endRepeatType) ?? "never"
        self.endRepeatUntil = try container.decodeIfPresent(String.self, forKey: .endRepeatUntil)
        self.endRepeatCount = try container.decodeIfPresent(Int.self, forKey: .endRepeatCount)

        if let dt = try? container.decode(Date.self, forKey: .createdAt) {
            self.createdAt = dt
        } else if let strDt = try? container.decode(String.self, forKey: .createdAt), let parsed = ISO8601DateFormatter().date(from: strDt) {
            self.createdAt = parsed
        } else {
            self.createdAt = Date()
        }

        if let dt = try? container.decode(Date.self, forKey: .completedAt) {
            self.completedAt = dt
        } else if let strDt = try? container.decode(String.self, forKey: .completedAt), let parsed = ISO8601DateFormatter().date(from: strDt) {
            self.completedAt = parsed
        } else {
            self.completedAt = nil
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(title, forKey: .title)
        try container.encodeIfPresent(courseId, forKey: .courseId)
        try container.encodeIfPresent(courseName, forKey: .courseName)
        try container.encode(estimatedPomodoros, forKey: .estimatedPomodoros)
        try container.encodeIfPresent(targetMinutes, forKey: .targetMinutes)
        try container.encode(completedPomodoros, forKey: .completedPomodoros)
        try container.encode(totalFocusMinutes, forKey: .totalFocusMinutes)
        try container.encode(totalFocusMinutes, forKey: .completedMinutes)
        try container.encode(isCompleted, forKey: .isCompleted)
        try container.encode(priority, forKey: .priority)
        try container.encodeIfPresent(notes, forKey: .notes)
        try container.encodeIfPresent(startDate, forKey: .startDate)
        try container.encodeIfPresent(dueDate, forKey: .dueDate)
        try container.encode(frequency, forKey: .frequency)
        try container.encodeIfPresent(customDaysOfWeek, forKey: .customDaysOfWeek)
        try container.encodeIfPresent(customWeeks, forKey: .customWeeks)
        try container.encodeIfPresent(endRepeatType, forKey: .endRepeatType)
        try container.encodeIfPresent(endRepeatUntil, forKey: .endRepeatUntil)
        try container.encodeIfPresent(endRepeatCount, forKey: .endRepeatCount)
        try container.encode(createdAt, forKey: .createdAt)
        try container.encodeIfPresent(completedAt, forKey: .completedAt)
    }

    public static func defaultSampleTasks() -> [PomodoroTask] {
        []
    }
}

/// Record of an individual completed focus session for analytics and historical insights.
public struct PomodoroRecord: Identifiable, Codable, Sendable, Equatable {
    public let id: UUID
    public var taskId: UUID?
    public var taskTitle: String
    public var courseName: String?
    public var focusMode: FocusMode
    public var durationMinutes: Int
    public var completedAt: Date
    public var tag: String

    public init(
        id: UUID = UUID(),
        taskId: UUID? = nil,
        taskTitle: String,
        courseName: String? = nil,
        focusMode: FocusMode = .pomodoro,
        durationMinutes: Int,
        completedAt: Date = Date(),
        tag: String = "日常自习"
    ) {
        self.id = id
        self.taskId = taskId
        self.taskTitle = taskTitle
        self.courseName = courseName
        self.focusMode = focusMode
        self.durationMinutes = max(1, durationMinutes)
        self.completedAt = completedAt
        self.tag = tag
    }

    enum CodingKeys: String, CodingKey {
        case id
        case taskId
        case taskTitle
        case courseName
        case focusMode
        case durationMinutes
        case completedAt
        case tag
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

        if let directTId = try? container.decode(UUID.self, forKey: .taskId) {
            self.taskId = directTId
        } else if let strTId = try? container.decode(String.self, forKey: .taskId), let parsed = UUID(uuidString: strTId) {
            self.taskId = parsed
        } else {
            self.taskId = nil
        }

        self.taskTitle = try container.decode(String.self, forKey: .taskTitle)
        self.courseName = try container.decodeIfPresent(String.self, forKey: .courseName)
        self.focusMode = (try? container.decode(FocusMode.self, forKey: .focusMode)) ?? .pomodoro
        self.durationMinutes = max(1, try container.decodeIfPresent(Int.self, forKey: .durationMinutes) ?? 25)

        if let dt = try? container.decode(Date.self, forKey: .completedAt) {
            self.completedAt = dt
        } else if let strDt = try? container.decode(String.self, forKey: .completedAt) {
            let iso = ISO8601DateFormatter()
            let customDf = DateFormatter()
            customDf.dateFormat = "yyyy-MM-dd HH:mm"
            self.completedAt = iso.date(from: strDt) ?? customDf.date(from: strDt) ?? Date()
        } else {
            self.completedAt = Date()
        }

        self.tag = try container.decodeIfPresent(String.self, forKey: .tag) ?? "日常自习"
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encodeIfPresent(taskId, forKey: .taskId)
        try container.encode(taskTitle, forKey: .taskTitle)
        try container.encodeIfPresent(courseName, forKey: .courseName)
        try container.encode(focusMode, forKey: .focusMode)
        try container.encode(durationMinutes, forKey: .durationMinutes)
        try container.encode(completedAt, forKey: .completedAt)
        try container.encode(tag, forKey: .tag)
    }

    public static func defaultSampleRecords() -> [PomodoroRecord] {
        []
    }
}
