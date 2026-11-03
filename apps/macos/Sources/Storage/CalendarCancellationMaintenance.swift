import Foundation
import ClassFlowCore

extension LocalDatabase {
    /// Fails closed on stale previews. The backup and atomic replacement contain no cloud credentials.
    func applyCalendarCancellationCleanup(_ plan: CalendarCancellationCleanup.Plan) throws {
        var payload = try loadForAutomation()
        let scheduleIDs = Set(payload.courses.flatMap { $0.schedules.map(\.id) })
        guard payload.calendarEvents == plan.sourceEvents, payload.exceptions == plan.sourceExceptions,
              scheduleIDs == plan.courseScheduleIDs else {
            throw NSError(domain: "ClassFlowMaintenance", code: 1, userInfo: [NSLocalizedDescriptionKey: "数据已变化，请重新预览后再清理。"])
        }
        // Keep a complete local recovery point before any destructive maintenance.
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let backup = fileURL.deletingLastPathComponent().appendingPathComponent("maintenance-backups", isDirectory: true)
        try FileManager.default.createDirectory(at: backup, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        let path = backup.appendingPathComponent("calendar-cancellations-\(UUID().uuidString).json")
        try encoder.encode(payload).write(to: path, options: .atomic)
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: path.path)
        payload.calendarEvents = plan.events
        payload.exceptions = plan.exceptions
        for id in Set(plan.removed.map(\.id)) {
            payload.tombstones.append(SyncTombstone(entityId: id.uuidString, entityType: "exception"))
        }
        try save(payload)
    }
}
