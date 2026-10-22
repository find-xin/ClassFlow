import Foundation
import CryptoKit
import ClassFlowCore

/// Device-local replay receipt, excluded from cloud payloads and exported backups.
public struct CalendarImportReceipt: Codable, Sendable {
    var digest: String
    var result: String
}

extension MCPToolHandlers {
    /// Validate the whole batch before a single atomic save; identical retries never reinsert data.
    public func importCalendarEvents(arguments: [String: Any]) throws -> String {
        guard let batchID = arguments["batch_id"] as? String,
              !batchID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, batchID.utf8.count <= 200,
              let rows = arguments["events"] as? [[String: Any]], (1...2000).contains(rows.count) else {
            throw automationError("请提供 batch_id 和 1–2000 条 events；重试时保持批次 ID 和内容不变。")
        }
        guard Set(arguments.keys).isSubset(of: ["batch_id", "events", "dry_run"]),
              arguments["dry_run"] == nil || arguments["dry_run"] is Bool else {
            throw automationError("未知参数，或 dry_run 不是布尔值。")
        }
        let dryRun = arguments["dry_run"] as? Bool ?? false
        let canonical = try JSONSerialization.data(withJSONObject: rows, options: [.sortedKeys])
        guard canonical.count <= 4 * 1024 * 1024 else { throw automationError("批次超过 4 MiB，请拆分后导入。") }
        let digest = SHA256.hash(data: canonical).map { String(format: "%02x", $0) }.joined()
        var payload = database.load()
        if let receipt = payload.calendarImportReceipts?[batchID] {
            guard receipt.digest == digest else { throw automationError("该 batch_id 已用于不同内容，请为新内容使用新的批次 ID。") }
            var result = try JSONSerialization.jsonObject(with: Data(receipt.result.utf8)) as? [String: Any] ?? [:]
            result["replayed"] = true
            result["dry_run"] = dryRun
            return try importJSON(result)
        }
        let semester = payload.semesters.first(where: { $0.isCurrent }) ?? payload.semesters.first
        let calendarID = UUID()
        var candidates: [CalendarEvent] = []
        for (index, row) in rows.enumerated() {
            do {
                let stringFields: Set<String> = ["title", "date", "start_time", "end_time", "end_date", "location", "notes", "color", "frequency"]
                guard Set(row.keys).isSubset(of: stringFields.union(["is_all_day"])),
                      row.allSatisfy({ key, value in key == "is_all_day" ? value is Bool : value is String }) else {
                    throw automationError("未知字段或字段类型不正确。")
                }
                guard let rawTitle = row["title"] as? String,
                      !rawTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                      let date = row["date"] as? String,
                      let start = row["start_time"] as? String,
                      let end = row["end_time"] as? String else {
                    throw automationError("必须提供 title、date、start_time 和 end_time。")
                }
                let endDate = row["end_date"] as? String ?? date
                try validateDate(date)
                try validateDate(endDate)
                try validateTime(start)
                try validateTime(end)
                let allDay = row["is_all_day"] as? Bool ?? false
                let startStamp = "\(date)T\(start):00"
                let endStamp = "\(endDate)T\(end):00"
                guard endDate >= date, allDay || endStamp > startStamp else { throw automationError("结束时间必须晚于开始时间。") }
                guard (row["frequency"] as? String ?? "none") == "none", row["recurrence_rule"] == nil else {
                    throw automationError("请先将重复日程展开为独立日期，不能静默丢弃重复规则。")
                }
                if let color = row["color"] as? String {
                    guard color.range(of: #"^#[0-9a-fA-F]{6}$"#, options: .regularExpression) != nil else { throw automationError("颜色必须为 #RRGGBB。") }
                }
                candidates.append(CalendarEvent(calendarId: calendarID, semesterId: semester?.id,
                    title: rawTitle.trimmingCharacters(in: .whitespacesAndNewlines),
                    startTime: startStamp, endTime: endStamp, isAllDay: allDay,
                    location: row["location"] as? String, description: row["notes"] as? String,
                    color: (row["color"] as? String) ?? "#FF5A5F", frequency: "none", startDate: date, endDate: endDate))
            } catch { throw automationError("第 \(index + 1) 条日程无效：\(error.localizedDescription) 整批尚未写入。") }
        }
        // Match occurrence identity, excluding notes/color so prior individual imports are adopted.
        func identity(_ event: CalendarEvent) -> [String] {
            [event.title.trimmingCharacters(in: .whitespacesAndNewlines), event.startTime, event.endTime,
             event.isAllDay ? "all-day" : "timed", event.location ?? "", event.frequency ?? "none", event.recurrenceRule ?? ""]
        }
        var seen = Set(payload.calendarEvents.map(identity))
        var inserted: [CalendarEvent] = []
        for candidate in candidates where seen.insert(identity(candidate)).inserted { inserted.append(candidate) }
        let result = try importJSON([
            "batch_id": batchID, "requested": rows.count, "inserted": inserted.count,
            "skipped_duplicates": rows.count - inserted.count,
            "total_after": payload.calendarEvents.count + inserted.count,
            "dry_run": dryRun, "replayed": false
        ])
        if !dryRun {
            payload.calendarEvents.append(contentsOf: inserted)
            if payload.calendarImportReceipts == nil { payload.calendarImportReceipts = [:] }
            payload.calendarImportReceipts?[batchID] = CalendarImportReceipt(digest: digest, result: result)
            try database.save(payload)
            notifyAppAndWidgets(payload: payload)
        }
        return result
    }

    private func importJSON(_ value: [String: Any]) throws -> String {
        String(decoding: try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]), as: UTF8.self)
    }
}

extension MCPServer {
    func calendarImportDefinition() -> [String: Any] {
        ["name": "import_calendar_events",
         "description": "批量迁移日程，一次 1–2000 条。整批校验后原子保存，按标题/起止时间/全天/地点/重复规则去重。重试必须保留 batch_id 及原内容；不会覆盖已有日程。Google 等来源先展开重复实例，转换为本机时区；全天跨日用含最后一天的 end_date。优先批量调用，勿逐条新增或反复列出全量日程。dry_run=true 仅预览。",
         "inputSchema": ["type": "object", "properties": [
            "batch_id": ["type": "string", "maxLength": 200],
            "dry_run": ["type": "boolean", "default": false],
            "events": ["type": "array", "minItems": 1, "maxItems": 2000,
                "items": ["type": "object", "properties": [
                    "title": ["type": "string"], "date": ["type": "string", "description": "YYYY-MM-DD，本机时区"],
                    "end_date": ["type": "string", "description": "可选结束日期；全天为含最后一天的日期"],
                    "start_time": ["type": "string", "description": "HH:mm；全天传 00:00"],
                    "end_time": ["type": "string", "description": "HH:mm；全天传 23:59"],
                    "is_all_day": ["type": "boolean"], "location": ["type": "string"],
                    "notes": ["type": "string"], "color": ["type": "string", "pattern": "^#[0-9a-fA-F]{6}$"],
                    "frequency": ["type": "string", "enum": ["none"]]
                ], "required": ["title", "date", "start_time", "end_time"], "additionalProperties": false]]
         ], "required": ["batch_id", "events"], "additionalProperties": false]]
    }
}
