import Foundation
import CryptoKit

/// Generate one successor on completion; stable IDs make completing/restoring/recompleting replay safe.
public enum TaskRecurrence {
    public static func next(after task: PomodoroTask, semester: Semester, timeZone: TimeZone = .current) -> PomodoroTask? {
        guard (task.frequency ?? "none") != "none", task.endRepeatType != "count" || (task.endRepeatCount ?? 0) > 1 else { return nil }
        let formatter = DateFormatter(); formatter.locale = Locale(identifier: "en_US_POSIX"); formatter.timeZone = timeZone; formatter.dateFormat = "yyyy-MM-dd"
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = timeZone
        let anchor = task.startDate ?? task.dueDate.map { String($0.prefix(10)) } ?? formatter.string(from: task.createdAt)
        guard let origin = formatter.date(from: anchor), let horizon = calendar.date(byAdding: .year, value: 5, to: origin) else { return nil }
        let event = CalendarEvent(semesterId: semester.id, title: task.title, startTime: anchor + "T09:00:00", endTime: anchor + "T10:00:00",
            frequency: task.frequency, customWeeks: task.customWeeks, customDaysOfWeek: task.customDaysOfWeek, startDate: anchor,
            endRepeatType: task.endRepeatType == "count" ? "never" : task.endRepeatType, endRepeatUntil: task.endRepeatUntil)
        guard let next = CalendarEventProjector(timeZone: timeZone).occurrenceDates(event, semester: semester, through: formatter.string(from: horizon)).first(where: { $0 > anchor }), let nextDate = formatter.date(from: next) else { return nil }
        let hash = SHA256.hash(data: Data((task.id.uuidString.lowercased() + ":" + next).utf8)).map { String(format: "%02x", $0) }.joined()
        let hex = String(hash.prefix(32))
        let uuidString = "\(hex.prefix(8))-\(hex.dropFirst(8).prefix(4))-\(hex.dropFirst(12).prefix(4))-\(hex.dropFirst(16).prefix(4))-\(hex.dropFirst(20))"
        guard let id = UUID(uuidString: uuidString) else { return nil }
        let shift = calendar.dateComponents([.day], from: origin, to: nextDate).day ?? 0
        var due: String?
        if let old = task.dueDate, let date = formatter.date(from: String(old.prefix(10))), let shifted = calendar.date(byAdding: .day, value: shift, to: date) {
            due = formatter.string(from: shifted) + String(old.dropFirst(10))
        }
        return PomodoroTask(id: id, semesterId: task.semesterId, title: task.title, courseId: task.courseId, courseName: task.courseName,
            estimatedPomodoros: task.estimatedPomodoros, targetMinutes: task.targetMinutes, priority: task.priority, notes: task.notes,
            startDate: next, dueDate: due, frequency: task.frequency, customDaysOfWeek: task.customDaysOfWeek, customWeeks: task.customWeeks,
            endRepeatType: task.endRepeatType, endRepeatUntil: task.endRepeatUntil,
            endRepeatCount: task.endRepeatCount.map { max(1, $0 - 1) })
    }
}
