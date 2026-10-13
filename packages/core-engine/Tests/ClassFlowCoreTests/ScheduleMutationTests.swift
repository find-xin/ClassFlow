import Foundation
import Testing
@testable import ClassFlowCore

@Suite("ScheduleMutationTests")
struct ScheduleMutationTests {
    let coordinator = ScheduleMutationCoordinator()

    func makeCourseWithSchedule() -> (Course, CourseSchedule) {
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
            semesterId: UUID(),
            name: "高等数学",
            schedules: [schedule]
        )
        return (course, schedule)
    }

    @Test("Mutation under .thisOnly emits a ScheduleException without altering CourseSchedule")
    func testMutationThisOnly() {
        let (course, schedule) = makeCourseWithSchedule()

        let result = coordinator.applyScheduleMutation(
            course: course,
            scheduleId: schedule.id,
            scope: .thisOnly,
            currentWeek: 7,
            totalWeeks: 20,
            occurrenceDate: "2026-10-19",
            updatedDayOfWeek: 2, // Move this instance to Tuesday
            updatedStartSection: 3,
            updatedEndSection: 4,
            updatedClassroom: "西五楼 W201"
        )

        // Course schedule intact
        #expect(result.updatedCourse.schedules.count == 1)
        #expect(result.updatedCourse.schedules[0].dayOfWeek == 1)
        #expect(result.updatedCourse.schedules[0].classroom == "东九楼 D102")

        // 1 ScheduleException emitted
        #expect(result.newExceptions.count == 1)
        let ex = result.newExceptions[0]
        #expect(ex.scheduleId == schedule.id)
        #expect(ex.originalDate == "2026-10-19")
        #expect(ex.targetDayOfWeek == 2)
        #expect(ex.targetStartSection == 3)
        #expect(ex.targetEndSection == 4)
        #expect(ex.targetClassroom == "西五楼 W201")
        #expect(ex.exceptionType == .reschedule)
    }

    @Test("Mutation under .thisAndFuture truncates prior schedule and creates new successor schedule")
    func testMutationThisAndFuture() {
        let (course, schedule) = makeCourseWithSchedule()

        let result = coordinator.applyScheduleMutation(
            course: course,
            scheduleId: schedule.id,
            scope: .thisAndFuture,
            currentWeek: 7,
            totalWeeks: 20,
            occurrenceDate: "2026-10-19",
            updatedDayOfWeek: 3, // Move future classes to Wednesday
            updatedStartSection: 5,
            updatedEndSection: 6,
            updatedClassroom: "新楼 N101"
        )

        #expect(result.newExceptions.isEmpty)
        #expect(result.updatedCourse.schedules.count == 2)

        // Prior schedule: weeks 1..6
        let prior = result.updatedCourse.schedules[0]
        #expect(prior.id == schedule.id)
        #expect(prior.dayOfWeek == 1)
        #expect(prior.weekPattern.type == .custom)
        #expect(prior.weekPattern.customWeeks == Array(1...6))

        // Successor schedule: weeks 7..20
        let successor = result.updatedCourse.schedules[1]
        #expect(successor.dayOfWeek == 3)
        #expect(successor.startSection == 5)
        #expect(successor.endSection == 6)
        #expect(successor.classroom == "新楼 N101")
        #expect(successor.weekPattern.type == .custom)
        #expect(successor.weekPattern.customWeeks == Array(7...20))
    }

    @Test("Mutation under .all updates the CourseSchedule directly across the semester")
    func testMutationAll() {
        let (course, schedule) = makeCourseWithSchedule()

        let result = coordinator.applyScheduleMutation(
            course: course,
            scheduleId: schedule.id,
            scope: .all,
            currentWeek: 7,
            totalWeeks: 20,
            occurrenceDate: "2026-10-19",
            updatedDayOfWeek: 5, // All Friday
            updatedStartSection: 7,
            updatedEndSection: 8,
            updatedClassroom: "教三楼 301"
        )

        #expect(result.newExceptions.isEmpty)
        #expect(result.updatedCourse.schedules.count == 1)
        let updatedSched = result.updatedCourse.schedules[0]
        #expect(updatedSched.id == schedule.id)
        #expect(updatedSched.dayOfWeek == 5)
        #expect(updatedSched.startSection == 7)
        #expect(updatedSched.endSection == 8)
        #expect(updatedSched.classroom == "教三楼 301")
        #expect(updatedSched.weekPattern == .all)
    }

    @Test("Cancellation under .thisOnly emits a cancel exception")
    func testCancelThisOnly() {
        let (course, schedule) = makeCourseWithSchedule()

        let result = coordinator.cancelSchedule(
            course: course,
            scheduleId: schedule.id,
            scope: .thisOnly,
            currentWeek: 7,
            totalWeeks: 20,
            occurrenceDate: "2026-10-19"
        )

        #expect(result.updatedCourse.schedules.count == 1)
        #expect(result.newExceptions.count == 1)
        #expect(result.newExceptions[0].exceptionType == .cancel)
        #expect(result.newExceptions[0].originalDate == "2026-10-19")
    }

    @Test("Cancellation under .all removes the schedule from the course")
    func testCancelAll() {
        let (course, schedule) = makeCourseWithSchedule()

        let result = coordinator.cancelSchedule(
            course: course,
            scheduleId: schedule.id,
            scope: .all,
            currentWeek: 7,
            totalWeeks: 20,
            occurrenceDate: "2026-10-19"
        )

        #expect(result.updatedCourse.schedules.isEmpty)
        #expect(result.newExceptions.isEmpty)
    }
}
