import Foundation
import ClassFlowCore

/// Native Model Context Protocol (MCP) server for ClassFlow.
/// Enables ChatGPT Desktop (and Claude Desktop, Cursor) to manage courses, timetable,
/// schedules, to-do tasks, Pomodoro focus timer, and cloud sync conversationally.
public final class MCPServer: Sendable {
    public static let shared = MCPServer()
    public let handlers: MCPToolHandlers

    public init(handlers: MCPToolHandlers = MCPToolHandlers()) { self.handlers = handlers }

    // MARK: - Main Entry Point

    public static func run() {
        let args = CommandLine.arguments.filter { $0 != "--mcp" && $0 != "-mcp" }

        if args.contains("--help") || args.contains("-h") {
            printHelp()
            exit(0)
        } else if args.contains("--openapi") {
            print(shared.generateOpenAPISpec())
            exit(0)
        } else if args.count > 1 && args[1] == "tools" {
            shared.printToolsList()
            exit(0)
        } else if args.count > 1 && args[1] == "status" {
            shared.executeToolCallAndPrint(name: "get_status", arguments: [:])
            exit(0)
        } else if args.count > 1 && args[1] == "sync" {
            shared.executeToolCallAndPrint(name: "trigger_cloud_sync", arguments: [:])
            exit(0)
        } else if args.count > 1 && args[1] == "call" {
            guard args.count > 2 else {
                fputs("Usage: classflow-mcp call <tool_name> [json_arguments]\n", stderr)
                exit(1)
            }
            let toolName = args[2]
            var arguments: [String: Any] = [:]
            if args.count > 3 {
                let jsonStr = args[3]
                guard let data = jsonStr.data(using: .utf8),
                   let parsed = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    fputs("Arguments must be a JSON object.\n", stderr)
                    exit(1)
                }
                arguments = parsed
            }
            shared.executeToolCallAndPrint(name: toolName, arguments: arguments)
            exit(0)
        } else if args.count > 1 && (args[1] == "query" || args[1] == "query_timetable") {
            var arguments: [String: Any] = [:]
            if args.count > 2, let week = Int(args[2]) { arguments["week"] = week }
            if args.count > 3, let day = Int(args[3]) { arguments["day_of_week"] = day }
            shared.executeToolCallAndPrint(name: "query_timetable", arguments: arguments)
            exit(0)
        } else if args.count > 1 && args[1] == "courses" {
            shared.executeToolCallAndPrint(name: "list_courses", arguments: [:])
            exit(0)
        } else if args.count > 1 && args[1] == "tasks" {
            shared.executeToolCallAndPrint(name: "list_tasks", arguments: args.count > 2 ? ["filter": args[2]] : [:])
            exit(0)
        } else if args.count > 1 && args[1] == "events" {
            shared.executeToolCallAndPrint(name: "list_calendar_events", arguments: args.count > 2 ? ["date": args[2]] : [:])
            exit(0)
        } else {
            // Default: Standard MCP JSON-RPC Server over stdio
            shared.runStdioLoop()
        }
    }

    // MARK: - Stdio Event Loop (JSON-RPC 2.0)

    public func runStdioLoop() {
        // Log startup to stderr (never stdout to avoid corrupting JSON-RPC protocol)
        fputs("[ClassFlow-MCP] Server initialized and listening on stdio...\n", stderr)

        let inputHandle = FileHandle.standardInput
        var buffer = Data()

        while true {
            let chunk = inputHandle.availableData
            if chunk.isEmpty {
                // EOF reached
                break
            }
            buffer.append(chunk)

            // Process newline-separated JSON-RPC messages
            while let newlineIndex = buffer.firstIndex(of: UInt8(ascii: "\n")) {
                let lineData = buffer.subdata(in: 0..<newlineIndex)
                buffer.removeSubrange(0...newlineIndex)

                if let lineStr = String(data: lineData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
                   !lineStr.isEmpty {
                    handleMessage(lineStr)
                }
            }
        }
    }

    private func handleMessage(_ jsonString: String) {
        guard let data = jsonString.data(using: .utf8),
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            sendError(id: nil, code: -32700, message: "Parse error: Invalid JSON")
            return
        }

        let id = json["id"]
        let method = json["method"] as? String ?? ""
        let params = json["params"] as? [String: Any] ?? [:]

        switch method {
        case "initialize":
            let result: [String: Any] = [
                "protocolVersion": ["2024-11-05", "2025-03-26", "2025-06-18"].contains(params["protocolVersion"] as? String ?? "") ? (params["protocolVersion"] as! String) : "2025-06-18",
                "capabilities": [
                    "tools": [
                        "listChanged": false
                    ]
                ],
                "serverInfo": [
                    "name": "classflow-mcp",
                    "version": "1.2.0"
                ]
            ]
            sendResult(id: id, result: result)

        case "notifications/initialized":
            // Notification from client; no response needed
            break

        case "ping":
            sendResult(id: id, result: [:])

        case "tools/list":
            let tools = defineTools()
            sendResult(id: id, result: ["tools": tools])

        case "tools/call":
            guard let toolName = params["name"] as? String else {
                sendError(id: id, code: -32602, message: "Invalid params: Missing tool name")
                return
            }
            let arguments = params["arguments"] as? [String: Any] ?? [:]
            executeToolCall(id: id, name: toolName, arguments: arguments)

        case "resources/list":
            sendResult(id: id, result: ["resources": []])

        case "resources/templates/list":
            sendResult(id: id, result: ["resourceTemplates": []])

        case "prompts/list":
            sendResult(id: id, result: ["prompts": []])

        case "logging/setLevel":
            sendResult(id: id, result: [:])

        default:
            if id != nil {
                sendError(id: id, code: -32601, message: "Method not found: \(method)")
            }
        }
    }

    // MARK: - Tool Execution

    public func executeToolCallAndPrint(name: String, arguments: [String: Any]) {
        do {
            let outputText = try invokeTool(name: name, arguments: arguments)
            print(outputText)
        } catch {
            fputs("Error executing \(name): \(error.localizedDescription)\n", stderr)
            exit(1)
        }
    }

    private func executeToolCall(id: Any?, name: String, arguments: [String: Any]) {
        do {
            let outputText = try invokeTool(name: name, arguments: arguments)
            let response: [String: Any] = [
                "content": [
                    [
                        "type": "text",
                        "text": outputText
                    ]
                ],
                "isError": false
            ]
            sendResult(id: id, result: response)
        } catch {
            let errorResponse: [String: Any] = [
                "content": [
                    [
                        "type": "text",
                        "text": "操作失败：\(error.localizedDescription)"
                    ]
                ],
                "isError": true
            ]
            sendResult(id: id, result: errorResponse)
        }
    }

    func invokeTool(name: String, arguments: [String: Any]) throws -> String {
        try validateEditingArguments(name: name, arguments: arguments)
        if handlers.enablesAppIntegration {
            return try MCPDesktopBridge.call(tool: name, arguments: arguments)
        }
        try handlers.database.validateForAutomation()
        switch name {
        case "get_editing_context": return try handlers.editingContext(arguments: arguments)
        case "manage_course_schedule": return try handlers.manageCourseSchedule(arguments: arguments)
        case "batch_edit_items": return try handlers.batchEdit(arguments: arguments)
        case "reset_focus_timer": return try handlers.resetFocus()
        case "get_status":
            let status = handlers.getStatus()
            let data = try JSONSerialization.data(withJSONObject: status, options: [.prettyPrinted])
            return String(data: data, encoding: .utf8) ?? "{}"

        case "query_timetable":
            let week = arguments["week"] as? Int
            let dayOfWeek = arguments["day_of_week"] as? Int
            let result = handlers.queryTimetable(week: week, dayOfWeek: dayOfWeek)
            let data = try JSONSerialization.data(withJSONObject: result, options: [.prettyPrinted])
            return String(data: data, encoding: .utf8) ?? "{}"

        case "add_course":
            return try handlers.addCourse(arguments: arguments)

        case "update_course":
            return try handlers.updateCourse(arguments: arguments)

        case "delete_course":
            return try handlers.deleteCourse(arguments: arguments)

        case "list_courses":
            let list = handlers.listCourses()
            let data = try JSONSerialization.data(withJSONObject: list, options: [.prettyPrinted])
            return String(data: data, encoding: .utf8) ?? "[]"

        case "list_calendar_events":
            let date = arguments["date"] as? String
            let list = handlers.listCalendarEvents(date: date)
            let data = try JSONSerialization.data(withJSONObject: list, options: [.prettyPrinted])
            return String(data: data, encoding: .utf8) ?? "[]"

        case "import_calendar_events":
            return try handlers.importCalendarEvents(arguments: arguments)
        case "add_calendar_event":
            return try handlers.addCalendarEvent(arguments: arguments)
        case "update_calendar_event":
            return try handlers.updateCalendarEvent(arguments: arguments)
        case "update_task":
            return try handlers.updateTask(arguments: arguments)
        case "pause_focus_timer":
            return try handlers.pauseFocusTimer(resume: false)
        case "resume_focus_timer":
            return try handlers.pauseFocusTimer(resume: true)

        case "delete_calendar_event":
            return try handlers.deleteCalendarEvent(arguments: arguments)

        case "list_tasks":
            let filter = arguments["filter"] as? String
            let list = handlers.listTasks(filter: filter)
            let data = try JSONSerialization.data(withJSONObject: list, options: [.prettyPrinted])
            return String(data: data, encoding: .utf8) ?? "[]"

        case "add_task":
            return try handlers.addTask(arguments: arguments)

        case "complete_task":
            return try handlers.completeTask(arguments: arguments)

        case "delete_task":
            return try handlers.deleteTask(arguments: arguments)

        case "start_focus_timer":
            return try handlers.startFocusTimer(arguments: arguments)

        case "stop_focus_timer":
            return try handlers.stopFocusTimer(arguments: arguments)

        case "get_focus_statistics":
            let stats = handlers.getFocusStatistics()
            let data = try JSONSerialization.data(withJSONObject: stats, options: [.prettyPrinted])
            return String(data: data, encoding: .utf8) ?? "{}"

        case "trigger_cloud_sync":
            let sem = DispatchSemaphore(value: 0)
            var syncOutput = ""
            var syncErr: Error?
            Task {
                do {
                    syncOutput = try await self.handlers.triggerCloudSync()
                } catch {
                    syncErr = error
                }
                sem.signal()
            }
            sem.wait()
            if let err = syncErr {
                throw err
            }
            return syncOutput

        case "switch_semester_or_week":
            return try handlers.switchSemesterOrWeek(arguments: arguments)

        case "open_app":
            let tab = arguments["tab"] as? String
            return handlers.openApp(tab: tab)

        default:
            throw NSError(domain: "ClassFlowMCP", code: -32601, userInfo: [NSLocalizedDescriptionKey: "Unknown tool name: \(name)"])
        }
    }

    // MARK: - Protocol Helpers

    private func sendResult(id: Any?, result: [String: Any]) {
        guard let id = id else { return }
        let payload: [String: Any] = [
            "jsonrpc": "2.0",
            "id": id,
            "result": result
        ]
        sendPayload(payload)
    }

    private func sendError(id: Any?, code: Int, message: String) {
        var payload: [String: Any] = [
            "jsonrpc": "2.0",
            "error": [
                "code": code,
                "message": message
            ]
        ]
        if let id = id {
            payload["id"] = id
        } else {
            payload["id"] = NSNull()
        }
        sendPayload(payload)
    }

    private func sendPayload(_ dict: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let str = String(data: data, encoding: .utf8) else {
            return
        }
        // Write raw JSON + newline to stdout and flush immediately
        print(str)
        fflush(stdout)
    }

    // MARK: - Tool Definitions

    public func defineTools() -> [[String: Any]] {
        var tools: [[String: Any]] = [
            [
                "name": "get_status",
                "description": "获取 ClassFlow 当前运行状态、激活学期、当前周次、今日日期、进行中待办数量与当前专注状态。",
                "inputSchema": [
                    "type": "object",
                    "properties": [:] as [String: Any]
                ]
            ],
            [
                "name": "query_timetable",
                "description": "查询指定周次或某一天的课程和日程列表，支持获取课程名、节次、时间、地点、教师、学分、颜色、备注、是否本周等完整信息。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "week": [
                            "type": "integer",
                            "description": "查询周次（1..30），默认当前周。"
                        ],
                        "day_of_week": [
                            "type": "integer",
                            "description": "星期几（1=周一 .. 7=周日）。不填则返回整周所有课程日程。"
                        ]
                    ]
                ]
            ],
            [
                "name": "add_course",
                "description": "添加一门新课程至当前学期，支持设置授课教师、学分、卡片颜色、备注以及详细上课时段（星期、起止节次、教室、起止周次、单双周等）。若同名课程已存在，则自动追加新时段。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "name": [
                            "type": "string",
                            "description": "课程名称（如：'操作系统'、'高等数学'）"
                        ],
                        "day_of_week": [
                            "type": "integer",
                            "description": "星期几：1 (周一) 到 7 (周日)"
                        ],
                        "start_section": [
                            "type": "integer",
                            "description": "开始节次（如：1，表示第 1 节）"
                        ],
                        "end_section": [
                            "type": "integer",
                            "description": "结束节次（如：2，表示到第 2 节结束）"
                        ],
                        "classroom": [
                            "type": "string",
                            "description": "教室或上课地点（如：'教三 201'、'计算机实验楼 304'）"
                        ],
                        "teacher": [
                            "type": "string",
                            "description": "授课教师姓名（如：'张教授'）"
                        ],
                        "credit": [
                            "type": "number",
                            "description": "课程学分（如：3.0、4.5）"
                        ],
                        "color": [
                            "type": "string",
                            "description": "卡片颜色十六进制（如 '#4F46E5'）或颜色名（'blue', 'green', 'purple', 'emerald', 'amber', 'orange', 'rose', 'pink'）"
                        ],
                        "notes": [
                            "type": "string",
                            "description": "课程备注或考试重点说明"
                        ],
                        "start_week": [
                            "type": "integer",
                            "description": "起始周（默认 1）"
                        ],
                        "end_week": [
                            "type": "integer",
                            "description": "结束周（默认 16）"
                        ],
                        "week_pattern": [
                            "type": "string",
                            "description": "周次模式：'all'=每周, 'odd'=单周, 'even'=双周, 'custom'=自定义周次"
                        ],
                        "custom_weeks": [
                            "type": "array",
                            "items": ["type": "integer"],
                            "description": "自定义上课周次数组（如 [1, 2, 3, 5, 7, 9]）"
                        ]
                    ],
                    "required": ["name", "day_of_week", "start_section"]
                ]
            ],
            [
                "name": "update_course",
                "description": "修改已有课程的属性（支持按课程名称模糊或精确匹配），可更新课程名称、教师、学分、卡片颜色、备注、教室地点等。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "name": [
                            "type": "string",
                            "description": "要修改的已有课程名称"
                        ],
                        "new_name": [
                            "type": "string",
                            "description": "重命名后的新课程名称"
                        ],
                        "teacher": [
                            "type": "string",
                            "description": "更新后的授课教师"
                        ],
                        "credit": [
                            "type": "number",
                            "description": "更新后的学分"
                        ],
                        "color": [
                            "type": "string",
                            "description": "更新后的颜色（如 '#10B981' 或 'emerald'）"
                        ],
                        "notes": [
                            "type": "string",
                            "description": "更新后的课程备注"
                        ],
                        "classroom": [
                            "type": "string",
                            "description": "更新后的教室地点"
                        ]
                    ],
                    "required": ["name"]
                ]
            ],
            [
                "name": "delete_course",
                "description": "删除课程。支持彻底删除整门课程、仅删除某一天的单次课时、或删除从某周开始的随后所有课时。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "name": [
                            "type": "string",
                            "description": "课程名称"
                        ],
                        "scope": [
                            "type": "string",
                            "description": "删除范围：'all'=彻底删除该课程, 'this_and_future'=删除指定周及以后的课时, 'this_only'=仅删除单次课时。默认 'all'"
                        ],
                        "week": [
                            "type": "integer",
                            "description": "当 scope 为 'this_and_future' 时指定的周次"
                        ],
                        "date": [
                            "type": "string",
                            "description": "当 scope 为 'this_only' 时指定的日期 ('YYYY-MM-DD')"
                        ]
                    ],
                    "required": ["name"]
                ]
            ],
            [
                "name": "list_courses",
                "description": "获取当前学期的全部课程列表（汇总查看每门课程的名称、教师、学分、时段数量、总学时与周次分布）。",
                "inputSchema": [
                    "type": "object",
                    "properties": [:] as [String: Any]
                ]
            ],
            [
                "name": "list_calendar_events",
                "description": "查询通用日程安排（会议、考试、日常活动等）。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "date": [
                            "type": "string",
                            "description": "可选：查询指定日期的日程 ('YYYY-MM-DD')"
                        ]
                    ]
                ]
            ],
            [
                "name": "add_calendar_event",
                "description": "添加通用日程（如会议、讲座、考试、活动等）。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "title": [
                            "type": "string",
                            "description": "日程标题（如：'系里组会'、'体测'）"
                        ],
                        "date": [
                            "type": "string",
                            "description": "日程日期 ('YYYY-MM-DD')"
                        ],
                        "start_time": [
                            "type": "string",
                            "description": "开始时间 ('HH:mm'，如 '14:30')"
                        ],
                        "end_time": [
                            "type": "string",
                            "description": "结束时间 ('HH:mm'，如 '16:00')"
                        ],
                        "is_all_day": [
                            "type": "boolean",
                            "description": "是否为全天事项"
                        ],
                        "location": [
                            "type": "string",
                            "description": "地点"
                        ],
                        "frequency": [
                            "type": "string",
                            "description": "重复周期：'none'=不重复, 'daily'=每天, 'workdays'=工作日, 'weekly'=每周, 'weekends'=周末"
                        ],
                        "color": [
                            "type": "string",
                            "description": "颜色十六进制"
                        ],
                        "notes": [
                            "type": "string",
                            "description": "备注信息"
                        ]
                    ],
                    "required": ["title", "date"]
                ]
            ],
            [
                "name": "delete_calendar_event",
                "description": "删除通用日程（按标题匹配）。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "title": [
                            "type": "string",
                            "description": "日程标题"
                        ]
                    ],
                    "required": ["title"]
                ]
            ],
            [
                "name": "list_tasks",
                "description": "查询番茄钟待办事项列表（支持按进行中、已完成、全部筛选）。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "filter": [
                            "type": "string",
                            "description": "任务筛选：'in_progress'=进行中, 'completed'=已完成, 'all'=全部。默认 'in_progress'"
                        ]
                    ]
                ]
            ],
            [
                "name": "add_task",
                "description": "添加待办任务，可设定预估番茄钟数、优先级与截止日期。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "title": [
                            "type": "string",
                            "description": "任务标题（如：'完成离散数学大作业'）"
                        ],
                        "estimated_pomodoros": [
                            "type": "integer",
                            "description": "预估番茄钟个数（默认 1）"
                        ],
                        "priority": [
                            "type": "integer",
                            "description": "优先级：0=普通, 1=重要, 2=紧急, 3=重要且紧急"
                        ],
                        "due_date": [
                            "type": "string",
                            "description": "截止日期 ('YYYY-MM-DD')"
                        ]
                    ],
                    "required": ["title"]
                ]
            ],
            [
                "name": "complete_task",
                "description": "将待办任务标记为已完成（或重新标记为未完成）。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "title": [
                            "type": "string",
                            "description": "任务标题"
                        ],
                        "is_completed": [
                            "type": "boolean",
                            "description": "是否已完成，默认 true"
                        ]
                    ],
                    "required": ["title"]
                ]
            ],
            [
                "name": "delete_task",
                "description": "删除待办任务。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "title": [
                            "type": "string",
                            "description": "任务标题"
                        ]
                    ],
                    "required": ["title"]
                ]
            ],
            [
                "name": "start_focus_timer",
                "description": "开启专注计时模式（番茄钟）。在 ClassFlow 本地 macOS 应用中自动切换到学习专注界面并启动倒计时时钟。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "task_title": [
                            "type": "string",
                            "description": "专注对应的任务标题（若不存在会自动创建），若不填则使用默认专注"
                        ],
                        "duration_minutes": [
                            "type": "integer",
                            "description": "专注时长（分钟），默认 25 分钟"
                        ]
                    ]
                ]
            ],
            [
                "name": "stop_focus_timer",
                "description": "结束当前专注计时，并将专注时长与记录自动保存到统计热力图。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "task_title": [
                            "type": "string",
                            "description": "记录对应的任务标题"
                        ],
                        "actual_minutes": [
                            "type": "integer",
                            "description": "实际专注时长（分钟）。默认 25"
                        ]
                    ]
                ]
            ],
            [
                "name": "get_focus_statistics",
                "description": "获取专注统计数据，包含今日专注总时长、累计完成番茄钟数、历史专注分布。",
                "inputSchema": [
                    "type": "object",
                    "properties": [:] as [String: Any]
                ]
            ],
            [
                "name": "trigger_cloud_sync",
                "description": "立即执行多端云同步（支持已配置的 WebDAV、AWS S3 或七牛云 Kodo），拉取其他设备变更并推送本地最新修改。",
                "inputSchema": [
                    "type": "object",
                    "properties": [:] as [String: Any]
                ]
            ],
            [
                "name": "switch_semester_or_week",
                "description": "切换当前查看的学期或周次。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "semester_name": [
                            "type": "string",
                            "description": "目标学期名称"
                        ],
                        "week": [
                            "type": "integer",
                            "description": "目标周次（1..30）"
                        ]
                    ]
                ]
            ],
            [
                "name": "open_app",
                "description": "呼出或唤醒 ClassFlow 桌面端窗口，可导航到指定标签页（课表、日程、专注学习、设置）。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "tab": [
                            "type": "string",
                            "description": "目标标签页：'timetable'=课表, 'schedule'=日程, 'study'=专注学习, 'settings'=设置"
                        ]
                    ]
                ]
            ]
        ]
        tools.append(contentsOf: managementToolDefinitions())
        tools.append(calendarImportDefinition())
        let readOnly: Set<String> = ["get_status", "query_timetable", "list_courses", "list_calendar_events", "list_tasks", "get_focus_statistics"]
        for i in tools.indices {
            let name = tools[i]["name"] as? String ?? ""
            tools[i]["annotations"] = [
                "readOnlyHint": readOnly.contains(name),
                "destructiveHint": name.hasPrefix("delete_"),
                "idempotentHint": readOnly.contains(name) || name.hasPrefix("update_") || name == "complete_task" || name == "import_calendar_events",
                "openWorldHint": name == "trigger_cloud_sync"
            ]
            var schema = tools[i]["inputSchema"] as? [String: Any] ?? [:]
            var properties = schema["properties"] as? [String: Any] ?? [:]
            if name == "update_course" {
                properties["schedule_id"] = ["type": "string", "description": "修改星期、节次或周次时，多个课时必须指定此 UUID。"]
                for field in ["day_of_week", "start_section", "end_section", "start_week", "end_week"] { properties[field] = ["type": "integer"] }
                properties["week_pattern"] = ["type": "string", "enum": ["all", "odd", "even", "custom"]]
                properties["custom_weeks"] = ["type": "array", "items": ["type": "integer"]]
            }
            if name == "start_focus_timer" { properties["task_id"] = ["type": "string", "description": "待办 UUID；与 task_title 二选一。"] }
            if name == "stop_focus_timer" {
                properties = [:]
                tools[i]["description"] = "结束当前专注会话，按真实计时时长保存记录。没有会话时返回错误；重复停止不会重复记账。"
            }
            if name == "add_task" { properties["notes"] = ["type": "string", "description": "任务备注"] }
            for (tool, field) in [("update_course", "course_id"), ("delete_course", "course_id"), ("delete_calendar_event", "event_id"), ("delete_task", "task_id"), ("complete_task", "task_id")] where name == tool {
                properties[field] = ["type": "string", "description": "项目 UUID；重名时使用 UUID。"]
                schema.removeValue(forKey: "required")
            }
            schema["properties"] = properties
            tools[i]["inputSchema"] = schema
        }
        for replacement in completeEditingDefinitions() {
            let name = replacement["name"] as? String ?? ""
            let readOnly = name == "get_editing_context"
            var definition = replacement
            definition["annotations"] = ["readOnlyHint": readOnly, "destructiveHint": ["delete_course", "delete_calendar_event", "manage_course_schedule", "update_course", "update_calendar_event", "reset_focus_timer", "batch_edit_items"].contains(name),
                "idempotentHint": readOnly || name == "reset_focus_timer" || name == "batch_edit_items", "openWorldHint": false]
            if let index = tools.firstIndex(where: { $0["name"] as? String == name }) { tools[index] = definition }
            else { tools.append(definition) }
        }
        return tools
    }

    private func printToolsList() {
        let tools = defineTools()
        print("ClassFlow MCP Available Tools (\(tools.count)):")
        for t in tools {
            let name = t["name"] as? String ?? ""
            let desc = t["description"] as? String ?? ""
            print("  - \(name): \(desc)")
        }
    }

    public func generateOpenAPISpec() -> String {
        let tools = defineTools()
        var spec: [String: Any] = [
            "openapi": "3.1.0",
            "info": [
                "title": "ClassFlow API",
                "version": "1.2.0",
                "description": "OpenAPI specification for ClassFlow local macOS companion."
            ],
            "servers": [
                ["url": "http://localhost:31415"]
            ],
            "paths": [:] as [String: Any]
        ]

        var paths: [String: Any] = [:]
        for tool in tools {
            let name = tool["name"] as? String ?? ""
            let desc = tool["description"] as? String ?? ""
            let inputSchema = tool["inputSchema"] as? [String: Any] ?? [:]

            paths["/api/v1/tools/\(name)"] = [
                "post": [
                    "summary": desc,
                    "operationId": name,
                    "requestBody": [
                        "required": true,
                        "content": [
                            "application/json": [
                                "schema": inputSchema
                            ]
                        ]
                    ],
                    "responses": [
                        "200": [
                            "description": "Successful operation",
                            "content": [
                                "application/json": [
                                    "schema": [
                                        "type": "object",
                                        "properties": [
                                            "content": [
                                                "type": "array",
                                                "items": ["type": "object"]
                                            ],
                                            "isError": ["type": "boolean"]
                                        ]
                                    ]
                                ]
                            ]
                        ]
                    ]
                ]
            ]
        }
        spec["paths"] = paths

        if let data = try? JSONSerialization.data(withJSONObject: spec, options: [.prettyPrinted, .sortedKeys]),
           let str = String(data: data, encoding: .utf8) {
            return str
        }
        return "{}"
    }

    private static func printHelp() {
        print("""
        ClassFlow MCP (Model Context Protocol) Server & CLI
        Version 1.0.0 (Native Apple Silicon macOS)

        USAGE:
            classflow-mcp                     Run MCP JSON-RPC stdio server (for ChatGPT Desktop / Claude Desktop)
            classflow-mcp tools               List all available MCP tools and their capabilities
            classflow-mcp status              Display current app status (semester, week, tasks, focus)
            classflow-mcp sync                Trigger cloud sync immediately
            classflow-mcp --openapi           Export OpenAPI 3.1 schema for ChatGPT Custom Actions

        CONFIGURATION FOR CHATGPT DESKTOP:
            In ChatGPT for Mac, open Settings -> Developer -> MCP Servers (or edit mcp.json):
            {
              "mcpServers": {
                "classflow": {
                  "command": "/Applications/ClassFlow.app/Contents/MacOS/classflow-mcp"
                }
              }
            }

        CONFIGURATION FOR CLAUDE DESKTOP:
            In ~/Library/Application Support/Claude/claude_desktop_config.json:
            {
              "mcpServers": {
                "classflow": {
                  "command": "/Applications/ClassFlow.app/Contents/MacOS/classflow-mcp"
                }
              }
            }
        """)
    }
}
