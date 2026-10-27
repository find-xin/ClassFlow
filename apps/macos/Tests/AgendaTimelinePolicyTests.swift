import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

@Suite("Agenda actual clock ordering")
struct AgendaTimelinePolicyTests {
    func item(_ id: String, _ start: String, _ end: String, section: Int = 1, date: String = "2026-10-23", current: Bool = true, status: TimelineItem.OccurrenceStatus = .normal) -> TimelineItem {
        TimelineItem(id: id, sourceType: .calendarEvent, title: id, date: date, dayOfWeek: 5,
                     startSection: section, endSection: 14, startTime: start, endTime: end,
                     weekNumber: 8, isCurrentWeek: current, status: status)
    }
    func now(_ clock: String) -> Date { ISO8601DateFormatter().date(from: "2026-10-23T\(clock):00Z")! }
    var utc: Calendar { var value = Calendar(identifier: .gregorian); value.timeZone = TimeZone(secondsFromGMT: 0)!; return value }

    @Test func sleepDoesNotSortBeforeMorning() {
        let items = [item("sleep", "23:30", "23:40"), item("research", "09:00", "11:30", section: 4), item("breakfast", "08:30", "09:00")]
        #expect(AgendaTimelinePolicy.sorted(items).map(\.id) == ["breakfast", "research", "sleep"])
        #expect(AgendaTimelinePolicy.upcoming(items, now: now("09:45"), calendar: utc)?.id == "research")
        #expect(AgendaTimelinePolicy.upcoming(items, now: now("12:00"), calendar: utc)?.id == "sleep")
        #expect(AgendaTimelinePolicy.upcoming(items, now: now("23:40"), calendar: utc) == nil)
    }
    @Test func heroExcludesOtherDatesCancelledAndInactiveCourses() {
        let items = [item("past", "09:00", "10:00", date: "2026-10-16"), item("cancelled", "09:00", "10:00", status: .cancelled), item("inactive", "09:00", "10:00", current: false)]
        #expect(AgendaTimelinePolicy.upcoming(items, now: now("09:30"), calendar: utc) == nil)
    }
    @Test func calendarDrawingSectionsAreNotPresentedAsRealPeriods() {
        let sleep = item("sleep", "23:30", "23:40")
        #expect(!AgendaTimelinePolicy.showsSections(sleep, events: []))
        var course = sleep; course.sourceType = .course
        #expect(AgendaTimelinePolicy.showsSections(course, events: []))
    }
}
