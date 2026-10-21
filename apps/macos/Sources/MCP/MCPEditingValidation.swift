import Foundation
import ClassFlowCore

extension MCPToolHandlers {
    func editScope(_ arguments: [String: Any], defaultValue: EditingScope = .all) throws -> EditingScope {
        guard let raw = arguments["scope"] as? String else { return defaultValue }
        switch raw {
        case "thisOnly", "this_only": return .thisOnly
        case "thisAndFuture", "this_and_future": return .thisAndFuture
        case "all": return .all
        default: throw automationError("scope 必须为 this_only、this_and_future 或 all。")
        }
    }
    func editSemester(_ id: UUID?, payload: LocalDatabase.DatabasePayload) throws -> Semester {
        guard let semester = id.flatMap({ id in payload.semesters.first { $0.id == id } }) ?? payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first else { throw automationError("请先创建学期。") }
        return semester
    }
    func nullableString(_ key: String, arguments: [String: Any], old: String?) throws -> String? {
        guard let value = arguments[key] else { return old }
        if value is NSNull { return nil }
        guard let text = value as? String else { throw automationError("\(key) 必须是文本或 null。") }
        return text.isEmpty ? nil : text
    }
    func int(_ key: String, arguments: [String: Any], old: Int, range: ClosedRange<Int>) throws -> Int {
        guard let value = arguments[key] else { return old }
        guard let number = value as? Int, range.contains(number) else { throw automationError("\(key) 必须在 \(range.lowerBound)–\(range.upperBound) 之间。") }
        return number
    }
    func setOfInts(_ key: String, arguments: [String: Any], old: Set<Int>?, range: ClosedRange<Int>) throws -> Set<Int>? {
        guard let value = arguments[key] else { return old }
        if value is NSNull { return nil }
        guard let values = value as? [Int], values.allSatisfy(range.contains) else { throw automationError("\(key) 含无效数字。") }
        return Set(values)
    }
    func validateDateTime(_ value: String) throws {
        if value.count == 10 { try validateDate(value); return }
        guard value.count == 16, value[value.index(value.startIndex, offsetBy: 10)] == " " || value[value.index(value.startIndex, offsetBy: 10)] == "T" else { throw automationError("日期时间格式为 YYYY-MM-DD HH:mm。") }
        try validateDate(String(value.prefix(10))); try validateTime(String(value.suffix(5)))
    }
    func recurrence(_ arguments: [String: Any], frequency: inout String?, days: inout Set<Int>?, weeks: inout Set<Int>?, endType: inout String?, until: inout String?, count: inout Int?, start: String, semester: Semester) throws {
        if let raw = arguments["frequency"] as? String {
            guard ["none", "daily", "workdays", "weekends", "weekly", "biweekly", "monthly", "customDays", "customWeeks"].contains(raw) else { throw automationError("重复频次无效。") }
            frequency = raw
            if raw != "customDays" && raw != "weekly" && arguments["custom_days_of_week"] == nil { days = nil }
            if raw != "customWeeks" && arguments["custom_weeks"] == nil { weeks = nil }
        }
        days = try setOfInts("custom_days_of_week", arguments: arguments, old: days, range: 1...7)
        weeks = try setOfInts("custom_weeks", arguments: arguments, old: weeks, range: 1...semester.totalWeeks)
        if frequency == "customDays", days?.isEmpty != false { throw automationError("自定义星期不能为空。") }
        if frequency == "customWeeks", weeks?.isEmpty != false { throw automationError("自定义周次不能为空。") }
        if let type = arguments["end_repeat_type"] as? String {
            guard ["never", "untilDate", "count"].contains(type) else { throw automationError("结束条件无效。") }
            endType = type
        }
        until = try nullableString("end_repeat_until", arguments: arguments, old: until)
        if let value = arguments["end_repeat_count"] { count = value is NSNull ? nil : try int("end_repeat_count", arguments: arguments, old: count ?? 10, range: 1...10000) }
        if endType == "untilDate" {
            guard let until else { throw automationError("请指定重复结束日期。") }; try validateDate(until)
            guard until >= start else { throw automationError("重复结束日期不能早于开始日期。") }
        }
        if endType == "count", (count ?? 0) < 1 { throw automationError("请指定有效重复次数。") }
    }
    func saveEdit(_ payload: LocalDatabase.DatabasePayload) throws {
        try database.save(payload); notifyAppAndWidgets(payload: payload)
    }
    func editJSON<T: Encodable>(_ value: T) throws -> String {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys]; encoder.dateEncodingStrategy = .iso8601
        return String(decoding: try encoder.encode(value), as: UTF8.self)
    }
    func textJSON(_ value: [String: Any]) throws -> String { String(decoding: try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]), as: UTF8.self) }
}
