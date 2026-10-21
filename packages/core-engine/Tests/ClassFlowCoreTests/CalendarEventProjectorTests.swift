import Foundation
import Testing
@testable import ClassFlowCore

@Suite("Calendar recurrence and task successors")
struct CalendarEventProjectorTests {
    let semester = Semester(name: "Test", startDate: "2026-10-19", endDate: "2027-02-07", totalWeeks: 16)
    let projector = CalendarEventProjector(timeZone: TimeZone(identifier: "Asia/Shanghai")!)
    func event(_ frequency: String, _ count: Int = 3) -> CalendarEvent {
        CalendarEvent(title: "Event", startTime: "2026-10-19T09:00:00", endTime: "2026-10-19T10:00:00", frequency: frequency, startDate: "2026-10-19", endRepeatType: "count", endRepeatCount: count)
    }
    @Test("All frequencies respect count and date endings")
    func frequencies() {
        let expected = ["daily": ["2026-10-19", "2026-10-20", "2026-10-21"], "workdays": ["2026-10-19", "2026-10-20", "2026-10-21"],
            "weekends": ["2026-10-24", "2026-10-25", "2026-10-31"], "weekly": ["2026-10-19", "2026-10-26", "2026-11-02"],
            "biweekly": ["2026-10-19", "2026-11-02", "2026-11-16"], "monthly": ["2026-10-19", "2026-11-19", "2026-12-19"]]
        for (frequency, dates) in expected { #expect(projector.occurrenceDates(event(frequency), semester: semester, through: "2027-01-31") == dates) }
        var custom = event("customDays"); custom.customDaysOfWeek = [2, 4]
        #expect(projector.occurrenceDates(custom, semester: semester, through: "2027-01-31") == ["2026-10-20", "2026-10-22", "2026-10-27"])
        custom.frequency = "customWeeks"; custom.customWeeks = [1, 3]; custom.customDaysOfWeek = [1]
        #expect(projector.occurrenceDates(custom, semester: semester, through: "2027-01-31") == ["2026-10-19", "2026-11-02"])
        var capped = event("daily"); capped.endRepeatType = "untilDate"; capped.endRepeatUntil = "2026-10-20"
        #expect(projector.occurrenceDates(capped, semester: semester, through: "2027-01-31").count == 2)
    }
    @Test("Cross-day duration clips at boundaries; section events follow revised school times")
    func multiDay() {
        var trip = event("none"); trip.startTime = "2026-10-19T19:00:00"; trip.endTime = "2026-10-21T00:00:00"
        #expect(projector.project(trip, semester: semester, week: 1).count == 2)
        trip.isAllDay = true; trip.endDate = "2026-10-21"
        #expect(projector.project(trip, semester: semester, week: 1).count == 3)
        var section = event("none"); section.timeMode = "sections"; section.startSection = 1; section.endSection = 2
        var revised = semester; revised.sections[0].startTime = "07:00"
        #expect(projector.project(section, semester: revised, week: 1).first?.startTime == "07:00")
    }
    @Test("Legacy recurrence bounds do not become duration; cancelled occurrences stay suppressed")
    func legacy() {
        var value = event("customDays"); value.startDate = "2026-10-12"; value.customDaysOfWeek = [2, 4]; value.endRepeatType = "never"; value.endDate = "2027-01-01"
        #expect(projector.project(value, semester: semester, week: 1).map { $0.dayOfWeek } == [2, 4])
        let ex = ScheduleException(scheduleId: value.id, originalDate: "2026-10-20", exceptionType: .cancel, reason: "单次删除")
        #expect(projector.project(value, semester: semester, week: 1, exceptions: [ex]).count == 1)
    }
    @Test("Task successor carries exact target, deadline clock and repeat cap with a stable ID")
    func successor() throws {
        let task = PomodoroTask(title: "Review", targetMinutes: 45, startDate: "2026-10-19", dueDate: "2026-10-19 18:00", frequency: "daily", endRepeatType: "count", endRepeatCount: 2)
        let next = try #require(TaskRecurrence.next(after: task, semester: semester))
        #expect(next.startDate == "2026-10-20" && next.dueDate == "2026-10-20 18:00" && next.targetMinutes == 45)
        #expect(next.id == TaskRecurrence.next(after: task, semester: semester)?.id)
        #expect(TaskRecurrence.next(after: next, semester: semester) == nil)
    }
    @Test("Empty week selection stays hidden and room changes do not create duplicate arrivals")
    func courseExceptions() {
        var course = Course(semesterId: semester.id, name: "Math")
        var slot = CourseSchedule(courseId: course.id, dayOfWeek: 1, startSection: 1, endSection: 2, weekPattern: .custom([]))
        course.schedules = [slot]
        #expect(TimelineEngine().generateOccurrences(semester: semester, courses: [course], exceptions: [], forWeek: 1, activeWeek: 1, showNonCurrentWeekCourses: true).isEmpty)
        slot.weekPattern = .range(start: 1, end: 16); course.schedules = [slot]
        let ex = ScheduleException(scheduleId: slot.id, originalDate: "2026-10-19", exceptionType: .room_change, targetDate: "2026-10-19", targetDayOfWeek: 1, targetStartSection: 1, targetEndSection: 2, targetClassroom: "B")
        #expect(TimelineEngine().generateOccurrences(semester: semester, courses: [course], exceptions: [ex], forWeek: 1, activeWeek: 1).count == 1)
    }
}
