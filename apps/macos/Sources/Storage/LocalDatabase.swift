import Foundation
import ClassFlowCore

/// Manages local persistent JSON storage in Application Support.
public struct LocalDatabase: Sendable {
    public struct DatabasePayload: Codable, Sendable {
        public var semesters: [Semester]
        public var courses: [Course]
        public var exceptions: [ScheduleException]
        public var settings: AppSettings

        public init(
            semesters: [Semester] = [],
            courses: [Course] = [],
            exceptions: [ScheduleException] = [],
            settings: AppSettings = AppSettings()
        ) {
            self.semesters = semesters
            self.courses = courses
            self.exceptions = exceptions
            self.settings = settings
        }
    }

    public let fileURL: URL

    public init(customURL: URL? = nil) {
        if let customURL {
            self.fileURL = customURL
        } else {
            let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            let classFlowDir = appSupport.appendingPathComponent("ClassFlow", isDirectory: true)
            self.fileURL = classFlowDir.appendingPathComponent("database.json")
        }
    }

    /// Loads the database payload, or generates sample bootstrap data if missing.
    public func load() -> DatabasePayload {
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return makeBootstrapData()
        }

        do {
            let data = try Data(contentsOf: fileURL)
            let payload = try JSONDecoder().decode(DatabasePayload.self, from: data)
            return payload
        } catch {
            print("[LocalDatabase] Failed to decode existing database, falling back to bootstrap: \(error)")
            return makeBootstrapData()
        }
    }

    /// Persists payload to disk atomically.
    public func save(_ payload: DatabasePayload) throws {
        let folder = fileURL.deletingLastPathComponent()
        if !FileManager.default.fileExists(atPath: folder.path) {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(payload)
        try data.write(to: fileURL, options: .atomic)
    }

    /// Generates high quality default university timetable data on first launch.
    public func makeBootstrapData() -> DatabasePayload {
        let semesterId = UUID(uuidString: "e6740bfa-8935-4cb2-8e14-d035f2990a41")!
        let semester = Semester(
            id: semesterId,
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
                SectionTime(section: 6, startTime: "14:50", endTime: "15:35"),
                SectionTime(section: 7, startTime: "15:55", endTime: "16:40"),
                SectionTime(section: 8, startTime: "16:45", endTime: "17:30"),
                SectionTime(section: 9, startTime: "18:30", endTime: "19:15"),
                SectionTime(section: 10, startTime: "19:20", endTime: "20:05"),
                SectionTime(section: 11, startTime: "20:10", endTime: "20:55"),
                SectionTime(section: 12, startTime: "21:00", endTime: "21:45")
            ]
        )

        let courseMathId = UUID(uuidString: "9fa8733b-8761-4fa3-9f50-ff3e107bb4c1")!
        let schedMath1Id = UUID(uuidString: "c4b92b60-449e-4a6b-9ee7-25e1a1795db2")!
        let schedMath2Id = UUID(uuidString: "e81d7f12-9c1a-4299-8cf0-21a47dfb2190")!

        let courseMath = Course(
            id: courseMathId,
            semesterId: semesterId,
            name: "高等数学 (上)",
            teacher: "张楚楚",
            color: "#4F46E5",
            notes: "期末统考占 60%，作业占 20%",
            schedules: [
                CourseSchedule(
                    id: schedMath1Id,
                    courseId: courseMathId,
                    dayOfWeek: 1,
                    startSection: 1,
                    endSection: 2,
                    classroom: "东九楼 D102",
                    weekPattern: .all
                ),
                CourseSchedule(
                    id: schedMath2Id,
                    courseId: courseMathId,
                    dayOfWeek: 3,
                    startSection: 3,
                    endSection: 4,
                    classroom: "东九楼 D102",
                    weekPattern: .all
                )
            ]
        )

        let coursePhysicsId = UUID()
        let coursePhysics = Course(
            id: coursePhysicsId,
            semesterId: semesterId,
            name: "大学物理",
            teacher: "王建华",
            color: "#0D9488",
            notes: "带物理实验手册",
            schedules: [
                CourseSchedule(
                    courseId: coursePhysicsId,
                    dayOfWeek: 2,
                    startSection: 1,
                    endSection: 2,
                    classroom: "西五楼 A201",
                    weekPattern: .all
                ),
                CourseSchedule(
                    courseId: coursePhysicsId,
                    dayOfWeek: 4,
                    startSection: 3,
                    endSection: 4,
                    classroom: "西五楼 A201",
                    weekPattern: .all
                )
            ]
        )

        let courseCsId = UUID()
        let courseCs = Course(
            id: courseCsId,
            semesterId: semesterId,
            name: "面向对象程序设计",
            teacher: "李明副教授",
            color: "#0284C7",
            notes: "提交 GitHub 课程大作业",
            schedules: [
                CourseSchedule(
                    courseId: courseCsId,
                    dayOfWeek: 2,
                    startSection: 5,
                    endSection: 6,
                    classroom: "计算中心 机房302",
                    weekPattern: .all
                )
            ]
        )

        let courseEnglishId = UUID()
        let courseEnglish = Course(
            id: courseEnglishId,
            semesterId: semesterId,
            name: "大学英语 (四级特训)",
            teacher: "Sarah Johnson",
            color: "#E11D48",
            notes: "口语小组对话练习",
            schedules: [
                CourseSchedule(
                    courseId: courseEnglishId,
                    dayOfWeek: 5,
                    startSection: 1,
                    endSection: 2,
                    classroom: "外语楼 405",
                    weekPattern: .all
                )
            ]
        )

        let rescheduleException = ScheduleException(
            id: UUID(uuidString: "769a68a2-3f8d-4ba6-86f1-ec4fa57209f8")!,
            scheduleId: schedMath1Id,
            originalDate: "2026-10-19",
            exceptionType: .reschedule,
            targetDate: "2026-10-24",
            targetDayOfWeek: 6,
            targetStartSection: 5,
            targetEndSection: 6,
            targetClassroom: "东九楼 D108",
            reason: "10月19日学术会议调课至周六下午"
        )

        return DatabasePayload(
            semesters: [semester],
            courses: [courseMath, coursePhysics, courseCs, courseEnglish],
            exceptions: [rescheduleException],
            settings: AppSettings()
        )
    }
}
