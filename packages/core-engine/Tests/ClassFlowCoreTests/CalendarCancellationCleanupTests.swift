import Foundation
import Testing
@testable import ClassFlowCore

@Suite("Permanent calendar exclusions and safe cancellation cleanup")
struct CalendarCancellationCleanupTests {
    let semester = Semester(name: "Test", startDate: "2026-11-02", endDate: "2027-02-07", totalWeeks: 14)
    func event(_ frequency: String = "daily") -> CalendarEvent {
        CalendarEvent(title: "Series", startTime: "2026-11-02T09:00:00", endTime: "2026-11-02T10:00:00", frequency: frequency, startDate: "2026-11-02", endRepeatType: "count", endRepeatCount: 3)
    }
    @Test(arguments: ["daily", "weekly", "workdays", "customDays", "monthly", "biweekly", "weekends", "customWeeks"])
    func exclusionDoesNotExtendCount(frequency: String) {
        let projector = CalendarEventProjector()
        var original = event(frequency)
        let dates = projector.occurrenceDates(original, semester: semester, through: "2027-02-07")
        #expect(dates.count == 3)
        let cancellation = ScheduleException(scheduleId: original.id, originalDate: dates[1], exceptionType: .cancel, reason: "单次删除")
        let before = (1...14).flatMap { projector.project(original, semester: semester, week: $0, exceptions: [cancellation]) }
        let plan = CalendarCancellationCleanup.preview(events: [original], exceptions: [cancellation], courseScheduleIDs: [])
        #expect(plan.removed == [cancellation] && plan.migratedDates == 1)
        original = plan.events[0]
        #expect(projector.occurrenceDates(original, semester: semester, through: "2027-02-07") == [dates[0], dates[2]])
        #expect((1...14).flatMap { projector.project(original, semester: semester, week: $0, exceptions: plan.exceptions) } == before)
    }
    @Test func previewMigrationOnlyDedupAndIdempotency() {
        let series = event()
        let cancellation = ScheduleException(scheduleId: series.id, originalDate: "2026-11-03", exceptionType: .cancel)
        var duplicate = cancellation; duplicate.id = UUID()
        let source = [cancellation, duplicate, cancellation]
        let preview = CalendarCancellationCleanup.preview(events: [series], exceptions: source, courseScheduleIDs: [], removeRedundant: false)
        #expect(series.excludedDates.isEmpty && source.count == 3)
        #expect(preview.removed.isEmpty && preview.exceptions == source && preview.duplicateRecords == 2)
        let cleanup = CalendarCancellationCleanup.preview(events: preview.events, exceptions: preview.exceptions, courseScheduleIDs: [])
        #expect(cleanup.migratedDates == 0 && cleanup.removed.count == 3 && cleanup.events[0].excludedDates == ["2026-11-03"])
        let repeated = CalendarCancellationCleanup.preview(events: cleanup.events, exceptions: cleanup.exceptions, courseScheduleIDs: [])
        #expect(repeated.changedSeries == 0 && repeated.removed.isEmpty)
    }
    @Test func courseRescheduleOrphanMalformedAndConflictingRecordsAreUntouched() throws {
        let series = event(), courseID = UUID(), orphanID = UUID()
        let records = [
            ScheduleException(scheduleId: courseID, originalDate: "2026-11-03", exceptionType: .cancel),
            ScheduleException(scheduleId: orphanID, originalDate: "2026-11-03", exceptionType: .cancel),
            ScheduleException(scheduleId: series.id, originalDate: "2026-11-04", exceptionType: .reschedule, targetDate: "2026-11-05"),
            ScheduleException(scheduleId: series.id, originalDate: "2026-11-04", exceptionType: .cancel),
            ScheduleException(scheduleId: series.id, originalDate: "2026-02-30", exceptionType: .cancel),
            ScheduleException(scheduleId: series.id, originalDate: "2026-11-03", exceptionType: .cancel, targetDate: "2026-11-05")]
        let plan = CalendarCancellationCleanup.preview(events: [series], exceptions: records, courseScheduleIDs: [courseID])
        #expect(plan.events == [series] && plan.exceptions == records && plan.removed.isEmpty)
        let raw = """
        {"id":"\(UUID())","scheduleId":"\(series.id)","originalDate":"2026-11-03","type":"FUTURE_TYPE"}
        """
        let unknown = try JSONDecoder().decode(ScheduleException.self, from: Data(raw.utf8))
        #expect(unknown.unrecognizedType == "FUTURE_TYPE")
        #expect(CalendarCancellationCleanup.preview(events: [series], exceptions: [unknown], courseScheduleIDs: []).removed.isEmpty)
        #expect(try JSONDecoder().decode(ScheduleException.self, from: JSONEncoder().encode(unknown)) == unknown)
    }
    @Test func multiDayOriginExcludesAllClippedDays() {
        var series = event("weekly"); series.startTime = "2026-11-01T09:00:00"; series.startDate = "2026-11-01"; series.endTime = "2026-11-03T10:00:00"; series.endRepeatCount = 1
        let record = ScheduleException(scheduleId: series.id, originalDate: "2026-11-01", exceptionType: .cancel)
        let plan = CalendarCancellationCleanup.preview(events: [series], exceptions: [record], courseScheduleIDs: [])
        #expect(!CalendarEventProjector().project(series, semester: semester, week: 1).isEmpty)
        #expect(CalendarEventProjector().project(plan.events[0], semester: semester, week: 1).isEmpty)
    }
    @Test func archiveRestartLegacyAndICS() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../..")
        let data = try Data(contentsOf: root.appendingPathComponent("packages/fixtures/sample_calendar_exclusions.json"))
        let series = try JSONDecoder().decode(CalendarEvent.self, from: data)
        #expect(series.excludedDates == ["2026-11-03"])
        var object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any]); object.removeValue(forKey: "excludedDates")
        let legacy = try JSONDecoder().decode(CalendarEvent.self, from: JSONSerialization.data(withJSONObject: object))
        #expect(legacy.excludedDates.isEmpty)
        #expect(legacy.preservingExclusions(from: series).excludedDates == series.excludedDates)
        let service = ClassFlowBackupService()
        let archive = try service.exportBackup(semesters: [semester], courses: [], exceptions: [], calendarEvents: [series])
        let restored = try service.importBackup(from: archive)
        #expect(restored.calendarEvents == [series] && restored.manifest.formatVersion == 2)
        let decoded = try JSONDecoder().decode(CalendarEvent.self, from: JSONEncoder().encode(restored.calendarEvents[0]))
        #expect(CalendarEventProjector().project(decoded, semester: semester, week: 1).map(\.date) == ["2026-11-02", "2026-11-04"])
        let ics = try ICSExporter().exportToICS(semester: semester, courses: [], exceptions: [], calendarEvents: [decoded])
        #expect(!ics.contains("DTSTART;TZID=Asia/Shanghai:20261103") && ics.contains("20261104T090000"))
        #expect(try CalendarEventICSMetadata.events(content: ics) == [decoded])
        #expect(ICSImporter().parseICS(content: ics).isEmpty)
        var files = try service.zipArchive.extractArchive(data: archive)
        files["calendarEvents.json"] = Data("[]".utf8)
        #expect(throws: ClassFlowBackupService.BackupError.self) { try service.importBackup(from: service.zipArchive.createArchive(files: files)) }
    }
    @Test func legacyVersionOneArchiveStillLoads() throws {
        let service = ClassFlowBackupService()
        var files = try service.zipArchive.extractArchive(data: service.exportBackup(semesters: [semester], courses: [], exceptions: []))
        files.removeValue(forKey: "calendarEvents.json")
        var manifest = try #require(JSONSerialization.jsonObject(with: files["manifest.json"]!) as? [String: Any])
        manifest["formatVersion"] = 1
        // Use a legacy export's checksum over the original three components.
        let bytes = files["semesters.json"]! + files["courses.json"]! + files["exceptions.json"]!
        manifest["checksum"] = legacyChecksum(bytes)
        files["manifest.json"] = try JSONSerialization.data(withJSONObject: manifest)
        #expect(try service.importBackup(from: service.zipArchive.createArchive(files: files)).calendarEvents.isEmpty)
    }
}

import CryptoKit
private func legacyChecksum(_ data: Data) -> String { SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined() }

extension CalendarCancellationCleanupTests {
    @Test func batchCleanupKeepsUUIDsAndEveryOtherDate() {
        let events = (0..<1000).map { index in
            CalendarEvent(title: "Series \(index)", startTime: "2026-11-02T09:00:00", endTime: "2026-11-02T10:00:00", frequency: "daily", endRepeatType: "count", endRepeatCount: 3)
        }
        let records = events.flatMap { event in
            [ScheduleException(scheduleId: event.id, originalDate: "2026-11-03", exceptionType: .cancel),
             ScheduleException(scheduleId: event.id, originalDate: "2026-11-03", exceptionType: .cancel)]
        }
        let plan = CalendarCancellationCleanup.preview(events: events, exceptions: records, courseScheduleIDs: [])
        #expect(plan.events.map(\.id) == events.map(\.id))
        #expect(plan.migratedDates == 1000 && plan.duplicateRecords == 1000 && plan.removed.count == 2000)
        #expect(CalendarEventProjector().project(events: plan.events, semester: semester, week: 1).count == 2000)
    }
    @Test func explicitMissingICSWeekIsNeverFilledByImportHeuristic() throws {
        let fullSemester = Semester(name: "Test", startDate: "2026-11-02", endDate: "2026-11-29", totalWeeks: 4)
        let content = ["20261102", "20261116", "20261123"].map { date in
            "BEGIN:VEVENT\r\nSUMMARY:Course\r\nDTSTART:\(date)T090000\r\nDTEND:\(date)T100000\r\nEND:VEVENT"
        }.joined(separator: "\r\n")
        let importer = ICSImporter()
        let courses = importer.convertToCourses(events: importer.parseICS(content: content), semester: fullSemester)
        #expect(!courses.isEmpty)
        #expect(TimelineEngine().generateOccurrences(semester: fullSemester, courses: courses, exceptions: [], forWeek: 2, showNonCurrentWeekCourses: false).isEmpty)
    }
}
