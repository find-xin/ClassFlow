import Foundation
import Testing
@testable import ClassFlowCore

@Suite("ICSTests")
struct ICSTests {
    let exporter = ICSExporter(timeZoneId: "Asia/Shanghai")
    let importer = ICSImporter(timeZoneId: "Asia/Shanghai")

    func makeTestSemesterAndCourse() -> (Semester, Course) {
        let semesterId = UUID()
        let semester = Semester(
            id: semesterId,
            name: "2026-2027 秋季学期",
            startDate: "2026-09-07",
            endDate: "2027-01-24",
            totalWeeks: 10,
            isCurrent: true,
            sections: [
                SectionTime(section: 1, startTime: "08:00", endTime: "08:45"),
                SectionTime(section: 2, startTime: "08:50", endTime: "09:35")
            ]
        )

        let courseId = UUID()
        let schedule = CourseSchedule(
            id: UUID(),
            courseId: courseId,
            dayOfWeek: 1,
            startSection: 1,
            endSection: 2,
            classroom: "东九楼 D102",
            weekPattern: .all
        )
        let course = Course(
            id: courseId,
            semesterId: semesterId,
            name: "高等数学",
            teacher: "张楚楚",
            color: "#4F46E5",
            notes: "期末闭卷",
            schedules: [schedule]
        )

        return (semester, course)
    }

    @Test("Export course occurrences to compliant RFC 5545 iCalendar string")
    func testExportToICS() {
        let (semester, course) = makeTestSemesterAndCourse()

        let icsContent = exporter.exportToICS(
            semester: semester,
            courses: [course],
            exceptions: []
        )

        #expect(icsContent.contains("BEGIN:VCALENDAR"))
        #expect(icsContent.contains("VERSION:2.0"))
        #expect(icsContent.contains("SUMMARY:高等数学"))
        #expect(icsContent.contains("LOCATION:东九楼 D102"))
        #expect(icsContent.contains("DTSTART;TZID=Asia/Shanghai:20260907T080000"))
        #expect(icsContent.contains("DTEND;TZID=Asia/Shanghai:20260907T093500"))
        #expect(icsContent.contains("END:VCALENDAR"))
    }

    @Test("Parse RFC 5545 iCalendar string and convert to Course candidates")
    func testImportICS() {
        let (semester, course) = makeTestSemesterAndCourse()

        let icsContent = exporter.exportToICS(
            semester: semester,
            courses: [course],
            exceptions: []
        )

        let parsedEvents = importer.parseICS(content: icsContent)
        #expect(!parsedEvents.isEmpty)
        #expect(parsedEvents[0].summary == "高等数学")
        #expect(parsedEvents[0].startTime == "08:00")
        #expect(parsedEvents[0].endTime == "09:35")

        let candidateCourses = importer.convertToCourses(
            events: parsedEvents,
            semester: semester
        )

        #expect(candidateCourses.count == 1)
        let importedCourse = candidateCourses[0]
        #expect(importedCourse.name == "高等数学")
        #expect(importedCourse.schedules.count == 1)
        #expect(importedCourse.schedules[0].dayOfWeek == 1)
        #expect(importedCourse.schedules[0].startSection == 1)
        #expect(importedCourse.schedules[0].endSection == 2)
        #expect(importedCourse.schedules[0].classroom == "东九楼 D102")
    }
}
