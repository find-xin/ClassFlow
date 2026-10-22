import Foundation
import CryptoKit
import ClassFlowCore

extension MCPToolHandlers {
    /// Selection always targets stored entities/series, never silently expands recurrences.
    /// A filter or explicit all=true is required; kind alone cannot mean "delete everything".
    func selectedIDs(_ selection: [String: Any], payload: LocalDatabase.DatabasePayload) throws -> [UUID] {
        let allowed: Set<String> = ["kind", "all", "ids", "title_contains", "date_from", "date_to", "semester_id", "course_id", "is_completed"]
        guard Set(selection.keys).isSubset(of: allowed), let kind = selection["kind"] as? String,
              ["events", "courses", "tasks"].contains(kind) else { throw automationError("selection.kind 必须为 events/courses/tasks，且不得含未知字段。") }
        let filters = Set(selection.keys).subtracting(["kind", "all"])
        guard !filters.isEmpty || selection["all"] as? Bool == true else { throw automationError("请提供筛选条件；选择全部必须显式 all=true。") }
        if let all = selection["all"] {
            guard all is Bool, !(all as? Bool == true && !filters.isEmpty) else { throw automationError("all=true 不能同时提供筛选条件。") }
        }
        var ids: Set<UUID>?
        if let value = selection["ids"] {
            guard let strings = value as? [String], !strings.isEmpty, strings.count <= 5000 else { throw automationError("ids 需要 1–5000 个 UUID。") }
            let parsed = strings.compactMap(UUID.init(uuidString:))
            guard parsed.count == strings.count else { throw automationError("ids 含无效 UUID。") }
            ids = Set(parsed)
        }
        func string(_ key: String) throws -> String? {
            guard let value = selection[key] else { return nil }
            guard let text = value as? String, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw automationError("\(key) 必须是非空文本。") }
            return text
        }
        let title = try string("title_contains")?.lowercased()
        let from = try string("date_from"), to = try string("date_to")
        if let from { try validateDate(from) }; if let to { try validateDate(to) }
        if let from, let to, from > to { throw automationError("date_from 不能晚于 date_to。") }
        func uuid(_ key: String) throws -> UUID? {
            guard let text = try string(key) else { return nil }
            guard let uuid = UUID(uuidString: text) else { throw automationError("\(key) 必须是 UUID。") }; return uuid
        }
        let semester = try uuid("semester_id"), course = try uuid("course_id")
        let completed = selection["is_completed"] as? Bool
        if selection["is_completed"] != nil && completed == nil { throw automationError("is_completed 必须是布尔值。") }
        if kind != "tasks" && (course != nil || completed != nil) { throw automationError("course_id / is_completed 仅用于待办筛选。") }
        if kind == "courses" && (from != nil || to != nil) { throw automationError("课程请按 semester_id 筛选；日期筛选适用于日程开始日期或待办截止/开始日期。") }
        func matches(_ id: UUID, _ name: String, _ sem: UUID?, _ date: String?) -> Bool {
            if let ids, !ids.contains(id) { return false }
            if let title, !name.lowercased().contains(title) { return false }
            if let semester, sem != semester { return false }
            if from != nil || to != nil {
                guard let date else { return false }
                if let from, date < from { return false }; if let to, date > to { return false }
            }
            return true
        }
        let result: [UUID]
        switch kind {
        case "events": result = payload.calendarEvents.filter { matches($0.id, $0.title, $0.semesterId, String(($0.startDate ?? $0.startTime).prefix(10))) }.map(\.id)
        case "courses": result = payload.courses.filter { matches($0.id, $0.name, $0.semesterId, nil) }.map(\.id)
        default: result = payload.pomodoroTasks.filter {
            matches($0.id, $0.title, $0.semesterId, ($0.dueDate ?? $0.startDate).map { String($0.prefix(10)) }) && (course == nil || $0.courseId == course) && (completed == nil || $0.isCompleted == completed)
        }.map(\.id)
        }
        // Explicit IDs must all exist in the selected domain; filters can further narrow them.
        if let ids {
            let existing: Set<UUID> = kind == "events" ? Set(payload.calendarEvents.map(\.id)) : kind == "courses" ? Set(payload.courses.map(\.id)) : Set(payload.pomodoroTasks.map(\.id))
            guard ids.isSubset(of: existing) else { throw automationError("ids 中有项目不存在于指定领域，整批未执行。") }
        }
        guard result.count <= 5000 else { throw automationError("匹配超过 5000 项，请按日期或学期缩小范围。") }
        return result
    }

    func batchEdit(arguments: [String: Any]) throws -> String {
        guard let batchID = arguments["batch_id"] as? String, !batchID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, batchID.utf8.count <= 200 else { throw automationError("需提供非空 batch_id（最多200字节）。") }
        let selection = arguments["selection"] as? [String: Any]
        let operations = arguments["operations"] as? [[String: Any]]
        guard (selection != nil) != (operations != nil) else { throw automationError("operations 与 selection 必须且只能提供一项。") }
        if let operations, !(1...1000).contains(operations.count) { throw automationError("operations 每批支持 1–1000 项。") }
        if operations != nil && (arguments["action"] != nil || arguments["changes"] != nil) { throw automationError("action/changes 仅用于 selection 模式。") }
        let canonicalArguments = arguments.filter { !["batch_id", "dry_run", "result_mode"].contains($0.key) }
        let data = try JSONSerialization.data(withJSONObject: canonicalArguments, options: [.sortedKeys])
        guard data.count <= 8 * 1024 * 1024 else { throw automationError("请求超过8 MiB，请缩小批次。") }
        let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        var payload = try database.loadForAutomation()
        let key = "editing:" + batchID
        if let prior = payload.calendarImportReceipts?[key] {
            // Accept receipts from the original operations-only interface after an App update.
            let legacy = try operations.map { rows in SHA256.hash(data: try JSONSerialization.data(withJSONObject: rows, options: [.sortedKeys])).map { String(format: "%02x", $0) }.joined() }
            guard prior.digest == digest || prior.digest == legacy else { throw automationError("batch_id 已用于不同内容。") }
            return prior.result
        }
        let staging = LocalDatabase(automationPayload: payload)
        let server = MCPServer(handlers: MCPToolHandlers(database: staging, enablesAppIntegration: false))
        let dryRun = arguments["dry_run"] as? Bool ?? false
        var count = 0, results: [String] = [], sampleIDs: [String] = []
        let full = arguments["result_mode"] as? String == "full" || (arguments["result_mode"] == nil && selection == nil && (operations?.count ?? 0) <= 100)
        if let selection {
            let ids = try selectedIDs(selection, payload: payload)
            count = ids.count; sampleIDs = ids.prefix(20).map(\.uuidString)
            let kind = selection["kind"] as? String ?? ""
            guard let action = arguments["action"] as? String, ["delete", "update", "complete"].contains(action) else { throw automationError("selection 模式须指定 action=delete/update/complete。") }
            let changes = arguments["changes"] as? [String: Any] ?? [:]
            let forbidden: Set<String> = ["event_id", "task_id", "title", "name", "scope", "occurrence_date", "schedule_id", "id"]
            guard Set(changes.keys).isDisjoint(with: forbidden), kind == "tasks" || changes["course_id"] == nil else { throw automationError("changes 不得修改定位字段或范围；重命名请用 new_title/new_name。") }
            if action == "delete" {
                guard changes.isEmpty else { throw automationError("删除不能提供 changes。") }
                let targets = Set(ids)
                var linked: Set<UUID> = kind == "events" ? targets : []
                if kind == "courses" { for value in payload.courses where targets.contains(value.id) { linked.formUnion(value.schedules.map(\.id)) } }
                for value in payload.exceptions where linked.contains(value.scheduleId) { payload.tombstones.append(SyncTombstone(entityId: value.id.uuidString, entityType: "exception")) }
                payload.exceptions.removeAll { linked.contains($0.scheduleId) }
                if kind == "events" { payload.calendarEvents.removeAll { targets.contains($0.id) } }
                else if kind == "tasks" { payload.pomodoroTasks.removeAll { targets.contains($0.id) } }
                else {
                    for id in linked { payload.tombstones.append(SyncTombstone(entityId: id.uuidString, entityType: "schedule")) }
                    payload.courses.removeAll { targets.contains($0.id) }
                }
                let type = kind == "events" ? "calendarEvent" : kind == "tasks" ? "pomodoroTask" : "course"
                for id in ids { payload.tombstones.append(SyncTombstone(entityId: id.uuidString, entityType: type)) }
                try staging.save(payload)
                if full { results = try ids.map { try textJSON(["id": $0.uuidString, "deleted": true]) } }
            } else {
                guard action != "complete" || kind == "tasks" else { throw automationError("complete 仅适用于待办。") }
                guard action != "update" || !changes.isEmpty else { throw automationError("update 必须提供非空 changes。") }
                if action == "complete", !Set(changes.keys).isSubset(of: ["is_completed"]) { throw automationError("complete 只接受 is_completed。") }
                let tool = action == "complete" ? "complete_task" : kind == "events" ? "update_calendar_event" : kind == "courses" ? "update_course" : "update_task"
                let idKey = kind == "events" ? "event_id" : kind == "courses" ? "course_id" : "task_id"
                for id in ids {
                    var args = changes; args[idKey] = id.uuidString
                    if action == "complete", let value = args["is_completed"], !(value is Bool) { throw automationError("is_completed 必须是布尔值。") }
                    let result = try server.invokeTool(name: tool, arguments: args)
                    if full { results.append(result) }
                }
            }
        } else if let operations {
            let allowed: Set<String> = ["add_course", "update_course", "delete_course", "manage_course_schedule", "add_calendar_event", "update_calendar_event", "delete_calendar_event", "add_task", "update_task", "complete_task", "delete_task"]
            for (index, operation) in operations.enumerated() {
                guard let name = operation["tool"] as? String, allowed.contains(name), let args = operation["arguments"] as? [String: Any] else { throw automationError("第 \(index + 1) 项操作无效或不支持批量执行。") }
                do {
                    let result = try server.invokeTool(name: name, arguments: args)
                    if full { results.append(result) }
                } catch { throw automationError("第 \(index + 1) 项失败，整批未写入：\(error.localizedDescription)") }
            }
            count = operations.count
        }
        var response: [String: Any] = ["batch_id": batchID, "dry_run": dryRun, "count": count, "sample_ids": sampleIDs]
        if selection != nil { response["matched"] = count; response["action"] = arguments["action"] }
        if full { response["results"] = results }
        let result = try textJSON(response)
        if !dryRun {
            payload = staging.load()
            if payload.calendarImportReceipts == nil { payload.calendarImportReceipts = [:] }
            payload.calendarImportReceipts?[key] = CalendarImportReceipt(digest: digest, result: result)
            try saveEdit(payload)
        }
        return result
    }
}
