import Foundation
import Testing
@testable import ClassFlowCore

@Suite("TimelineEngineTests")
struct TimelineEngineTests {
    let engine = TimelineEngine(timeZoneId: "Asia/Shanghai")

    func makeTestSemester() -> Semester {
        Semester(
            id: UUID(),
            name: "2026-2027 秋季学期",
            startDate: "2026-09-07",
            endDate: "2027-01-24",
            totalWeeks: 20,
            isCurrent: true,
            sections: [
                SectionTime(section: 1, startTime: "08:00", endTime: "08:45"),
                SectionTime(section: 2, startTime: "08:50", endTime: "09:35"),
                SectionTime(section: 3, startTime: "10:05", endTime: "10:50"),
                SectionTime(section: 4, startTime: "10:55", endTime: "11:40"),
                SectionTime(section: 5, startTime: "14:00", endTime: "14:45"),
                SectionTime(section: 6, startTime: "14:50", endTime: "15:35")
            ]
        )
    }

    @Test("Project regular recurring course into TimelineItems")
    func testRegularCourseProjection() {
        let semester = makeTestSemester()
        let courseId = UUID()
        let scheduleId = UUID()
        let schedule = CourseSchedule(
            id: scheduleId,
            courseId: courseId,
            dayOfWeek: 1, // Monday
            startSection: 1,
            endSection: 2,
            classroom: "东九楼 D102",
            weekPattern: .all
        )
        let course = Course(
            id: courseId,
            semesterId: semester.id,
            name: "高等数学",
            teacher: "张教授",
            color: "#4F46E5",
            schedules: [schedule]
        )

        // Generate for week 7 (Oct 19 is Monday)
        let occurrences = engine.generateOccurrences(
            semester: semester,
            courses: [course],
            exceptions: [],
            forWeek: 7
        )

        #expect(occurrences.count == 1)
        let occ = occurrences[0]
        #expect(occ.title == "高等数学")
        #expect(occ.date == "2026-10-19")
        #expect(occ.dayOfWeek == 1)
        #expect(occ.startSection == 1)
        #expect(occ.endSection == 2)
        #expect(occ.startTime == "08:00")
        #expect(occ.endTime == "09:35")
        #expect(occ.location == "东九楼 D102")
        #expect(occ.status == .normal)
        #expect(occ.sectionSpan == 2)
    }

    @Test("Schedule cancellation exception marks occurrence cancelled")
    func testCancellationException() {
        let semester = makeTestSemester()
        let courseId = UUID()
        let scheduleId = UUID()
        let schedule = CourseSchedule(
            id: scheduleId,
            courseId: courseId,
            dayOfWeek: 1,
            startSection: 1,
            endSection: 2,
            classroom: "东九楼 D102",
            weekPattern: .all
        )
        let course = Course(
            id: courseId,
            semesterId: semester.id,
            name: "高等数学",
            schedules: [schedule]
        )

        let cancelException = ScheduleException(
            id: UUID(),
            scheduleId: scheduleId,
            originalDate: "2026-10-19",
            exceptionType: .cancel,
            reason: "教师学术交流停课"
        )

        let occurrences = engine.generateOccurrences(
            semester: semester,
            courses: [course],
            exceptions: [cancelException],
            forWeek: 7
        )

        #expect(occurrences.count == 1)
        #expect(occurrences[0].status == .cancelled)
    }

    @Test("Reschedule exception cancels original and creates target occurrence")
    func testRescheduleException() {
        let semester = makeTestSemester()
        let courseId = UUID()
        let scheduleId = UUID()
        let schedule = CourseSchedule(
            id: scheduleId,
            courseId: courseId,
            dayOfWeek: 1, // Monday Oct 19
            startSection: 1,
            endSection: 2,
            classroom: "东九楼 D102",
            weekPattern: .all
        )
        let course = Course(
            id: courseId,
            semesterId: semester.id,
            name: "高等数学",
            schedules: [schedule]
        )

        // Reschedule to Saturday Oct 24, sections 5-6 in room D108
        let rescheduleException = ScheduleException(
            id: UUID(),
            scheduleId: scheduleId,
            originalDate: "2026-10-19",
            exceptionType: .reschedule,
            targetDate: "2026-10-24",
            targetDayOfWeek: 6,
            targetStartSection: 5,
            targetEndSection: 6,
            targetClassroom: "东九楼 D108",
            reason: "调课至周六"
        )

        let occurrences = engine.generateOccurrences(
            semester: semester,
            courses: [course],
            exceptions: [rescheduleException],
            forWeek: 7
        )

        #expect(occurrences.count == 2)
        let origSlot = occurrences.first(where: { $0.date == "2026-10-19" })!
        #expect(origSlot.status == .cancelled)

        let targetSlot = occurrences.first(where: { $0.date == "2026-10-24" })!
        #expect(targetSlot.status == .rescheduled)
        #expect(targetSlot.dayOfWeek == 6)
        #expect(targetSlot.startSection == 5)
        #expect(targetSlot.endSection == 6)
        #expect(targetSlot.location == "东九楼 D108")
        #expect(targetSlot.startTime == "14:00")
        #expect(targetSlot.endTime == "15:35")
    }

    @Test("Room change exception modifies classroom without altering time")
    func testRoomChangeException() {
        let semester = makeTestSemester()
        let courseId = UUID()
        let scheduleId = UUID()
        let schedule = CourseSchedule(
            id: scheduleId,
            courseId: courseId,
            dayOfWeek: 1,
            startSection: 1,
            endSection: 2,
            classroom: "原教室 A101",
            weekPattern: .all
        )
        let course = Course(
            id: courseId,
            semesterId: semester.id,
            name: "物理实验",
            schedules: [schedule]
        )

        let roomException = ScheduleException(
            id: UUID(),
            scheduleId: scheduleId,
            originalDate: "2026-10-19",
            exceptionType: .room_change,
            targetClassroom: "新实验室 B202"
        )

        let occurrences = engine.generateOccurrences(
            semester: semester,
            courses: [course],
            exceptions: [roomException],
            forWeek: 7
        )

        #expect(occurrences.count == 1)
        #expect(occurrences[0].status == .normal)
        #expect(occurrences[0].location == "新实验室 B202")
    }

    @Test("WeekPattern custom ranges (e.g. 1-8周 vs 9-16周) and non-current week visibility")
    func testWeekPatternRangeFiltering() {
        let semester = makeTestSemester()
        let courseA = Course(
            id: UUID(),
            semesterId: semester.id,
            name: "前期课程 (1-8周)",
            schedules: [
                CourseSchedule(
                    dayOfWeek: 1,
                    startSection: 1,
                    endSection: 2,
                    classroom: "A101",
                    weekPattern: WeekPattern(type: .all, startWeek: 1, endWeek: 8)
                )
            ]
        )
        let courseB = Course(
            id: UUID(),
            semesterId: semester.id,
            name: "后期课程 (9-16周)",
            schedules: [
                CourseSchedule(
                    dayOfWeek: 1,
                    startSection: 3,
                    endSection: 4,
                    classroom: "B202",
                    weekPattern: WeekPattern(type: .all, startWeek: 9, endWeek: 16)
                )
            ]
        )

        // Generate for Week 5 with showNonCurrentWeekCourses = true
        let week5Items = engine.generateOccurrences(
            semester: semester,
            courses: [courseA, courseB],
            exceptions: [],
            forWeek: 5,
            showNonCurrentWeekCourses: true
        )

        #expect(week5Items.count == 2)
        let itemA = week5Items.first(where: { $0.title == "前期课程 (1-8周)" })!
        let itemB = week5Items.first(where: { $0.title == "后期课程 (9-16周)" })!
        #expect(itemA.isCurrentWeek == true)
        #expect(itemB.isCurrentWeek == false)

        // Generate for Week 5 with showNonCurrentWeekCourses = false
        let week5ActiveOnly = engine.generateOccurrences(
            semester: semester,
            courses: [courseA, courseB],
            exceptions: [],
            forWeek: 5,
            showNonCurrentWeekCourses: false
        )
        #expect(week5ActiveOnly.count == 1)
        #expect(week5ActiveOnly[0].title == "前期课程 (1-8周)")

        // Generate for Week 10
        let week10Items = engine.generateOccurrences(
            semester: semester,
            courses: [courseA, courseB],
            exceptions: [],
            forWeek: 10,
            showNonCurrentWeekCourses: true
        )
        #expect(week10Items.count == 2)
        let itemA10 = week10Items.first(where: { $0.title == "前期课程 (1-8周)" })!
        let itemB10 = week10Items.first(where: { $0.title == "后期课程 (9-16周)" })!
        #expect(itemA10.isCurrentWeek == false)
        #expect(itemB10.isCurrentWeek == true)
    }

    @Test("Course truncated with thisAndFuture (custom weeks) completely disappears from subsequent weeks rather than lingering as dimmed ghost card")
    func testTruncatedCourseDisappearsFromFutureWeeks() {
        let semester = makeTestSemester()
        let coordinator = ScheduleMutationCoordinator()
        let course = Course(
            id: UUID(),
            semesterId: semester.id,
            name: "高等数学",
            schedules: [
                CourseSchedule(
                    dayOfWeek: 1,
                    startSection: 1,
                    endSection: 2,
                    classroom: "A101",
                    weekPattern: WeekPattern.all
                )
            ]
        )

        // Cancel schedule at Week 4 using thisAndFuture scope
        let schedId = course.schedules[0].id
        let mutation = coordinator.cancelSchedule(
            course: course,
            scheduleId: schedId,
            scope: .thisAndFuture,
            currentWeek: 4,
            totalWeeks: semester.totalWeeks,
            occurrenceDate: "2026-09-21"
        )

        // In Week 3 (prior to truncation), the course should still run normally
        let week3Items = engine.generateOccurrences(
            semester: semester,
            courses: [mutation.updatedCourse],
            exceptions: mutation.newExceptions,
            forWeek: 3,
            showNonCurrentWeekCourses: true
        )
        #expect(week3Items.count == 1)
        #expect(week3Items[0].isCurrentWeek == true)

        // In Week 4 and Week 5 (after truncation), the course must be completely gone (count == 0),
        // and must NOT linger as a dimmed ghost card even if showNonCurrentWeekCourses is true!
        let week4Items = engine.generateOccurrences(
            semester: semester,
            courses: [mutation.updatedCourse],
            exceptions: mutation.newExceptions,
            forWeek: 4,
            showNonCurrentWeekCourses: true
        )
        #expect(week4Items.isEmpty)

        let week5Items = engine.generateOccurrences(
            semester: semester,
            courses: [mutation.updatedCourse],
            exceptions: mutation.newExceptions,
            forWeek: 5,
            showNonCurrentWeekCourses: true
        )
        #expect(week5Items.isEmpty)
    }
}

