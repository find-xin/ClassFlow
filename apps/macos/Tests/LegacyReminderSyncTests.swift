import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

struct LegacyReminderSyncTests {
 @Test @MainActor func oldPeerCannotEraseReminderAndRestartDoesNotReplaySnapshot() async throws {
  let (server, db, root) = try MCPCompleteEditingTests().fixture()
  defer { try? FileManager.default.removeItem(at:root) }
  _ = try server.invokeTool(name:"add_calendar_event",arguments:["title":"anchor","date":"2026-10-25","alert_minutes_before":10])
  var events = db.load().calendarEvents
  events[0].alertMinutesBefore=nil; events[0].reminderConfigured=nil
  let storage=MockStorageProvider(), peer=SyncEngine(deviceId:"old-peer",deviceName:"Old client",initialSequence:1)
  var courses:[Course]=[], exceptions:[ScheduleException]=[], semesters=db.load().semesters
  _ = try await peer.executeSync(storage:storage,courses:&courses,exceptions:&exceptions,semesters:&semesters,calendarEvents:&events)
  let app=AppState(database:db,notificationService:MockNotificationService(),widgetDataStore:WidgetDataStore(customURL:root.appendingPathComponent("widget.json")))
  _ = try await app.executeCloudSync(storage:storage)
  #expect(db.load().calendarEvents.first?.alertMinutesBefore == 10)
  #expect(db.load().syncPeerSequences?["old-peer"] == peer.latestSequence)
  let reopened=AppState(database:LocalDatabase(customURL:db.fileURL),notificationService:MockNotificationService(),widgetDataStore:WidgetDataStore(customURL:root.appendingPathComponent("widget2.json")))
  _ = try await reopened.executeCloudSync(storage:storage)
  #expect(db.load().calendarEvents.first?.alertMinutesBefore == 10)
 }
 @Test func explicitNoneIsDistinctFromLegacyAbsence() throws {
  var local=CalendarEvent(title:"anchor",startTime:"10:00",endTime:"11:00",alertMinutesBefore:10)
  var legacy=local; legacy.alertMinutesBefore=nil; legacy.reminderConfigured=nil
  #expect(legacy.preservingLegacyReminder(from:local).alertMinutesBefore == 10)
  var clear=local; clear.alertMinutesBefore=nil
  let decoded=try JSONDecoder().decode(CalendarEvent.self,from:JSONEncoder().encode(clear))
  #expect(decoded.reminderConfigured == true)
  #expect(decoded.preservingLegacyReminder(from:local).alertMinutesBefore == nil)
  local.alertMinutesBefore=0
  #expect(legacy.preservingLegacyReminder(from:local).alertMinutesBefore == 0)
 }
}
