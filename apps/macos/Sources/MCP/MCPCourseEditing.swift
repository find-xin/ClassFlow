import Foundation
import ClassFlowCore

extension MCPToolHandlers {
    func courseDraft(_ original: CourseSchedule?, arguments: [String: Any], courseID: UUID, semester: Semester) throws -> CourseSchedule {
        let day = try int("day_of_week", arguments: arguments, old: original?.dayOfWeek ?? 1, range: 1...7)
        let start = try int("start_section", arguments: arguments, old: original?.startSection ?? 1, range: 1...1000)
        let end = try int("end_section", arguments: arguments, old: original?.endSection ?? start, range: start...1000)
        guard semester.sectionTime(for: start) != nil, semester.sectionTime(for: end) != nil else { throw automationError("起止节次不存在。") }
        var customStart = try nullableString("custom_start_time", arguments: arguments, old: original?.customStartTime)
        var customEnd = try nullableString("custom_end_time", arguments: arguments, old: original?.customEndTime)
        if let mode = arguments["time_mode"] as? String {
            guard ["sections", "time"].contains(mode) else { throw automationError("time_mode 必须为 sections 或 time。") }
            if mode == "sections" { customStart = nil; customEnd = nil }
            else if customStart == nil || customEnd == nil { throw automationError("具体时间模式需提供 custom_start_time 与 custom_end_time。") }
        }
        guard (customStart == nil) == (customEnd == nil) else { throw automationError("具体起止时间必须成对提供。") }
        if let customStart, let customEnd { try validateTime(customStart); try validateTime(customEnd); guard customStart < customEnd else { throw automationError("结束时间必须晚于开始时间。") } }
        var pattern = original?.weekPattern ?? .range(start: 1, end: semester.totalWeeks)
        if let weeks = arguments["custom_weeks"] as? [Int] {
            guard weeks.allSatisfy({ (1...semester.totalWeeks).contains($0) }) else { throw automationError("周次超出学期。") }; pattern = .custom(Array(Set(weeks)).sorted())
        } else if arguments["start_week"] != nil || arguments["end_week"] != nil || arguments["week_pattern"] != nil {
            let first = try int("start_week", arguments: arguments, old: pattern.startWeek, range: 1...semester.totalWeeks)
            let last = try int("end_week", arguments: arguments, old: pattern.endWeek, range: first...semester.totalWeeks)
            let name = arguments["week_pattern"] as? String ?? pattern.type.rawValue
            guard let type = WeekPattern.PatternType(rawValue: name), type != .custom else { throw automationError("周次模式为 all/odd/even；custom 必须提供 custom_weeks。") }
            pattern = .range(start: first, end: last, type: type)
        }
        return CourseSchedule(id: original?.id ?? UUID(), courseId: courseID, dayOfWeek: day, startSection: start, endSection: end,
            classroom: try nullableString("classroom", arguments: arguments, old: original?.classroom),
            teacher: arguments["teacher"] is NSNull || arguments["teacher"] as? String == "" ? "" : try nullableString("teacher", arguments: arguments, old: original?.teacher), weekPattern: pattern, customStartTime: customStart, customEndTime: customEnd)
    }
    func editCourse(arguments: [String: Any], creating: Bool = false) throws -> String {
        var payload = database.load()
        let matches = creating ? payload.courses.indices.filter { payload.courses[$0].name == (arguments["name"] as? String) && payload.courses[$0].semesterId == (payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first)?.id } : []
        guard matches.count <= 1 else { throw automationError("同名课程不唯一，请使用 update_course 和 course_id。") }
        let index: Int? = creating ? matches.first : try uniqueIndex(payload.courses, query: arguments["course_id"] as? String ?? arguments["name"] as? String ?? "", id: { $0.id }, title: { $0.name })
        let semester = try editSemester(index.map { payload.courses[$0].semesterId }, payload: payload)
        var course = index.map { payload.courses[$0] } ?? Course(semesterId: semester.id, name: arguments["name"] as? String ?? "")
        if let name = arguments[creating ? "name" : "new_name"] as? String { course.name = name.trimmingCharacters(in: .whitespacesAndNewlines) }
        guard !course.name.isEmpty else { throw automationError("课程名称不能为空。") }
        course.notes = try nullableString("notes", arguments: arguments, old: course.notes)
        if let credit = arguments["credit"] {
            if credit is NSNull { course.credit = nil }
            else { guard let number = credit as? NSNumber, number.doubleValue.isFinite, number.doubleValue >= 0 else { throw automationError("学分必须是非负数字或 null。") }; course.credit = number.doubleValue }
        }
        if let color = arguments["color"] as? String { course.color = resolveColorHex(color) }
        if let rows = arguments["schedules"] as? [[String: Any]] {
            guard rows.count <= 100 else { throw automationError("一门课最多提交 100 个时段。") }
            var schedules: [CourseSchedule] = [], ids = Set<UUID>()
            for row in rows {
                var prior: CourseSchedule?
                if let id = row["schedule_id"] as? String {
                    guard let uuid = UUID(uuidString: id), let found = course.schedules.first(where: { $0.id == uuid }) else { throw automationError("时段 UUID 不属于这门课。") }; prior = found
                }
                let schedule = try courseDraft(prior, arguments: row, courseID: course.id, semester: semester)
                guard ids.insert(schedule.id).inserted else { throw automationError("不能重复提交同一时段 UUID。") }; schedules.append(schedule)
            }
            if creating && index != nil {
                guard ids.isDisjoint(with: Set(course.schedules.map { $0.id })) else { throw automationError("追加时段不能重复现有 schedule_id；请使用 update_course。") }
                course.schedules.append(contentsOf: schedules)
            } else {
                for removed in course.schedules where !ids.contains(removed.id) { removeScheduleData(removed.id, from: &payload) }
                course.schedules = schedules
            }
        } else if creating {
            course.schedules.append(try courseDraft(nil, arguments: arguments, courseID: course.id, semester: semester))
        } else {
            let fields = ["day_of_week", "start_section", "end_section", "time_mode", "custom_start_time", "custom_end_time", "start_week", "end_week", "week_pattern", "custom_weeks", "teacher", "classroom"]
            if fields.contains(where: { arguments[$0] != nil }) {
                if let id = arguments["schedule_id"] as? String {
                    guard let uuid = UUID(uuidString: id), let i = course.schedules.firstIndex(where: { $0.id == uuid }) else { throw automationError("未找到此课程的时段 UUID。") }
                    course.schedules[i] = try courseDraft(course.schedules[i], arguments: arguments, courseID: course.id, semester: semester)
                } else {
                    let temporal = fields.filter { $0 != "teacher" && $0 != "classroom" }
                    guard course.schedules.count == 1 || !temporal.contains(where: { arguments[$0] != nil }) else { throw automationError("多个时段时，必须指定 schedule_id 或完整 schedules。") }
                    for i in course.schedules.indices { course.schedules[i] = try courseDraft(course.schedules[i], arguments: arguments, courseID: course.id, semester: semester) }
                }
            }
        }
        if arguments["teacher"] != nil && arguments["schedule_id"] == nil { course.teacher = try nullableString("teacher", arguments: arguments, old: course.teacher) }
        if let index { payload.courses[index] = course } else { payload.courses.append(course) }
        try saveEdit(payload)
        return try textJSON(["course_id": course.id.uuidString, "name": course.name, "message": "已成功更新课程", "schedule_ids": course.schedules.map { $0.id.uuidString }, "updated": true])
    }
    func removeScheduleData(_ id: UUID, from payload: inout LocalDatabase.DatabasePayload) {
        payload.tombstones.append(SyncTombstone(entityId: id.uuidString, entityType: "schedule"))
        for ex in payload.exceptions where ex.scheduleId == id { payload.tombstones.append(SyncTombstone(entityId: ex.id.uuidString, entityType: "exception")) }
        payload.exceptions.removeAll { $0.scheduleId == id }
    }
    func manageCourseSchedule(arguments: [String: Any]) throws -> String {
        var payload = database.load()
        let index = try uniqueIndex(payload.courses, query: arguments["course_id"] as? String ?? arguments["name"] as? String ?? "", id: { $0.id }, title: { $0.name })
        var course = payload.courses[index]
        let semester = try editSemester(course.semesterId, payload: payload)
        guard let action = arguments["action"] as? String else { throw automationError("缺少 action。") }
        if action == "add" {
            let new = try courseDraft(nil, arguments: arguments, courseID: course.id, semester: semester)
            course.schedules.append(new); payload.courses[index] = course; try saveEdit(payload)
            return try textJSON(["course_id": course.id.uuidString, "schedule_id": new.id.uuidString])
        }
        guard let id = arguments["schedule_id"] as? String, let uuid = UUID(uuidString: id), let slot = course.schedules.firstIndex(where: { $0.id == uuid }) else { throw automationError("必须提供此课程的 schedule_id。") }
        let original = course.schedules[slot]
        if action == "update" { course.schedules[slot] = try courseDraft(original, arguments: arguments, courseID: course.id, semester: semester) }
        else if action == "delete" { course.schedules.remove(at: slot); removeScheduleData(uuid, from: &payload) }
        else {
            let scope = try editScope(arguments, defaultValue: .thisOnly)
            var occurrence = arguments["occurrence_date"] as? String ?? ""
            var week = 1
            if scope != .all || action == "restore" {
                try validateDate(occurrence)
                let calculator = WeekCalculator(timeZoneId: TimeZone.current.identifier)
                week = calculator.weekIndex(for: occurrence, semesterStartDate: semester.startDate) ?? 0
                let expected = calculator.dateString(forWeek: week, dayOfWeek: original.dayOfWeek, semesterStartDate: semester.startDate)
                guard occurrence == expected, original.weekPattern.isActive(inWeek: week, totalWeeks: semester.totalWeeks) else { throw automationError("该日期不是所选时段的有效课时。") }
            } else { occurrence = semester.startDate }
            if action == "restore" {
                let removed = payload.exceptions.filter { $0.scheduleId == uuid && $0.originalDate == occurrence }
                for ex in removed { payload.tombstones.append(SyncTombstone(entityId: ex.id.uuidString, entityType: "exception")) }
                payload.exceptions.removeAll { $0.scheduleId == uuid && $0.originalDate == occurrence }
            } else if action == "cancel" || action == "reschedule" {
                if scope == .thisOnly {
                    let removed = payload.exceptions.filter { $0.scheduleId == uuid && $0.originalDate == occurrence }
                    for ex in removed { payload.tombstones.append(SyncTombstone(entityId: ex.id.uuidString, entityType: "exception")) }
                    payload.exceptions.removeAll { $0.scheduleId == uuid && $0.originalDate == occurrence }
                    if action == "cancel" {
                        payload.exceptions.append(ScheduleException(scheduleId: uuid, originalDate: occurrence, exceptionType: .cancel, reason: arguments["reason"] as? String ?? "单次停课"))
                    } else {
                        let draft = try courseDraft(original, arguments: arguments, courseID: course.id, semester: semester)
                        guard arguments["custom_start_time"] == nil, arguments["custom_end_time"] == nil, arguments["time_mode"] == nil else { throw automationError("单次调课使用节次；自定义时间请修改时段或拆分后续规则。") }
                        let calculator = WeekCalculator(timeZoneId: TimeZone.current.identifier)
                        let target = arguments["target_date"] as? String ?? calculator.dateString(forWeek: week, dayOfWeek: draft.dayOfWeek, semesterStartDate: semester.startDate)
                        try validateDate(target)
                        let day = (Calendar.current.component(.weekday, from: dateForEdit(target)!) + 5) % 7 + 1
                        let type: ScheduleException.ExceptionType = target == occurrence && draft.startSection == original.startSection && draft.endSection == original.endSection ? .room_change : .reschedule
                        payload.exceptions.append(ScheduleException(scheduleId: uuid, originalDate: occurrence, exceptionType: type, targetDate: type == .room_change ? nil : target, targetDayOfWeek: day,
                            targetStartSection: draft.startSection, targetEndSection: draft.endSection, targetClassroom: arguments["classroom"] != nil ? draft.classroom ?? "" : draft.classroom, reason: arguments["reason"] as? String ?? "单次调课"))
                    }
                } else {
                    let weeks = (1...semester.totalWeeks).filter { original.weekPattern.isActive(inWeek: $0, totalWeeks: semester.totalWeeks) }
                    let prior = scope == .all ? [] : weeks.filter { $0 < week }
                    let future = scope == .all ? weeks : weeks.filter { $0 >= week }
                    guard !future.isEmpty else { throw automationError("所选范围没有有效课时。") }
                    if prior.isEmpty { course.schedules.remove(at: slot); removeScheduleData(uuid, from: &payload) }
                    else {
                        course.schedules[slot].weekPattern = .custom(prior)
                        let removed = payload.exceptions.filter { $0.scheduleId == uuid && $0.originalDate >= occurrence }
                        for ex in removed { payload.tombstones.append(SyncTombstone(entityId: ex.id.uuidString, entityType: "exception")) }
                        payload.exceptions.removeAll { $0.scheduleId == uuid && $0.originalDate >= occurrence }
                    }
                    if action == "reschedule" {
                        var draft = try courseDraft(original, arguments: arguments, courseID: course.id, semester: semester)
                        draft.id = prior.isEmpty ? original.id : UUID(); draft.weekPattern = .custom(future); course.schedules.append(draft)
                        // Reusing a schedule ID for an all-scope edit must not leave a tombstone for that live entity.
                        payload.tombstones.removeAll { $0.entityId.lowercased() == draft.id.uuidString.lowercased() && $0.entityType == "schedule" }
                    }
                }
            } else { throw automationError("action 必须为 add/update/delete/reschedule/cancel/restore。") }
        }
        payload.courses[index] = course; try saveEdit(payload)
        return try textJSON(["course_id": course.id.uuidString, "schedule_ids": course.schedules.map { $0.id.uuidString }, "action": action])
    }
}
