import Foundation

/// Importer supporting popular Chinese university timetable open formats:
/// 1. AISchedule (小爱课程表开源教务适配规范) JSON format
/// 2. WakeUp 课程表 open JSON format
/// 3. Plain unified course JSON array
public struct OpenFormatCourseImporter: Sendable {
    public init() {}

    public struct ImportResult: Sendable {
        public let courses: [Course]
        public let parsedSections: [SectionTime]?
        public let detectedFormat: String
    }

    /// Automatically detects the JSON payload format (AISchedule or WakeUp) and parses it into ClassFlow Course entities.
    public func parse(jsonString: String, semesterId: UUID) throws -> ImportResult {
        guard let data = jsonString.data(using: .utf8) else {
            throw NSError(domain: "OpenFormatCourseImporter", code: 400, userInfo: [NSLocalizedDescriptionKey: "无法解析输入的 JSON 编码"])
        }

        let jsonObject = try JSONSerialization.jsonObject(with: data, options: [])

        if let dict = jsonObject as? [String: Any] {
            // Check AISchedule format
            if let courseInfos = dict["courseInfos"] as? [[String: Any]] {
                let sections = parseAIScheduleSectionTimes(dict["sectionTimes"])
                let courses = parseAIScheduleCourseInfos(courseInfos, semesterId: semesterId)
                return ImportResult(courses: courses, parsedSections: sections, detectedFormat: "AISchedule (小爱课程表规范)")
            }

            // Check WakeUp format with root "courses"
            if let coursesList = dict["courses"] as? [[String: Any]] {
                let courses = parseWakeUpCourses(coursesList, semesterId: semesterId)
                return ImportResult(courses: courses, parsedSections: nil, detectedFormat: "WakeUp 课程表规范")
            }
        } else if let array = jsonObject as? [[String: Any]] {
            // Root is array: detect whether elements have "position" (AISchedule) or "room" (WakeUp)
            if array.first?["position"] != nil || array.first?["sections"] != nil {
                let courses = parseAIScheduleCourseInfos(array, semesterId: semesterId)
                return ImportResult(courses: courses, parsedSections: nil, detectedFormat: "AISchedule 课程列表")
            } else {
                let courses = parseWakeUpCourses(array, semesterId: semesterId)
                return ImportResult(courses: courses, parsedSections: nil, detectedFormat: "WakeUp 课程列表")
            }
        }

        throw NSError(domain: "OpenFormatCourseImporter", code: 422, userInfo: [NSLocalizedDescriptionKey: "未识别出兼容的 AISchedule 或 WakeUp 课程表数据结构"])
    }

    // MARK: - AISchedule Parsing

    private func parseAIScheduleCourseInfos(_ list: [[String: Any]], semesterId: UUID) -> [Course] {
        var schedulesByCourseName: [String: [(schedule: CourseSchedule, teacher: String?, color: String?, credit: Double?)]] = [:]
        let pastelColors = ["#5B7FB5", "#56A3A6", "#E37B75", "#8E7DBE", "#E8A87C", "#7BB38E", "#C38D9E", "#457B9D"]

        for (idx, item) in list.enumerated() {
            guard let name = item["name"] as? String, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { continue }
            let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)

            let teacher = (item["teacher"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            let room = (item["position"] as? String ?? item["room"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            let day = (item["day"] as? Int) ?? (item["dayOfWeek"] as? Int) ?? 1
            let credit: Double? = (item["credit"] as? Double) ?? (item["credit"] as? Int).map { Double($0) } ?? (item["credit"] as? String).flatMap { Double($0) }

            // Parse sections
            var startSec = 1
            var endSec = 2
            if let secArray = item["sections"] as? [Int], let first = secArray.first {
                startSec = first
                endSec = secArray.last ?? first
            } else if let secObjArray = item["sections"] as? [[String: Any]], let first = secObjArray.first?["section"] as? Int {
                startSec = first
                endSec = (secObjArray.last?["section"] as? Int) ?? first
            } else if let s = item["startSection"] as? Int {
                startSec = s
                endSec = (item["endSection"] as? Int) ?? s
            }

            // Parse weeks
            var weekPattern: WeekPattern = .all
            if let weeks = item["weeks"] as? [Int], !weeks.isEmpty {
                weekPattern = WeekPattern.custom(weeks)
            }

            let color = pastelColors[idx % pastelColors.count]

            let schedule = CourseSchedule(
                dayOfWeek: min(7, max(1, day)),
                startSection: max(1, startSec),
                endSection: max(startSec, endSec),
                classroom: room?.isEmpty == false ? room : nil,
                teacher: teacher?.isEmpty == false ? teacher : nil,
                weekPattern: weekPattern
            )

            schedulesByCourseName[trimmedName, default: []].append((schedule, teacher, color, credit))
        }

        return schedulesByCourseName.map { courseName, items in
            let courseId = UUID()
            let schedules = items.map { entry in
                CourseSchedule(
                    id: UUID(),
                    courseId: courseId,
                    dayOfWeek: entry.schedule.dayOfWeek,
                    startSection: entry.schedule.startSection,
                    endSection: entry.schedule.endSection,
                    classroom: entry.schedule.classroom,
                    teacher: entry.schedule.teacher,
                    weekPattern: entry.schedule.weekPattern
                )
            }
            let firstTeacher = items.compactMap { $0.teacher }.first
            let firstCredit = items.compactMap { $0.credit }.first
            let color = items.compactMap { $0.color }.first ?? "#5B7FB5"

            return Course(
                id: courseId,
                semesterId: semesterId,
                name: courseName,
                teacher: firstTeacher,
                credit: firstCredit,
                color: color,
                schedules: schedules
            )
        }
    }

    private func parseAIScheduleSectionTimes(_ obj: Any?) -> [SectionTime]? {
        guard let list = obj as? [[String: Any]], !list.isEmpty else { return nil }
        var result: [SectionTime] = []
        for (i, item) in list.enumerated() {
            let section = (item["section"] as? Int) ?? (i + 1)
            let start = item["startTime"] as? String ?? item["start"] as? String ?? ""
            let end = item["endTime"] as? String ?? item["end"] as? String ?? ""
            if !start.isEmpty && !end.isEmpty {
                result.append(SectionTime(section: section, startTime: start, endTime: end))
            }
        }
        return result.isEmpty ? nil : result.sorted(by: { $0.section < $1.section })
    }

    // MARK: - WakeUp Parsing

    private func parseWakeUpCourses(_ list: [[String: Any]], semesterId: UUID) -> [Course] {
        var schedulesByCourseName: [String: [(schedule: CourseSchedule, teacher: String?, color: String?, credit: Double?)]] = [:]
        let pastelColors = ["#5B7FB5", "#56A3A6", "#E37B75", "#8E7DBE", "#E8A87C", "#7BB38E", "#C38D9E", "#457B9D"]

        for (idx, item) in list.enumerated() {
            guard let name = item["name"] as? String, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { continue }
            let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)

            let teacher = (item["teacher"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            let room = (item["room"] as? String ?? item["position"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            let day = (item["day"] as? Int) ?? 1
            let start = (item["start"] as? Int) ?? 1
            let step = (item["step"] as? Int) ?? 1
            let endSec = start + max(1, step) - 1
            let credit: Double? = (item["credit"] as? Double) ?? (item["credit"] as? Int).map { Double($0) } ?? (item["credit"] as? String).flatMap { Double($0) }

            var weekPattern: WeekPattern = .all
            if let weeks = item["weeks"] as? [Int], !weeks.isEmpty {
                weekPattern = WeekPattern.custom(weeks)
            }

            let color = (item["color"] as? String) ?? pastelColors[idx % pastelColors.count]

            let schedule = CourseSchedule(
                dayOfWeek: min(7, max(1, day)),
                startSection: max(1, start),
                endSection: max(start, endSec),
                classroom: room?.isEmpty == false ? room : nil,
                teacher: teacher?.isEmpty == false ? teacher : nil,
                weekPattern: weekPattern
            )

            schedulesByCourseName[trimmedName, default: []].append((schedule, teacher, color, credit))
        }

        return schedulesByCourseName.map { courseName, items in
            let courseId = UUID()
            let schedules = items.map { entry in
                CourseSchedule(
                    id: UUID(),
                    courseId: courseId,
                    dayOfWeek: entry.schedule.dayOfWeek,
                    startSection: entry.schedule.startSection,
                    endSection: entry.schedule.endSection,
                    classroom: entry.schedule.classroom,
                    teacher: entry.schedule.teacher,
                    weekPattern: entry.schedule.weekPattern
                )
            }
            let firstTeacher = items.compactMap { $0.teacher }.first
            let firstCredit = items.compactMap { $0.credit }.first
            let color = items.compactMap { $0.color }.first ?? "#5B7FB5"

            return Course(
                id: courseId,
                semesterId: semesterId,
                name: courseName,
                teacher: firstTeacher,
                credit: firstCredit,
                color: color,
                schedules: schedules
            )
        }
    }
}
