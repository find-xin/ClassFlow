---
name: classflow
description: 通过本地 MCP 查询和管理 ClassFlow 课程、日程、待办、专注与已配置的云同步。
---

# ClassFlow

使用已连接的 ClassFlow MCP 工具执行请求；成功与否以工具返回结果为准。
本插件需要本地执行环境。普通 ChatGPT 云端聊天应添加 Secure MCP Tunnel MCP 连接。

- 先用 get_status 确认本机日期、当前学期、当前周和专注状态。
- 今天的课程用 query_timetable(day_of_week: 1–7) 查询；根据实际 start_time 筛选上午或下午。
- 课程使用 add_course、update_course、delete_course；颜色和备注通过 color、notes 修改。
- 日程使用 list_calendar_events、add_calendar_event、update_calendar_event、delete_calendar_event。
- 待办使用 list_tasks、add_task、update_task、complete_task、delete_task。
- 同名项目先查询 UUID，再使用 course_id、event_id 或 task_id 操作；不要任意选择第一个匹配。
- 专注使用 start_focus_timer、pause_focus_timer、resume_focus_timer、stop_focus_timer。
  停止时由真实计时计算时长，不能编造 actual_minutes；没有会话时不能声称结束成功。
- trigger_cloud_sync 使用 App 内已配置的存储。不要索取、回显或把云密钥写入工具参数。
- 云同步和跨进程操作可能耗时。超时后先查询状态，不要自动重试添加或删除等写操作。
- 工具没有加载时说明连接尚未成功，不能通过命令名称或此说明推断操作已执行。
