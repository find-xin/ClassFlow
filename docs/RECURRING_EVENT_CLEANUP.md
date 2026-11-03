# 重复日程取消记录维护

日期：2026-11-04。当前实现仍使用 macOS LocalDatabase 原子 JSON、Android CourseDataManager 的 SharedPreferences JSON；没有进行 SQLite/Room 迁移。

## 根因与设计

过去，重复日程的单次删除仅生成 `ScheduleException(CANCEL)`，发生日期不属于系列自身的持久状态。删除这条例外后，重复生成器自然再次生成原日期。

现在复用 `CalendarEvent`，增加 `excludedDates: ["YYYY-MM-DD"]`。缺少字段的历史数据读取为空数组；写入时去重排序。它表示**重复实例的原始开始日期**，跨天实例的所有切片一起排除，`TimelineItem.recurrenceOriginDate` 保留这个原始日期。重复次数先按原规则计算，再过滤排除日期，因此删除一次不会在结束日期之外补出新实例。

普通编辑和同 UUID 的同步取排除日期并集，包含同步期间本地修改的 rebase，以及强制拉取同 UUID 系列。旧快照缺少字段不能清除本机已有排除日期。不同 UUID 即使同名、同时，也不再被同步合并成一个系列。完整删除系列仍按原流程执行。

## 两端入口与操作

- macOS：设置 → 日历 →「重复日程取消记录维护」。
- Android：设置 → 日程维护。
- 点击「预览批量清理」，核对系列、日期、待移除数量、重复数量和跳过数量。
- 默认同时迁移与清理；关闭「迁移后清理冗余取消记录」可只迁移，之后再预览清理。
- 预览不写业务数据。提交前重新比对完整日程、例外及课程时段 ID；预览过期则拒绝执行。
- 执行前自动写入本机私有恢复备份；备份失败则停止。macOS 在数据库旁 `maintenance-backups/` 保存完整 JSON，目录 0700、文件 0600；Android 在应用私有 `files/maintenance-backups/` 保存日程、例外和删除标记。
- 排除日期、例外删除和同步 tombstone 在同一个原子持久化操作中写入。Android 检查 `commit()` 结果，失败时尝试恢复原快照并明确报错；macOS 使用 JSON 原子替换。

新发生的重复日程「单次删除」直接保存排除日期，不再制造新的 CANCEL。迁移始终保留系列 UUID，不拆成独立日程，也不改变其他业务字段。

## 保守清理规则

仅处理归属于唯一一个重复 CalendarEvent 的已识别 CANCEL，同一系列、同一日期只添加一次排除日期，冗余 CANCEL 可全部清理。

以下记录保持原样：课程时段的 CANCEL、所有 RESCHEDULE/MAKEUP/ROOM_CHANGE、无归属例外、非重复日程的例外、无效日期、含目标调动字段的 CANCEL、同日期混有其他例外、重复 ID 指向不同日期/对象的冲突数据。未来未知例外类型也保留其原始类型，不再被维护逻辑当作普通 CANCEL。

核心 `CalendarCancellationCleanup.preview` 支持可选系列 ID 选择、只迁移与迁移后清理。原记录顺序保持；再次清理没有额外副作用。同步 tombstone 遵守既有保留策略；即使旧取消记录在 tombstone 过期后重回本机，系列排除日期仍能防止日程复活。

## 查询、备份与 ICS

- Mac 视图、MCP 按日期查询、小组件和提醒计划使用共享投影；Android 对应投影镜像相同语义。
- MCP 工具数量/参数未增加；Mac 原有单次删除工具写入永久排除，`get_editing_context` 能读取 `excludedDates`。Android 现有 MCP 查询和非目标字段编辑保留该字段；批量迁移通过原生维护入口执行。
- `.classflow` 新导出为 **formatVersion 2**，新增 `calendarEvents.json` 和数量，校验范围按顺序覆盖 semesters/courses/exceptions/calendarEvents 原始字节。两端导入继续兼容 v1；Android 原先漏读 exceptions 的问题一并修复。缺失组件或校验失败不得假装导入成功。
- `.classflow` 合并导入按 UUID 保留排除日期。Mac 明确选择覆盖导入 v2 时仍可用备份恢复整个日程集合；v1 没有日程组件，保留现有日程。
- ICS 普通 VEVENT 只导出经过投影、未取消的实例；Android 不再额外导出未过滤的系列起点。ClassFlow 生成的 ICS 用可折行的 `X-CLASSFLOW-CALENDAR-EVENT` 属性保留原始系列 JSON，通用日程 VEVENT 标记来源。两端重新导入时恢复原系列而不是将其误判为课程或拆出大量实例。普通外部客户端仍能读取 VEVENT；若外部软件去掉 X 属性，则不保证完整系列往返，应使用 `.classflow` 备份。
- Mac 外来课程 ICS 的周次聚合只使用实际出现的周次，不再因接近全学期而补齐缺失日期。课程既有调课/停课执行逻辑没有改动。

**所有参与设备应先更新再执行清理。** 老版本本身无法理解新增字段；虽然新版合并旧快照时保留排除日期，不能保证老客户端自身的显示与新客户端一致。没有宣称旧二进制已获得新能力。

## 主要文件与函数

| 范围 | 文件/函数 |
| --- | --- |
| Swift 模型与生成 | `CalendarEvent.excludedDates/preservingExclusions`、`CalendarEventRecurrence.occurrenceDates`、`CalendarEventProjector`、`TimelineItem.recurrenceOriginDate` |
| Swift 维护计划 | `CalendarCancellationCleanup.preview` |
| Mac 原子提交/UI | `LocalDatabase.applyCalendarCancellationCleanup`、`AppState.preview/applyCalendarCancellationCleanup`、`CalendarCancellationMaintenanceView`、`SettingsView` |
| Mac 编辑与 MCP | `AppState.delete/updateCalendarEvent`、`MCPCalendarEditing.editCalendar`、`CalendarEditorPreservation`、`MainWindowView` |
| 同步 | `SyncEngine` 快照/操作合并、`LocalSyncRebase.merge`、`FullSyncManager.mergePeerSnapshot`；UUID 删除标记大小写兼容 |
| Android 模型与维护 | `DomainModels`、`CalendarEventProjection.project`、`CalendarCancellationCleanup.preview`、`CourseDataManager` 序列化/保存/原子清理 |
| Android UI | `MainActivity` 单次删除/导入导出、`CalendarCancellationMaintenanceView`、`MacSettingsDialog` |
| 备份与 ICS | `ClassFlowBackupService`、`ClassFlowBackupManager`、两端 `CalendarEventICSMetadata`、`ICSImporter/Exporter`、`ImportPreviewSheet` |
| Schema | `calendar_event.schema.json`、`classflow_backup.schema.json` |

## 验证范围

新增回归覆盖各重复规则、次数上限、跨天起点、迁移预览、只迁移、重复 CANCEL、重复清理、批量 1000 系列/2000 记录、课程隔离、调课冲突、无归属/无效/未知类型保护、持久化重读、备份 v1/v2、篡改校验、ICS 系列往返、MCP 查询、旧编辑器保存和同步排除并集。

[VERIFIED] 已通过的本轮命令、数量见 PROJECT_HANDOVER 最新记录。

[UNVERIFIED] 原生维护界面的人工点击、Android 真机磁盘故障注入、实际 BYOC 云端多设备端到端与系统通知送达未在本轮验收。测试使用临时文件/虚构数据；未迁移或清理用户现有数据，未发布或安装新版本。

### ChatGPT / MCP 入口

后续源码已在两端提供预览和提交工具，仍使用相同的原生维护计划、备份与原子写入。
参见 [MCP 调用流程](CHATGPT_INTEGRATION.md#mcp-接管历史取消维护)。预览默认覆盖
本机所有符合条件的系列；确认参数是调用方的声明，不是操作系统级人工确认。
已打包的 macOS build18 / Android build19 不包含这两个新工具。
