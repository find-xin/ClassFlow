import Foundation
import ClassFlowCore

/// Local runtime state shared by the desktop UI and MCP. Never uses a timetable profile ID.
public struct FocusSession: Codable, Sendable, Equatable, Identifiable {
    public var id: UUID = UUID()
    public var taskId: UUID?
    public var title: String
    public var mode: FocusMode = .pomodoro
    public var durationMinutes: Int
    public var startedAt: Date
    public var accumulatedSeconds: Int = 0
    public var pausedAt: Date?
    public var courseId: UUID?
    public var courseName: String?
    public var location: String?

    public func elapsedSeconds(at now: Date) -> Int {
        let elapsed = accumulatedSeconds + max(0, Int((pausedAt ?? now).timeIntervalSince(startedAt)))
        return mode == .countUp ? elapsed : min(durationMinutes * 60, elapsed)
    }
}
