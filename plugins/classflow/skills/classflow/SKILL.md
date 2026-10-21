---
name: classflow
description: 通过本地 MCP 查询和管理 ClassFlow 课程、日程、待办、专注与已配置的云同步。
---

# ClassFlow

使用已连接的 ClassFlow MCP 工具执行请求；成功与否以工具返回结果为准。
本插件需要本地执行环境。普通 ChatGPT 云端聊天应添加 Secure MCP Tunnel MCP 连接。

- 先用 get_status 确认本机日期、当前学期、当前周和专注状态。
- 今天的课程用 query_timetable(day_of_week: 1–7) 查询；根据实际 start_time 筛选上午或下午。
- 完整编辑前用 get_editing_context(kind, offset, limit) 获取原始字段与 UUID；按 totals 翻页，不要把单页当成全部。读取结果不包含密钥。
- 课程时段使用 manage_course_schedule：add/update/delete/reschedule/cancel/restore；调课、停课用 scope 和 occurrence_date，单次调课可指定 target_date。restore 撤销该次异常；全局编辑用 update_course。schedules 是完整替换，保留现有时段时必须携带 schedule_id。
- 课程使用 add_course、update_course、delete_course；颜色和备注通过 color、notes 修改。
- 日程支持 time_mode=time/sections、跨天 end_date、全天、频次/自定义星期/学期周次、重复结束日期或次数、提醒 alert_minutes_before（null 无提醒，0 开始时）。重复日程修改/删除须明确 scope=all/this_only/this_and_future；后两者需要 occurrence_date。
- 单条日程使用 list_calendar_events、add_calendar_event、update_calendar_event、delete_calendar_event。
- 大量迁移优先 import_calendar_events：每批 1–500 条，先 dry_run 预览，再写入；重试保留相同 batch_id 和完整内容。禁止用几百次 add_calendar_event 代替批量导入。重复日程先展开并转换到本机时区。
- 区分 requested、inserted、skipped_duplicates 和数据库总量；完成源日历所有分页并核对实例后才报告全部迁移成功。超时重试保持批次 ID，不要换 ID 重做。
- 待办支持 course_id、start_date、due_date（YYYY-MM-DD HH:mm）、target_minutes、notes 和完整重复规则。null 清除可选字段；三级优先级不是四象限。完成周期待办会生成下一次，保留历史与精确分钟数。
- 批量修改优先 batch_edit_items：每批 1–100 项，先 dry_run，固定 batch_id 和相同 operations 重试；任一失败整批不写入。操作依次执行，使用预先查询的 UUID，禁止嵌套、同步或专注操作。
- 待办使用 list_tasks、add_task、update_task、complete_task、delete_task。
- 同名项目先查询 UUID，再使用 course_id、event_id 或 task_id 操作；不要任意选择第一个匹配。
- 专注使用 start_focus_timer、pause_focus_timer、resume_focus_timer、stop_focus_timer、reset_focus_timer。start 支持 pomodoro/count_up/countdown，course_id 可开启课程专注；reset 丢弃本次，不计统计。
  停止时由真实计时计算时长，不能编造 actual_minutes；没有会话时不能声称结束成功。
- trigger_cloud_sync 使用 App 内已配置的存储。不要索取、回显或把云密钥写入工具参数。
- 云同步和跨进程操作可能耗时。超时后先查询状态，不要自动重试添加或删除等写操作。
- 工具没有加载时说明连接尚未成功，不能通过命令名称或此说明推断操作已执行。
