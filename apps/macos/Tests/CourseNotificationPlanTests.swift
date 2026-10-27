import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

struct CourseNotificationPlanTests {
    func item(_ id: String, date: String = "2026-10-23", active: Bool = true, status: TimelineItem.OccurrenceStatus = .normal) -> TimelineItem {
        TimelineItem(id: id, title: id, date: date, dayOfWeek: 5, startSection: 3, endSection: 4,
                     startTime: "10:15", endTime: "11:50", weekNumber: 8, isCurrentWeek: active, status: status)
    }
    @Test func inactiveAndCancelledCoursesCannotNotify() {
        let values = [item("real"), item("not-this-week", active: false), item("cancelled", status: .cancelled)]
        #expect(CourseNotificationPlan.eligible(values).map(\.id) == ["real"])
        #expect(CourseNotificationPlan.eligible([]).isEmpty)
    }
    @Test func briefingsCarryOnlyTheirOwnDateAndExpireAfterMorning() throws {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd HH:mm"
        let now = try #require(f.date(from: "2026-10-23 06:00"))
        let plans = CourseNotificationPlan.briefings([item("today"), item("tomorrow", date: "2026-10-24"), item("inactive", active: false)], hour: 7, minute: 30, now: now)
        #expect(plans.count == 7)
        #expect(plans[0].items.map(\.id) == ["today"])
        #expect(plans[1].items.map(\.id) == ["tomorrow"])
        #expect(plans[2].items.isEmpty)
        #expect(Set(plans.map(\.id)).count == 7)
        let after = try #require(f.date(from: "2026-10-23 08:00"))
        #expect(CourseNotificationPlan.briefings([item("today")], hour: 7, minute: 30, now: after).allSatisfy { $0.items.isEmpty && $0.date > after })
    }
}
