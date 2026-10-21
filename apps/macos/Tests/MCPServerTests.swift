import Foundation
import Testing
import ClassFlowCore
@testable import ClassFlow

@Suite("MCPServerTests")
struct MCPServerTests {
    func makeIsolatedHandler() -> (MCPToolHandlers, LocalDatabase) {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let dbURL = tempDir.appendingPathComponent("mcp_test_db.json")
        let db = LocalDatabase(customURL: dbURL)
        let handlers = MCPToolHandlers(database: db)
        return (handlers, db)
    }

    @Test("Verify all 19 MCP tools are defined and registered")
    func testToolsListCompleteness() {
        let server = MCPServer()
        let tools = server.defineTools()
        #expect(tools.count == 19)

        let toolNames = Set(tools.compactMap { $0["name"] as? String })
        let expectedNames: Set<String> = [
            "get_status",
            "query_timetable",
            "add_course",
            "update_course",
            "delete_course",
            "list_courses",
            "list_calendar_events",
            "add_calendar_event",
            "delete_calendar_event",
            "list_tasks",
            "add_task",
            "complete_task",
            "delete_task",
            "start_focus_timer",
            "stop_focus_timer",
            "get_focus_statistics",
            "trigger_cloud_sync",
            "switch_semester_or_week",
            "open_app"
        ]
        #expect(toolNames == expectedNames)
    }

    @Test("Verify OpenAPI 3.1 schema generation")
    func testOpenAPISchemaGeneration() throws {
        let server = MCPServer()
        let openapiStr = server.generateOpenAPISpec()
        #expect(!openapiStr.isEmpty)

        let data = try #require(openapiStr.data(using: .utf8))
        let json = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        #expect(json["openapi"] as? String == "3.1.0")

        let paths = try #require(json["paths"] as? [String: Any])
        #expect(paths["/api/v1/tools/add_course"] != nil)
        #expect(paths["/api/v1/tools/query_timetable"] != nil)
        #expect(paths["/api/v1/tools/start_focus_timer"] != nil)
    }

    @Test("MCP Tool: get_status returns current semester and week")
    func testGetStatus() throws {
        let (handlers, _) = makeIsolatedHandler()
        let status = handlers.getStatus()
        let sem = try #require(status["current_semester"] as? [String: Any])
        #expect(sem["name"] as? String == "2026-2027 秋季学期")
        #expect(sem["current_week"] as? Int != nil)
        let counts = try #require(status["counts"] as? [String: Any])
        #expect(counts["courses"] as? Int != nil)
    }

    @Test("MCP Course Lifecycle: add, query, update, and delete")
    func testCourseLifecycle() throws {
        let (handlers, db) = makeIsolatedHandler()

        // 1. Add course
        let addResult = try handlers.addCourse(arguments: [
            "name": "人工智能安全",
            "teacher": "张教授",
            "classroom": "信研楼 402",
            "day_of_week": 3,
            "start_section": 1,
            "end_section": 2,
            "start_week": 1,
            "end_week": 16,
            "credit": 3.0,
            "color": "#10B981",
            "notes": "期末大作业形式"
        ])
        #expect(addResult.contains("人工智能安全"))

        // Verify in database
        let payload1 = db.load()
        let course = try #require(payload1.courses.first(where: { $0.name == "人工智能安全" }))
        #expect(course.teacher == "张教授")
        #expect(course.credit == 3.0)
        #expect(course.color.caseInsensitiveCompare("#10B981") == .orderedSame)
        #expect(course.notes == "期末大作业形式")
        #expect(course.schedules.count == 1)
        #expect(course.schedules[0].dayOfWeek == 3)
        #expect(course.schedules[0].classroom == "信研楼 402")

        // 2. Query timetable
        let queryResult = handlers.queryTimetable(week: 1, dayOfWeek: 3)
        let items = queryResult["items"] as? [[String: Any]] ?? []
        #expect(items.contains(where: { ($0["title"] as? String) == "人工智能安全" }))

        // 3. Update course
        let updateResult = try handlers.updateCourse(arguments: [
            "name": "人工智能安全",
            "new_name": "人工智能安全与隐私计算",
            "teacher": "张资深教授",
            "credit": 3.5
        ])
        #expect(updateResult.contains("成功更新"))

        let payload2 = db.load()
        let updatedCourse = try #require(payload2.courses.first(where: { $0.id == course.id }))
        #expect(updatedCourse.name == "人工智能安全与隐私计算")
        #expect(updatedCourse.teacher == "张资深教授")
        #expect(updatedCourse.credit == 3.5)

        // 4. Delete course
        let deleteResult = try handlers.deleteCourse(arguments: [
            "name": "人工智能安全与隐私计算"
        ])
        #expect(deleteResult.contains("删除"))

        let payload3 = db.load()
        #expect(payload3.courses.first(where: { $0.id == course.id }) == nil)
        #expect(payload3.tombstones.contains(where: { $0.entityId == course.id.uuidString }))
    }

    @Test("MCP Calendar Event Lifecycle: add, list, delete")
    func testCalendarEventLifecycle() throws {
        let (handlers, db) = makeIsolatedHandler()

        // 1. Add calendar event
        let addResult = try handlers.addCalendarEvent(arguments: [
            "title": "计算机系学术研讨会",
            "date": "2026-10-25",
            "start_time": "14:00",
            "end_time": "16:00",
            "location": "图书馆一层报告厅",
            "color": "#6366F1",
            "notes": "需要提前准备个人报告 PPT"
        ])
        #expect(addResult.contains("计算机系学术研讨会"))

        // 2. List calendar events
        let listResult = handlers.listCalendarEvents(date: nil)
        #expect(listResult.contains(where: { ($0["title"] as? String) == "计算机系学术研讨会" }))
        #expect(listResult.contains(where: { ($0["location"] as? String) == "图书馆一层报告厅" }))

        // 3. Delete calendar event
        let payload = db.load()
        let event = try #require(payload.calendarEvents.first(where: { $0.title == "计算机系学术研讨会" }))
        let deleteResult = try handlers.deleteCalendarEvent(arguments: [
            "event_id": event.id.uuidString
        ])
        #expect(deleteResult.contains("删除"))

        let payloadAfter = db.load()
        #expect(payloadAfter.calendarEvents.first(where: { $0.id == event.id }) == nil)
        #expect(payloadAfter.tombstones.contains(where: { $0.entityId == event.id.uuidString }))
    }

    @Test("MCP Task & Focus Timer Lifecycle: add task, start/stop timer, complete task")
    func testTaskAndFocusLifecycle() throws {
        let (handlers, db) = makeIsolatedHandler()

        // 1. Add task
        let addResult = try handlers.addTask(arguments: [
            "title": "撰写分布式系统实验报告",
            "priority": "high",
            "estimated_pomodoros": 3,
            "notes": "重点写 Paxos 算法容错分析"
        ])
        #expect(addResult.contains("撰写分布式系统实验报告"))

        let payload1 = db.load()
        let task = try #require(payload1.pomodoroTasks.first(where: { $0.title == "撰写分布式系统实验报告" }))
        #expect(task.priority == .high)
        #expect(task.estimatedPomodoros == 3)

        // 2. Start Focus Timer
        let startResult = try handlers.startFocusTimer(arguments: [
            "task_id": task.id.uuidString,
            "duration_minutes": 25
        ])
        #expect(startResult.contains("已为任务"))
        #expect(startResult.contains("25 分钟"))

        // 3. Stop Focus Timer
        let stopResult = try handlers.stopFocusTimer(arguments: [
            "task_title": "撰写分布式系统实验报告",
            "actual_minutes": 25
        ])
        #expect(stopResult.contains("已结束"))

        let payload2 = db.load()
        #expect(payload2.pomodoroRecords.contains(where: { $0.taskTitle == "撰写分布式系统实验报告" && $0.durationMinutes == 25 }))

        // 4. Get Focus Statistics
        let statsResult = handlers.getFocusStatistics()
        #expect(statsResult["today_focus_minutes"] as? Int != nil)

        // 5. Complete Task
        let completeResult = try handlers.completeTask(arguments: [
            "task_id": task.id.uuidString
        ])
        #expect(completeResult.contains("标记为已完成"))

        let payload3 = db.load()
        let completedTask = try #require(payload3.pomodoroTasks.first(where: { $0.id == task.id }))
        #expect(completedTask.isCompleted == true)

        // 6. Delete Task
        let deleteResult = try handlers.deleteTask(arguments: [
            "task_id": task.id.uuidString
        ])
        #expect(deleteResult.contains("删除"))

        let payload4 = db.load()
        #expect(payload4.pomodoroTasks.first(where: { $0.id == task.id }) == nil)
        #expect(payload4.tombstones.contains(where: { $0.entityId == task.id.uuidString }))
    }

    @Test("MCP Switch week operation")
    func testSwitchWeek() throws {
        let (handlers, _) = makeIsolatedHandler()
        let switchResult = try handlers.switchSemesterOrWeek(arguments: [
            "week": 7
        ])
        #expect(switchResult.contains("第 7 周"))
    }
}
