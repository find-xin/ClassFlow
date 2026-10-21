import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

struct CalendarAlertPlanTests {
    @Test("Reminders follow per-event offsets, counts, cancellations and sections")
    func alerts() throws {
        let semester = Semester(name: "Test", startDate: "2026-10-19", endDate: "2027-02-07", totalWeeks: 16, isCurrent: true)
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd HH:mm"
        let now = try #require(f.date(from: "2026-10-19 07:00"))
        var event = CalendarEvent(semesterId: semester.id, title: "Test", startTime: "2026-10-19T12:00:00", endTime: "2026-10-19T13:00:00", timeMode: "sections", startSection: 1, endSection: 2, alertMinutesBefore: 0, frequency: "daily", startDate: "2026-10-19", endRepeatType: "count", endRepeatCount: 3)
        var plans = CalendarAlertPlan.make(events: [event], semesters: [semester], exceptions: [], now: now)
        #expect(plans.count == 3 && f.string(from: plans[0].fireDate) == "2026-10-19 08:00")
        event.alertMinutesBefore = 15
        let cancel = ScheduleException(scheduleId: event.id, originalDate: "2026-10-20", exceptionType: .cancel, reason: "单次删除")
        plans = CalendarAlertPlan.make(events: [event], semesters: [semester], exceptions: [cancel], now: now)
        #expect(plans.count == 2 && f.string(from: plans[0].fireDate) == "2026-10-19 07:45")
        event.alertMinutesBefore = nil
        #expect(CalendarAlertPlan.make(events: [event], semesters: [semester], exceptions: [], now: now).isEmpty)
    }
}
