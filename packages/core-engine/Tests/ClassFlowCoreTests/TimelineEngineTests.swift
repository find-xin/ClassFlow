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
}
