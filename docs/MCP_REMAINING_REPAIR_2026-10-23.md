# 提醒恢复与剩余问题验收（2026-10-23）

本记录更新并补充build8报告，不把历史测试结论当作本轮实机验收。

## 新确认的根因

1. build8已修复网络等待期间MCP写入被同步副本覆盖，但安装版重启验收又出现19条提醒全部丢失。
2. 普通同步applyPeerSnapshot按UUID整条替换，旧端未携带提醒字段时会把本地配置覆盖成nil。SyncEngine的peerSequences原来只在内存，重启后会再次应用已处理的旧快照。
3. 通知调度原来直接读取界面缓存，已用隔离回归复现：磁盘提醒为10时，旧缓存nil会进入调度器。改为一次读取本地持久化快照，避免IPC/同步提交后的旧视图数据影响提醒。
4. 编辑器保存会把表单推算的节次、未使用的重复次数、空字段与时间格式写回；提醒虽然保持，但无关字段发生变化。

修复：CalendarEvent新增可选reminderConfigured，区分旧端无字段与新端明确无提醒。旧端缺失值保留本机配置，新端显式清空仍传播。Swift构造/修改和Android编解码/编辑器/自动化都保留该语义。SyncEngine在快照和UPDATE入口应用兼容保护；Mac本地DatabasePayload新增可选syncPeerSequences，初始化和保存MCP操作均保留已处理序号。序号仅存本机，不加入云快照或业务导出。CalendarEditorPreservation保留未改变的格式、空值及隐藏字段。

这些是兼容性新增字段，不需要删库或重建日程。旧客户端不能可靠表达显式清空提醒，需升级后执行该操作；不能据此宣称所有旧版客户端写入都具备新语义。显式强制云端恢复保留其覆盖行为。

## 历史恢复

先读取Google Calendar原系列的reminders，再匹配ClassFlow标题、时间与拆分系列。Google Calendar全程只读，ClassFlow只更新原UUID的提醒。

- 起床与早餐、睡觉：开始时，共2系列。
- 科研A/B、技术、英语、科研整理、运动、短暂休息、晚餐及两项Sports：提前10分钟，共13系列。
- 午餐与午休、洗澡与整理、每日复盘、自由活动：无提醒，共4系列。

“短暂休息”“晚餐”的10分钟来自原Google系列的明确popup规则，未按名称猜测。科研等两段系列分别匹配相同的原时间块规则。

备份与逐UUID恢复计划保存在用户私有Application Support/ClassFlow/Backups/reminder-recovery-20261023-152841，未纳入仓库。首次恢复在build9重启时再次丢失，作为实际失败证据。修复后build10再次恢复，连续两次退出重启仍保持19条；在真实编辑器打开指定科研整理系列，显示提前10分钟，保存后云端MCP仍读到10。按UUID/集合语义比较，除提醒与兼容标记外，19条日程其他业务字段与恢复前一致。旧版UI验收引起的字段规范化已在App退出时按备份恢复，随后新编辑器保存不再产生这些变化。

## 测试

- 核心69项；macOS105项；Android app54项+hyperos2项，共56项；插件4项；Foundation5组。
- 新增NotificationSnapshotTests：调度必须读取已提交提醒，不能读取滞后的视图缓存。
- 新增CalendarEditorPreservationTests：保存不写入隐藏默认字段、提醒0不变。
- 新增LegacyReminderSyncTests：旧端缺失提醒不覆盖10/0，显式nil仍可同步；内存存储的实际旧设备快照、保存游标、App重新构造后重复同步。
- 保留跨模块App执行器、批量幂等/回滚、导入、三作用域、提醒多值和所有重复频率查询回归。
- 云端list_calendar_events在10月23/26日均实际返回15条；CF-001无回归。

## 状态边界

- CF-002：[VERIFIED] 按原Google规则恢复19系列，并通过连续重启及MCP复读。
- CF-003：[VERIFIED] 指定真实科研整理系列的MCP→UI→保存→MCP及重启；0分钟也实际显示日程开始时。其他选项组合由自动化覆盖，不冒充全部UI手动覆盖。
- CF-005：[VERIFIED] 原跨模块回归与新旧端快照保护；不是仅重新赋值。
- CF-004：[UNVERIFIED] 历史初始化异常未稳定复现；本轮实际云端读取与重连不代表根除所有连接故障。
- CF-006：系统授权/隔离通知已有回执；真实App队列和原生测试通知的最终验收结果见PROJECT_HANDOVER，不用其他进程的通知回执冒充App日程送达。自然日程的全天持续送达不在短时验收内。
- CF-007：[VERIFIED] 旧快照/并发等待/下一轮同步/本机连续重启路径；[UNVERIFIED] 所有实体在所有多设备并发组合、Android真机、长时间离线与系统休眠恢复。

新增get_status.notification_status是只读诊断字段，旧参数和28工具数量不变；包含授权值、待发送数量、日程提醒数与最近触发时间，不包含凭据。通知设置提供不写业务数据库的独立测试通知，收到系统deliveredNotifications回执才显示成功。

## 修改位置与最终安装状态

CalendarEvent.init(from:)/preservingLegacyReminder、SyncEngine.init/applyPeerSnapshot/UPDATE、LocalDatabase.DatabasePayload、AppState初始化/save/executeAutomationTool、CalendarEditorPreservation.apply及ScheduleItemCreateSheet保存、Android DomainModels/CourseDataManager/ScheduleAddDialog/AndroidAutomation为本轮新增修改点。NotificationService新增diagnosticStatus/sendDiagnosticAlert；NotificationSettingsTab新增测试入口。

build15已安装，19条提醒再次从磁盘验证（customDaysOfWeek/customWeeks按集合比较），无其他业务字段变化。build12真实App的独立测试通知得到系统送达回执。build14/15调度诊断区分生成27、系统add回调接受27，但build15实际仅保留4条日程/共7条待发，不能宣称队列完整。

进一步读取本机usernoted日志，确认17:08及17:15的“Too many requests, dropping”，同一App下出现多个旧source以及旧classflow_daily_briefing请求；系统会在add成功回调后仍丢弃请求。仅按已查询ID清理和removeAllPending+等待清空两种路径均未解决，未把清理实现当作已验证根因修复。多来源与重复构建的ad-hoc签名是否存在因果关系尚未证实；没有签名证书，也未放宽签名要求或修改系统通知数据库。原生“立即更新”按钮现在比较实际待发与计划数量，缺失时明确提示尚未全部确认。等待用户批准短暂关闭/重开本App系统通知进行下一步清理验收。

CF-006仍未全部完成。自然日程后续送达、系统队列完整性和所有多设备冲突组合仍为UNVERIFIED；不能用测试按钮成功掩盖这些边界。build15 App/Widget严格签名、DMG完整性、28工具协议、macOS105项、插件4项及Foundation5组通过，未发布GitHub。

## 2026-10-27 系统通知开关与队列复验

用户明确授权后，通过系统设置仅将ClassFlow允许通知从on切为off，再恢复on。操作前读取已是计划27/接受27/实际日程27、总33，故不能将恢复归因于开关切换，也不能据此认定旧来源丢弃根因已解决。

[VERIFIED] build15原生通知设置点击立即更新后显示系统待发送日程提醒27条；MCP再次读取计划27、接受27、实际日程27/总33，授权2。原生测试按钮显示系统已确认测试通知送达（实际deliveredNotifications回执）。本轮检查最近3分钟日志未出现ClassFlow Too many requests丢弃。

[VERIFIED] 操作前后database.json的SHA256相同；19个日程系列逐UUID提醒仍与恢复计划完全一致。仅更改并恢复系统通知开关、重建队列和发送独立测试通知，无业务数据写入、源码修改或重新构建。

[UNVERIFIED] 这完成了当前队列完整性与即时送达复验，不证明旧来源问题永久消除，也不等同于自然日程跨多日、休眠恢复和所有设备通知的完整验收。本轮未重跑历史105项测试，未发布GitHub。
