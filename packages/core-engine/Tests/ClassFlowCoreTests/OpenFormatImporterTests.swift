import Foundation
import Testing
@testable import ClassFlowCore

@Suite("OpenFormatImporterTests")
struct OpenFormatImporterTests {
    let importer = OpenFormatCourseImporter()
    let semesterId = UUID()

    @Test("Parse AISchedule format JSON payload")
    func testAIScheduleFormatParsing() throws {
        let json = """
        {
            "courseInfos": [
                {
                    "name": "高等数学",
                    "teacher": "张教授",
                    "position": "理学楼 A101",
                    "day": 1,
                    "sections": [1, 2],
                    "weeks": [1, 2, 3, 4, 5, 6, 7, 8]
                },
                {
                    "name": "高等数学",
                    "teacher": "张教授",
                    "position": "理学楼 A101",
                    "day": 3,
                    "sections": [3, 4],
                    "weeks": [1, 2, 3, 4, 5, 6, 7, 8]
                },
                {
                    "name": "大学英语",
                    "teacher": "王老师",
                    "position": "外语楼 203",
                    "day": 2,
                    "sections": [1, 2],
                    "weeks": [1, 3, 5, 7, 9, 11]
                }
            ],
            "sectionTimes": [
                { "section": 1, "startTime": "08:00", "endTime": "08:45" },
                { "section": 2, "startTime": "08:50", "endTime": "09:35" }
            ]
        }
        """

        let result = try importer.parse(jsonString: json, semesterId: semesterId)
        #expect(result.detectedFormat.contains("AISchedule"))
        #expect(result.courses.count == 2) // "高等数学" should be merged into 1 course with 2 schedules!

        let math = result.courses.first(where: { $0.name == "高等数学" })!
        #expect(math.schedules.count == 2)
        #expect(math.schedules[0].weekPattern.type == .custom)
        #expect(math.schedules[0].weekPattern.customWeeks == [1, 2, 3, 4, 5, 6, 7, 8])

        let english = result.courses.first(where: { $0.name == "大学英语" })!
        #expect(english.schedules.count == 1)
        #expect(english.schedules[0].weekPattern.customWeeks == [1, 3, 5, 7, 9, 11])

        #expect(result.parsedSections?.count == 2)
    }

    @Test("Parse WakeUp format JSON payload")
    func testWakeUpFormatParsing() throws {
        let json = """
        {
            "courses": [
                {
                    "name": "数据结构",
                    "teacher": "李老师",
                    "room": "信息楼 301",
                    "day": 4,
                    "start": 5,
                    "step": 2,
                    "weeks": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]
                }
            ]
        }
        """

        let result = try importer.parse(jsonString: json, semesterId: semesterId)
        #expect(result.detectedFormat.contains("WakeUp"))
        #expect(result.courses.count == 1)
        let ds = result.courses[0]
        #expect(ds.name == "数据结构")
        #expect(ds.schedules[0].dayOfWeek == 4)
        #expect(ds.schedules[0].startSection == 5)
        #expect(ds.schedules[0].endSection == 6)
        #expect(ds.schedules[0].classroom == "信息楼 301")
    }
}
