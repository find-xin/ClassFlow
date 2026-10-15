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
        createdAt: Date = Date(),
        completedAt: Date? = nil
    ) {
        self.id = id
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
        self.createdAt = createdAt
        self.completedAt = completedAt
    }

    public static func defaultSampleTasks() -> [PomodoroTask] {
        [
            PomodoroTask(
                title: "高等数学 · 习题册第4章复习",
                courseName: "高等数学",
                estimatedPomodoros: 3,
                completedPomodoros: 2,
                totalFocusMinutes: 50,
                priority: .high,
                notes: "重点攻克多元微分学与拉格朗日乘数法"
            ),
            PomodoroTask(
                title: "英语六级 · 听力真题精听与词汇整理",
                courseName: "大学英语",
                estimatedPomodoros: 2,
                completedPomodoros: 1,
                totalFocusMinutes: 25,
                priority: .medium,
                notes: "完成 2024 年 12 月卷一听力第二套"
            ),
            PomodoroTask(
                title: "计算机网络 · Lab 2 路由协议模拟",
                courseName: "计算机网络",
                estimatedPomodoros: 4,
                completedPomodoros: 0,
                totalFocusMinutes: 0,
                priority: .high,
                notes: "实现 RIP 协议收敛仿真并在周五前提交代码"
            ),
            PomodoroTask(
                title: "近代史纲要 · 期末论文参考文献查阅",
                courseName: "中国近现代史纲要",
                estimatedPomodoros: 2,
                completedPomodoros: 2,
                totalFocusMinutes: 50,
                isCompleted: true,
                priority: .low,
                notes: "已搜集 5 篇核心期刊文献",
                completedAt: Date()
            )
        ]
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

    public static func defaultSampleRecords() -> [PomodoroRecord] {
        let now = Date()
        return [
            PomodoroRecord(
                taskTitle: "高等数学 · 习题册第4章复习",
                courseName: "高等数学",
                focusMode: .pomodoro,
                durationMinutes: 25,
                completedAt: now.addingTimeInterval(-7200),
                tag: "课后作业"
            ),
            PomodoroRecord(
                taskTitle: "高等数学 · 习题册第4章复习",
                courseName: "高等数学",
                focusMode: .pomodoro,
                durationMinutes: 25,
                completedAt: now.addingTimeInterval(-3600),
                tag: "课后作业"
            ),
            PomodoroRecord(
                taskTitle: "英语六级 · 听力真题精听与词汇整理",
                courseName: "大学英语",
                focusMode: .pomodoro,
                durationMinutes: 25,
                completedAt: now.addingTimeInterval(-1800),
                tag: "四六级"
            )
        ]
    }
}
