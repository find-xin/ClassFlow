import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

struct NotificationSnapshotTests {
    @Test @MainActor
    func schedulingUsesPersistedSnapshotInsteadOfStaleViewCache() async throws {
        let (server, database, root) = try MCPCompleteEditingTests().fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try server.invokeTool(name: "add_calendar_event", arguments: [
            "title": "anchor", "date": "2026-10-25", "alert_minutes_before": 10
        ])
        let service = MockNotificationService()
        let app = AppState(database: database, notificationService: service,
            widgetDataStore: WidgetDataStore(customURL: root.appendingPathComponent("widget.json")))
        // Simulate a view cache that has not received an IPC/sync commit yet.
        app.calendarEvents[0].alertMinutesBefore = nil
        await app.scheduleNotifications()
        #expect(service.calendarAlertInput.first?.alertMinutesBefore == 10)
    }
}
