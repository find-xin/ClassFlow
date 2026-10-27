# MCP 实测问题修复报告（2026-10-23，build 8 本地）

## 复现证据与结果

| 编号 | 根因与处理 | 状态 |
|---|---|---|
| CF-001 | listCalendarEvents原来只比较基础startDate/startTime；日期参数现在交给CalendarEventProjector，复用频率、结束条件、跨天裁剪和取消例外。省略日期仍返回系列，id仍为系列UUID；指定日期返回实际匹配日期。 | [VERIFIED] 隔离复现先失败，修复后通过 |
| CF-002 | 确认一条提醒丢失路径：AppState.executeCloudSync在网络await前复制nil提醒，期间MCP成功写入10，返回后旧数组覆盖该值。失败测试稳定复现。同步返回前对七类领域集合按UUID和顶层字段重合并网络等待期间的本地修改，保留独立远端字段，并重新记录并发本地操作供后续同步。删除和新实体也保留。 | [VERIFIED] 本地内存StorageProvider触发真实App/MCP交错调用，修复后及下一次同步均保留10 |
| CF-003 | AlertOption没有10分钟选项，其他未列举值也落入none，UI保存可写回nil。增加10分钟和保留自定义提醒选项；null与0保持独立。同时补全biweekly编辑映射，防止重开保存变成不重复。 | [VERIFIED] 映射与持久化回归通过；真实界面交互仍待验收 |
| CF-004 | 在本地服务源码中未找到报告的初始化错误原文。未获得当时会话/客户端日志，无法确定产生层或稳定复现。保留原握手、工具名和协议约束。 | [UNVERIFIED] 不宣称已修复 |

这些复现证明存在的数据覆盖路径，不证明报告中19条提醒一定由同一路径清除。没有批量重置提醒、重建用户日程或修改用户业务数据库；已经丢失的历史提醒不能凭推测自动恢复。

## 修改位置

- MCPToolHandlers.listCalendarEvents：按共享投影筛选日期；新增可空alert_minutes_before输出，原字段及无日期查询保留。
- MCPServer.defineTools：澄清日期参数的重复实例语义，工具数量仍为28。
- ScheduleItemCreateSheet.AlertOption、提醒Picker及保存分支：准确映射/保留提醒值。
- ScheduleItemCreateSheet.EventFrequency：biweekly双向映射。
- AppState.executeCloudSync、requeueLocalSyncEdits：保留网络等待期间已确认写入，重新排队完整合并记录；并发普通同步拒绝重入。
- LocalSyncRebase.merge：按UUID和顶层属性三方重合并；本机同期修改同一属性时本机优先，集合字段为整体属性，不声称嵌套数组逐元素冲突合并。customWeeks/customDaysOfWeek比较归一化为集合顺序。
- MCPReportedRegressionTests：新增报告复现、连续操作、重加载及内存同步回归。

## 自动化验收范围

初始日期查询测试在10月26/27日失败；10分钟映射测试得到nil；同步交错测试在MCP确认写入后重新读到nil。这些测试均在实现修改前实际运行失败。

新增覆盖：

- 10月25—27日daily+untilDate，每日出现、28日不出现。
- none/daily/weekly/biweekly/monthly/workdays/weekends/customDays/customWeeks，三周逐日查询与同一投影器输出一致。
- null/0/5/10/30及自定义7/120/10080分钟；三种作用域保留、UI枚举转换、JSON往返。
- 实际App执行器连续课程CRUD、待办CRUD/完成、其他日程CRUD、批量及重放、导入，每步从磁盘重新读取原日程10分钟提醒。
- 重新构造MCPServer与AppState、普通App保存后提醒不丢。
- 使用内存StorageProvider在第一次网络读取时插入MCP写入，普通同步返回后与下一次同步仍保留提醒；独立远端标题修改、明确清空提醒、本地删除及新增合并。

最终执行与计数记录在PROJECT_HANDOVER.md。原有完整编辑、批量回滚/预检/幂等、无效UUID和字段、边界日期、专注、App桥接、隧道与协议测试一起回归。不能将此称为每个28工具的所有输入组合或全部云端行为都完成实机验收。

## 兼容性与待验收

不改业务持久化Schema，不需要迁移或重新创建日程。按日期查询现在真正执行重复规则，可能返回旧版本漏掉的已有系列；原系列UUID保留。新增输出字段是可空字段。刷新ChatGPT工具说明可加载新描述，原请求参数兼容。

[UNVERIFIED] 安装版真实编辑窗口（null/0/5/10/30）、UI保存后云端重读、真实BYOC跨设备并发、系统通知送达、初始化故障均需独立验收。内存同步与重新构造服务不能冒充真实隧道重连或系统进程重启。已经打开的旧版App不因本地构建而自动升级；尚未发布GitHub。强制云端恢复仍保留其显式覆盖语义，本修复针对普通同步。
