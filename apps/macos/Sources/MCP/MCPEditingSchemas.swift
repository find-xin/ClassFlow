import Foundation
import CoreFoundation

extension MCPServer {
    /// One schema source is used by stdio, HTTP/OpenAPI and batch validation.
    func completeEditingDefinitions() -> [[String: Any]] {
        let text: [String: Any] = ["type": "string"]
        let nullable: [String: Any] = ["type": ["string", "null"]]
        let integer: [String: Any] = ["type": "integer"]
        let ints: [String: Any] = ["type": "array", "items": integer]
        let boolean: [String: Any] = ["type": "boolean"]
        let scope: [String: Any] = ["type": "string", "enum": ["all", "this_only", "this_and_future", "thisOnly", "thisAndFuture"]]
        let mode: [String: Any] = ["type": "string", "enum": ["sections", "time"]]
        func tool(_ name: String, _ description: String, _ fields: [String: Any], required: [String] = []) -> [String: Any] {
            ["name": name, "description": description, "inputSchema": ["type": "object", "properties": fields, "required": required, "additionalProperties": false]]
        }
        let recurrence: [String: Any] = ["frequency": ["type": "string", "enum": ["none", "daily", "workdays", "weekends", "weekly", "biweekly", "monthly", "customDays", "customWeeks"]],
            "custom_days_of_week": ints, "custom_weeks": ints, "end_repeat_type": ["type": "string", "enum": ["never", "untilDate", "count"]],
            "end_repeat_until": nullable, "end_repeat_count": ["type": ["integer", "null"]]]
        let slot: [String: Any] = ["schedule_id": text, "day_of_week": integer, "start_section": integer, "end_section": integer,
            "classroom": nullable, "teacher": nullable, "time_mode": mode, "custom_start_time": nullable, "custom_end_time": nullable,
            "start_week": integer, "end_week": integer, "week_pattern": ["type": "string", "enum": ["all", "odd", "even", "custom"]], "custom_weeks": ints]
        var course = slot
        course.merge(["course_id": text, "name": text, "new_name": text, "credit": ["type": ["number", "null"]], "notes": nullable, "color": text,
            "schedules": ["type": "array", "maxItems": 100, "items": ["type": "object", "properties": slot, "additionalProperties": false]]]) { _, new in new }
        var manage = slot
        manage.merge(["course_id": text, "name": text, "action": ["type": "string", "enum": ["add", "update", "delete", "reschedule", "cancel", "restore"]], "scope": scope, "occurrence_date": text, "target_date": text, "reason": text]) { _, new in new }
        var event = recurrence
        event.merge(["event_id": text, "title": text, "new_title": text, "date": text, "end_date": nullable, "start_time": text, "end_time": text,
            "is_all_day": boolean, "time_mode": mode, "start_section": integer, "end_section": integer, "location": nullable, "notes": nullable, "color": text,
            "alert_minutes_before": ["type": ["integer", "null"], "minimum": 0, "maximum": 10080], "interval_weeks": integer, "scope": scope, "occurrence_date": text]) { _, new in new }
        var task = recurrence
        task.merge(["task_id": text, "title": text, "new_title": text, "notes": nullable, "course_id": nullable, "priority": ["type": ["string", "integer"]],
            "start_date": nullable, "due_date": nullable, "target_minutes": integer, "estimated_pomodoros": integer]) { _, new in new }
        return [
            tool("get_editing_context", "读取完整学期、作息节次、课程及每个时段 UUID、日程重复与提醒规则、待办全部可编辑字段、异常与当前专注。编辑前先读取以准确定位；不含密钥。通过 kind、offset、limit 分页；totals 返回各类总数。", ["kind": ["type": "string", "enum": ["all", "semesters", "courses", "events", "tasks", "exceptions"]], "offset": integer, "limit": integer]),
            tool("add_course", "新建课程（同名课程追加时段）。支持学分、备注、颜色、每时段教师地点、具体时间或节次、周次矩阵；schedules 创建多个时段。", course, required: ["name"]),
            tool("update_course", "完整编辑课程。course_id 或原 name 定位，new_name 改名。schedules 为完整替换，已有时段须保留 schedule_id；空数组清空时段。单时段修改用 schedule_id。null 清除选填字段。", course),
            tool("delete_course", "删除课程：all 彻底删除；this_only 按 occurrence_date 取消当天有效课时；this_and_future 按 week/occurrence_date 截断未来并保留历史。schedule_id 可限定时段。", ["course_id": text, "name": text, "id": text, "scope": scope, "date": text, "occurrence_date": text, "week": integer, "schedule_id": text]),
            tool("manage_course_schedule", "管理独立课程时段：add/update/delete；reschedule/cancel 支持 this_only/this_and_future/all，默认 this_only；restore 撤销 occurrence_date 的单次停课或调课。单次调课支持 target_date、节次、教室；具体时间通过 update 或后续范围配置。", manage, required: ["action"]),
            tool("add_calendar_event", "创建完整日程：日期/跨天 end_date、全天、具体时间或 time_mode=sections、颜色地点备注、重复规则及结束条件、提醒（null 无提醒，0 开始时）。重复事件 end_date 为系列截止日。", event, required: ["title", "date"]),
            tool("update_calendar_event", "编辑日程全部字段。event_id 或原 title 定位，new_title 改名。scope 默认 all；this_only/this_and_future 必须 occurrence_date，分别拆出单次或未来系列，保留历史。null 清除字段。", event),
            tool("delete_calendar_event", "删除独立或循环日程，scope=all/this_only/this_and_future；后两项须 occurrence_date，保留其他发生日期。", ["event_id": text, "title": text, "scope": scope, "occurrence_date": text]),
            tool("add_task", "创建待办：关联 course_id、备注、三级优先级 high/medium/low（兼容0/1/2）、开始日期、截止日期时间 YYYY-MM-DD HH:mm、精确 target_minutes 或番茄数、重复与结束规则。", task, required: ["title"]),
            tool("update_task", "修改待办所有可编辑字段，task_id 或原 title 定位，new_title 改名。null 清除关联课程/备注/起止日期。target_minutes 保留精确分钟，不修改已完成统计。", task),
            tool("start_focus_timer", "开始真实专注，mode=pomodoro/count_up/countdown。可指定 task_id 或 task_title，或 course_id 直接开启课程专注；schedule_id 可绑定教室。已有会话时先停止或重置。", ["task_id": text, "task_title": text, "course_id": text, "schedule_id": text, "mode": ["type": "string", "enum": ["pomodoro", "count_up", "countdown", "番茄钟", "正向计时", "定目标"]], "duration_minutes": integer]),
            tool("reset_focus_timer", "重置并丢弃当前专注会话，不产生专注记录。", [:]),
            tool("batch_edit_items", "原子批量编辑课程/时段/日程/待办，每批1–100项。任一失败整批不写入；dry_run 预检。相同 batch_id+内容重试不重复执行。先读取 UUID，operations 按顺序执行；不支持专注、同步或嵌套批量。", ["batch_id": text, "dry_run": boolean, "operations": ["type": "array", "minItems": 1, "maxItems": 100, "items": ["type": "object", "properties": ["tool": text, "arguments": ["type": "object"]], "required": ["tool", "arguments"], "additionalProperties": false]]], required: ["batch_id", "operations"])
        ]
    }

    func validateEditingArguments(name: String, arguments: [String: Any]) throws {
        guard let schema = completeEditingDefinitions().first(where: { $0["name"] as? String == name })?["inputSchema"] as? [String: Any] else { return }
        func validate(_ value: Any, schema: [String: Any], path: String) throws {
            func fail(_ message: String) throws -> Never { throw handlers.automationError("\(path)：\(message)") }
            let types = schema["type"] as? [String] ?? (schema["type"] as? String).map { [$0] } ?? []
            func matches(_ type: String) -> Bool {
                switch type {
                case "null": return value is NSNull
                case "string": return value is String
                case "object": return value is [String: Any]
                case "array": return value is [Any]
                case "boolean": return (value as? NSNumber).map { CFGetTypeID($0) == CFBooleanGetTypeID() } ?? false
                case "integer", "number":
                    guard let n = value as? NSNumber, CFGetTypeID(n) != CFBooleanGetTypeID(), n.doubleValue.isFinite else { return false }
                    return type == "number" || n.doubleValue.rounded() == n.doubleValue
                default: return true
                }
            }
            if !types.isEmpty && !types.contains(where: matches) { try fail("参数类型错误") }
            if value is NSNull { return }
            if let options = schema["enum"] as? [String], let string = value as? String, !options.contains(string) { try fail("参数选项无效") }
            if let n = value as? NSNumber {
                if let min = schema["minimum"] as? Int, n.doubleValue < Double(min) { try fail("小于最小值") }
                if let max = schema["maximum"] as? Int, n.doubleValue > Double(max) { try fail("大于最大值") }
            }
            if let object = value as? [String: Any], let properties = schema["properties"] as? [String: Any] {
                for required in schema["required"] as? [String] ?? [] where object[required] == nil { try fail("缺少 \(required)") }
                for (key, item) in object {
                    if let child = properties[key] as? [String: Any] { try validate(item, schema: child, path: path + "." + key) }
                    else if schema["additionalProperties"] as? Bool == false { try fail("未知字段 \(key)") }
                }
            }
            if let array = value as? [Any], let child = schema["items"] as? [String: Any] {
                if let max = schema["maxItems"] as? Int, array.count > max { try fail("条目过多") }
                if let min = schema["minItems"] as? Int, array.count < min { try fail("条目过少") }
                for (i, item) in array.enumerated() { try validate(item, schema: child, path: "\(path)[\(i)]") }
            }
        }
        try validate(arguments, schema: schema, path: name)
    }
}
