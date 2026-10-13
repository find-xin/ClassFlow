import Foundation
import Testing
@testable import ClassFlowCore

@Suite("CodableFixtureTests")
struct CodableFixtureTests {
    @Test("Roundtrip serialization for Semester")
    func testSemesterRoundtrip() throws {
        let semester = Semester(
            id: UUID(),
            name: "2026-2027 秋季学期",
            startDate: "2026-09-07",
            endDate: "2027-01-24",
            totalWeeks: 20,
            isCurrent: true,
            sections: [
                SectionTime(section: 1, startTime: "08:00", endTime: "08:45"),
                SectionTime(section: 2, startTime: "08:50", endTime: "09:35")
            ]
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .prettyPrinted]
        let data = try encoder.encode(semester)

        let decoded = try JSONDecoder().decode(Semester.self, from: data)
        #expect(decoded == semester)
    }

    @Test("Roundtrip serialization for Course with WeekPattern")
    func testCourseRoundtrip() throws {
        let course = Course(
            id: UUID(),
            semesterId: UUID(),
            name: "高等数学 (上)",
            teacher: "张楚楚",
            color: "#4F46E5",
            notes: "期末闭卷",
            schedules: [
                CourseSchedule(
                    id: UUID(),
                    dayOfWeek: 1,
                    startSection: 1,
                    endSection: 2,
                    classroom: "东九楼 D102",
                    weekPattern: .all
                ),
                CourseSchedule(
                    id: UUID(),
                    dayOfWeek: 3,
                    startSection: 3,
                    endSection: 4,
                    classroom: "东九楼 D102",
                    weekPattern: .custom([1, 3, 5, 7, 9])
                )
            ]
        )

        let data = try JSONEncoder().encode(course)
        let decoded = try JSONDecoder().decode(Course.self, from: data)
        #expect(decoded == course)
    }

    @Test("Roundtrip serialization for ScheduleException")
    func testExceptionRoundtrip() throws {
        let ex = ScheduleException(
            id: UUID(),
            scheduleId: UUID(),
            originalDate: "2026-10-19",
            exceptionType: .reschedule,
            targetDate: "2026-10-24",
            targetDayOfWeek: 6,
            targetStartSection: 5,
            targetEndSection: 6,
            targetClassroom: "东九楼 D108",
            reason: "调课至周六"
        )

        let data = try JSONEncoder().encode(ex)
        let decoded = try JSONDecoder().decode(ScheduleException.self, from: data)
        #expect(decoded == ex)
    }

    @Test("Roundtrip serialization for TimelineItem")
    func testTimelineItemRoundtrip() throws {
        let item = TimelineItem(
            id: "course_occ_c4b92b60_2026-10-19",
            title: "高等数学",
            location: "D102",
            date: "2026-10-19",
            dayOfWeek: 1,
            startSection: 1,
            endSection: 2,
            startTime: "08:00",
            endTime: "09:35",
            weekNumber: 7,
            isCurrentWeek: true,
            status: .normal,
            color: "#4F46E5",
            hasConflict: true,
            conflictingItemIds: ["course_occ_other"]
        )

        let data = try JSONEncoder().encode(item)
        let decoded = try JSONDecoder().decode(TimelineItem.self, from: data)
        #expect(decoded == item)
    }
}
