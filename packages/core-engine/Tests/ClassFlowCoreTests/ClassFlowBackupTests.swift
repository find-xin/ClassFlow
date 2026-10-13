import Foundation
import Testing
@testable import ClassFlowCore

@Suite("ClassFlowBackupTests")
struct ClassFlowBackupTests {
    let service = ClassFlowBackupService()

    func makeTestEntities() -> (Semester, [Course], [ScheduleException]) {
        let semesterId = UUID()
        let semester = Semester(
            id: semesterId,
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

        let courseId = UUID()
        let scheduleId = UUID()
        let course = Course(
            id: courseId,
            semesterId: semesterId,
            name: "高等数学 (上)",
            teacher: "张楚楚",
            color: "#4F46E5",
            notes: "闭卷统考",
            schedules: [
                CourseSchedule(
                    id: scheduleId,
                    courseId: courseId,
                    dayOfWeek: 1,
                    startSection: 1,
                    endSection: 2,
                    classroom: "东九楼 D102",
                    weekPattern: .all
                )
            ]
        )

        let exception = ScheduleException(
            id: UUID(),
            scheduleId: scheduleId,
            originalDate: "2026-10-19",
            exceptionType: .cancel,
            reason: "学术会议停课"
        )

        return (semester, [course], [exception])
    }

    @Test("Export to .classflow and import back with integrity verification")
    func testBackupExportAndImportRoundtrip() throws {
        let (semester, courses, exceptions) = makeTestEntities()

        let backupData = try service.exportBackup(
            semesters: [semester],
            courses: courses,
            exceptions: exceptions,
            settings: ["showWeekends": "true"]
        )

        #expect(backupData.count > 0)

        // Import and verify
        let package = try service.importBackup(from: backupData)
        #expect(package.manifest.formatVersion == 1)
        #expect(package.manifest.semestersCount == 1)
        #expect(package.manifest.coursesCount == 1)
        #expect(package.manifest.exceptionsCount == 1)

        #expect(package.semesters == [semester])
        #expect(package.courses == courses)
        #expect(package.exceptions == exceptions)
        #expect(package.settings["showWeekends"] == "true")
    }

    @Test("Tampered backup file fails SHA-256 integrity verification")
    func testChecksumMismatchThrows() throws {
        let (semester, courses, exceptions) = makeTestEntities()

        let validBackupData = try service.exportBackup(
            semesters: [semester],
            courses: courses,
            exceptions: exceptions
        )

        // Extract and modify courses.json without updating manifest checksum
        var files = try service.zipArchive.extractArchive(data: validBackupData)
        files["courses.json"] = "[]".data(using: .utf8)!
        let tamperedBackupData = service.zipArchive.createArchive(files: files)

        #expect(throws: ClassFlowBackupService.BackupError.self) {
            try service.importBackup(from: tamperedBackupData)
        }
    }

    @Test("Import preview correctly categorizes new vs conflicting courses")
    func testImportPreview() throws {
        let (semester, courses, exceptions) = makeTestEntities()
        let backupData = try service.exportBackup(
            semesters: [semester],
            courses: courses,
            exceptions: exceptions
        )
        let package = try service.importBackup(from: backupData)

        // Local state has 1 course with the same name, and 1 unrelated course
        let existingCourse = Course(
            id: UUID(),
            semesterId: semester.id,
            name: "高等数学 (上)", // duplicate name
            color: "#123456"
        )
        let otherCourse = Course(
            id: UUID(),
            semesterId: semester.id,
            name: "大学英语",
            color: "#654321"
        )

        let preview = service.generateImportPreview(
            package: package,
            existingCourses: [existingCourse, otherCourse]
        )

        #expect(preview.semestersCount == 1)
        #expect(preview.totalCoursesCount == 1)
        #expect(preview.conflictingCoursesCount == 1)
        #expect(preview.newCoursesCount == 0)
        #expect(preview.exceptionsCount == 1)
    }
}
