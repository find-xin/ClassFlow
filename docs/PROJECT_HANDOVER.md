# ClassFlow 全局项目交付与交接清单 (Project Handover & Onboarding Manifest)

> **文档定位**：本文件专为新开启的 AI 协同对话、新接入的核心工程师或代码交接设计。阅读本文档后，任何 AI 或开发者均可**零延迟、全景掌控整个项目的工程规范、技术架构、核心业务模型、所有已完成功能、构建测试命令与后续待办事项**。

---

## 1. 项目基本信息与核心哲学 (Project Philosophy)

- **项目名称**：ClassFlow (课流)
- **项目定位**：高品质、开源、原生跨平台的现代化课程表与日程协同应用，涵盖 **macOS 桌面端**、**Android 移动端（含 HyperOS 深度适配）** 以及 **小米手环 / 穿戴设备轻量端**。
- **核心开发原则（Mandatory Tenets）**：
  1. **Native First**：拒绝低性能的通用 WebView 或跨端套壳。macOS 采用纯原生 Swift + SwiftUI + AppKit；Android 采用纯原生 Kotlin + Jetpack Compose + Material 3 + Glance。
  2. **Local First & Offline First**：本地持久化（macOS 为 SQLite/Local JSON，Android 为 Room/JSON Store）作为即时交互的单一真理源（Single Source of Truth），所有网络请求均为异步增量附加。
  3. **Bring Your Own Cloud (BYOC)**：尊重隐私，绝不强制官方 SaaS 中心化账户。支持用户自主配置 WebDAV、阿里云/AWS S3、七牛云 Kodo。
  4. **Clean Domain Separation**：课程（Course & Schedule）与通用日历日程（CalendarEvent）在领域层严格隔离；统一通过 `TimelineEngine` 投影为 `TimelineItem` / `Occurrence` 模型供日视图、周视图、桌面小组件消费。
  5. **Privacy & Security First**：云端 AK/SK 等敏感凭证严禁明文落地，分别采用 macOS Keychain 和 Android KeyStore / EncryptedSharedPreferences。
  6. **No Fake Implementations**：严禁伪造未经验证的系统特性（如使用 `WindowManager` 悬浮窗侵入式伪造小米超级小岛）。所有平台能力严格标记 `[VERIFIED]`、`[UNVERIFIED]`、`[BLOCKED]`。

---

## 2. 代码库拓扑与目录索引 (Codebase Architecture)

```
Schedule/
├── apps/
│   ├── macos/                     # macOS 原生应用 (Swift Package / SwiftUI / AppKit)
│   │   ├── Sources/
│   │   │   ├── App/              # ClassFlowApp 入口、AppState 全局响应式状态机
│   │   │   ├── Storage/          # LocalDatabase (JSON/SQLite)、AppSettings 用户配置模型
│   │   │   ├── UI/
│   │   │   │   ├── MainWindow/   # 课表主画布 (TimetableGridView)、日程瀑布 (TodayAgendaTimelineView)
│   │   │   │   │                 # 专注学习 (StudyFocusView)、热力图分析 (PomodoroStatisticsView)
│   │   │   │   ├── Settings/     # 卡片式偏好设置 (SettingsView)、课表外观与皮肤 (TimetableAppearanceSheet)
│   │   │   │   └── Components/   # 动态壁纸背景 (WallpaperBackgroundView)、周数选择矩阵等
│   │   │   └── Services/         # EventKitCalendarService、NotificationService、KeychainService
│   │   └── Tests/                # Swift Testing 自动化单元测试 (MacOSViewModelTests.swift)
│   ├── android/                   # Android 原生应用 (Gradle / Jetpack Compose / Glance)
│   │   ├── app/src/main/kotlin/org/classflow/
│   │   │   ├── ui/               # MainActivity, TimetableScreen, AgendaScreen, StudyScreen
│   │   │   ├── data/local/       # CourseDataManager (持久化), SettingsManager, WidgetDataManager
│   │   │   └── widgets/          # TimetableGlanceWidget, GlanceReceiver (桌面小组件系统)
│   │   └── hyperos-adapter/      # 小米 HyperOS 专用能力隔离适配模块
│   └── band-xiaomi/              # 小米手环 8/9 / Vela OS 轻量级表盘与快应用 (RPK)
├── packages/
│   ├── core-engine/              # Swift 核心计算引擎 (节次时间轴投影、冲突消歧、ICS 互导)
│   ├── schemas/                  # 全局领域协议 JSON Schema (course, schedule, semester, sync)
│   └── fixtures/                 # 跨平台一致性测试标准化测试用例集
├── scripts/                      # 构建工具集 (package_macos.sh 打包 DMG, 生成高清图标/矢量资源)
├── dist/                         # 最终制品分发目录 (.dmg, .apk, .rpk, .zip, SHA256SUMS.txt)
└── docs/                         # 架构蓝图与技术设计文档 (ARCHITECTURE, DATA_MODEL, SYNC_PROTOCOL 等)
```

---

## 3. 核心领域数据模型与统一接口 (Core Domain Models)

### (1) 领域隔离与统一投影
- **Course & CourseSchedule**：描述学期课程、教师、教室、周次模式（`WeekPattern`: 全部/单周/双周/自定义周矩阵）。
- **CalendarEvent**：描述带时间区间的日程或学习任务。
- **TimelineItem (统一投影)**：
  - 由 `TimelineEngine.project(...)` 统一生成。
  - 具备 `sourceType` (`.course` vs `.calendarEvent`)、`title`、`location`、`startSection`/`endSection`、`startTime`/`endTime`、`color`、`hasConflict`、`isCurrentWeek` 等字段。
  - 周视图、日视图、小组件均仅依赖 `TimelineItem`，彻底解耦数据源。

### (2) 数据持久化与单例统一
- **macOS**：`AppState` 托管 `LocalDatabase.DatabasePayload`，自动同步存储至 `Application Support/ClassFlow/database.json`。
- **Android**：由 `CourseDataManager` 统一管理 `courses.json`、`semesters.json`、`focus_records.json` 和 `daily_minutes.json`。所有课程与待办增删改查必须经由 `CourseDataManager`，自动触发 `WidgetDataManager.updateWidgetTimelineData(...)` 广播刷新 Glance 桌面小组件。

---

## 4. 关键已完成功能与最新设计细节 (Feature Completion State)

### (1) 课程视图与排版引擎 (Timetable Canvas)
- **无感融合壁纸**：底部导航栏、顶部表头、空白课表格子均为半透明或完全透明，壁纸全窗口无缝透出。
- **差异化重叠消歧策略（平台特性对齐）**：
  - **macOS 端**：保留类似 Google Calendar 的贪心列泳道分流（Greedy Column Allocation），同一节次若出现多门重叠课程，并排自适应排列，避免互相遮挡。
  - **Android 端**：采用**重叠聚类卡片堆叠 + 用户自主置顶**方案。当某节次出现多门重叠课程时，自动显示气泡角标（如 `2门重叠 ▾`）与物理层叠效果，点击弹出 `OverlapCoursePickerDialog`，允许用户手动选择置顶展示的课程卡片或查看详情，选择状态持久化存储于 `CourseDataManager`。
- **周视图 / 日视图无缝切换**：日视图拥有大字号、独立日程状态流；周视图支持动态隐藏周六/周日列。
- **课表外观与皮肤深度定制**：支持 6 款极光/星空/天空渐变预设、纯色模式、自定义本地图片壁纸；课程卡片文字对比度动态自适应（浅色卡片深色字、深色卡片浅色字）。
- **动态当前周计算**：基于当前学期 `startDate` 执行 ISO 规范周一基准对齐与动态教学校准计算，彻底消除静态硬编码。

### (2) 全量备份与跨平台互导服务 (Backup & Import/Export)
- **.classflow PKZip 容器标准**：macOS 与 Android 端均严格遵循标准 ZIP 容器归档与 `manifest.json` SHA-256 完整性哈希校验，完整打包 `semesters.json`、`courses.json`、`exceptions.json` 和 `settings.json`。
- **Android SAF (Storage Access Framework)**：接入系统原生文件选择器 (`ActivityResultContracts.OpenDocument` / `CreateDocument`)，杜绝假实现与 Mock Toast。
- **RFC 5545 iCalendar 标准互通**：支持生成与解析合规的 `.ics` 课表与日程文件，无缝衔接 Apple 日历、Google Calendar 与 Outlook。

### (3) 去中心化 BYOC 云同步协议 (Decentralized BYOC Protocol)
- **设备隔离日志与快照双向互通**：严格按照 `docs/SYNC_PROTOCOL.md` 规范，云端按 `devices/{deviceId}/` 隔离写入 append-only 操作日志及 `snapshots/snapshot.json`。
- **快照引导与字段兼容层**：`DeviceSnapshot` 模型提供 `calendarEvents` 与 `schedules` 别名双向兼容；macOS `SyncEngine` 遇到新 Peer 时优先读取 Snapshot 实现毫秒级快速引导，随后重放增量 Revisions。
- **Android 全量 Revisions 与 Exceptions 上报**：Android `FullSyncManager` 在 S3 与 WebDAV 同步时均写入 `revisions/{seq}_{uuid}.json` 操作日志，并完整持久化与同步 `exceptions`（调课/停课单次豁免）。
- **AWS SigV4 认证**：Android 端完整实现 `AWS4-HMAC-SHA256` 算法（`S3StorageProvider`），与 macOS `S3SignatureV4` 保持跨端互通。
- **macOS 缓存失效与单调递增版本**：`AppState` 采用全局单调递增 `dataVersion` 纪元计数器，并在所有数据突变处自动打点记录增量操作至 `SyncEngine`。

### (4) 双端高并发与渲染性能优化 (Performance Optimizations)
- **CoreEngine 日期格式化与算法降阶**：
  - `WeekCalculator`：采用 `NSLock` 保护的多时区 `DateFormatter` 实例池，彻底消除高频重复分配 `DateFormatter` 的 CPU 损耗。
  - `TimelineEngine`：在生成周投影时单次预计算该周 7 天的日期字符串，并复用于调课/补课排查，避免对每门课程每节次重复计算基准星期一。
  - `ConflictDetector`：先按 `date` 分组再检测节次重叠，将 $O(N^2)$ 的跨日期无效全量比较优化为各日期局部的低复杂度碰撞。
- **Android 渲染与重组抖动消除**：
  - `TimetableScreen`：使用 `remember(occurrences)` 缓存 `coursesByDay` 分组字典，杜绝每帧重新分配内存导致的卡片重组震荡。
  - `GoogleCalendarColorPicker`：引入 `ConcurrentHashMap` 颜色十六进制解析缓存，消除界面重绘时的重复色值字符串解析与异常处理。
  - **主线程 I/O 解耦**：`MainActivity` 中小组件数据保存 (`WidgetDataManager.saveAndUpdate`) 与专注历史落盘全面调度至 `Dispatchers.IO`。

### (5) 学习专注与番茄 ToDo (Study & Pomodoro Module)
- **双模计时**：支持经典番茄钟（25m/5m/15m）与自由正向心流计时。
- **24 周全学期 GitHub 风格热力图谱**：
  - macOS 端修复了 `@State private var cachedAnalytics` 缓存机制，实现 120Hz 丝滑悬浮预览，无掉帧。
  - Android 端引入 `StudyDateUtils`（`ThreadLocal` 缓存 `SimpleDateFormat`），消除循环 GC 抖动。
- **历史记录清理**：macOS 和 Android 端均已在“专注卡片”及“设置中心”提供【清空历史】功能。

### (6) 跨平台桌面与通知中心小组件 (Widgets)
- **macOS**：原生 WidgetKit Extension (`ClassFlowWidgets.appex`)，支持 Small、Medium、Large 尺寸，利用统一存储文件实现瞬时免重启刷新。
- **Android**：基于 `androidx.glance:glance-appwidget`，支持【日视图】与【周视图】。修复了深色模式跟随失效问题，移除了生硬的白色色块与非本周暗沉阴影。

---

## 5. 编译、验证与打包指令 (Build, Test & Packaging Guide)

### (1) macOS 端 (Swift 6.0 / macOS 14.0+)
- **运行单元测试**：
  ```bash
  cd packages/core-engine && swift test # 54 项测试全过
  cd ../../apps/macos && swift test    # 24 项测试全过
  ```
- **一键构建 Production Release 并生成 DMG 安装包**：
  ```bash
  ./scripts/package_macos.sh
  # 输出: dist/ClassFlow-1.0.0-macOS-arm64.dmg
  ```

### (2) Android 端 (JDK 17 / API 34+)
- **运行单元测试**：
  ```bash
  cd apps/android
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest # 全项单元测试通过
  ```
- **构建 Release / Debug APK 并同步至 dist**：
  ```bash
  cd apps/android
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease
  cp app/build/outputs/apk/release/app-release.apk ../../dist/ClassFlow-1.0.0-android.apk
  ```

### (3) 校验和同步
```bash
cd dist
ls -1 ClassFlow-*.{apk,dmg,zip,rpk} | xargs shasum -a 256 > SHA256SUMS.txt
```

---

## 6. 当前状态标签与后续演进建议 (Status & Roadmap)

| 模块 / 特性 | 状态标签 | 现状与演进指引 |
| :--- | :--- | :--- |
| **macOS 主程序与课表引擎** | `[VERIFIED]` | 运行平稳，24 项单元测试通过，DMG 已发布至 `dist/` |
| **macOS WidgetKit Extension** | `[VERIFIED]` | 已嵌入 `.app/Contents/PlugIns`，支持日程全量投影与过期物理时钟过滤 |
| **日程/待办重复机制与生效周次解耦** | `[VERIFIED]` | 双端中英文频次归一化互通，周频次与周次矩阵彻底解耦，按周次重复精准生效 |
| **统一事项管理（实体管理）** | `[VERIFIED]` | 课程、日程、待办三合一检索、查看、编辑与删除，消除了旧版仅可删不可改问题 |
| **Android 课表与本地数据同步** | `[VERIFIED]` | `CourseDataManager` 与 Glance 小组件双向联动无延迟，日程事件全量投影就绪 |
| **跨端 .classflow 容器与 ICS** | `[VERIFIED]` | 跨端 SHA-256 完整性校验与 RFC 5545 互导双向通过自动化测试 |
| **BYOC 全模块多端云同步 (WebDAV/S3)** | `[VERIFIED]` | 覆盖课表、待办番茄、专注统计、作息模板全模块；快照无条件归档与增量同步均验证通过 |
| **小米 HyperOS 超级小岛** | `[BLOCKED]` | 依赖小米开放平台场景资质白名单，严禁使用悬浮窗假冒 |

---

## 7. 新对话 AI 接盘指令模板 (Copy & Paste Prompt)

打开全新 AI 对话时，可直接复制以下文字发送给新 AI：

```markdown
你好，这是开源跨平台课表与日程应用【ClassFlow (课流)】的代码库。
请你首先阅读项目根目录下的 `AGENTS.md` 和 `docs/PROJECT_HANDOVER.md`。

本项目技术栈：
- macOS 端：纯原生 Swift + SwiftUI + AppKit + WidgetKit Extension
- Android 端：纯原生 Kotlin + Jetpack Compose + Glance AppWidget + Room/JSON Store
- 核心算法：packages/core-engine/ 中的统一时间轴投影引擎 TimelineEngine
- 最新发行产物：位于 dist/ 下的 DMG、APK 与 SHA256SUMS.txt

请严格遵守工程规范：
1. Native First、Local First、BYOC，绝不引入无关第三方 SaaS 依赖或粗糙 WebView。
2. 任何代码修改必须执行 10 步工程法则，确保 macOS (swift test) 和 Android (testDebugUnitTest) 单元测试 100% 通过。
3. 请在理解上述交接文档后，告诉我你已就绪，并简要列出你当前理解的项目架构重点。
```

### ChatGPT 接入修复（2026-10-21）

本地 Work/Codex 与普通 ChatGPT 云端聊天使用不同连接路径，共用 23 项 Swift MCP 工具。
新增 App 端命令确认执行器、日程/待办修改、可持久化的真实专注计时与暂停/继续；
修复课表配置 ID 被任务覆盖、同步操作遗漏、安装失败被隐藏及凭据明文存储。
接入步骤和独立验收要求见 [CHATGPT_INTEGRATION.md](CHATGPT_INTEGRATION.md)。
云端验收需用户的隧道授权及运行凭据，未实际完成目标聊天调用前不得记为已验证。

### App 内隧道设置（2026-10-21）

设置 → AI / ChatGPT 新增 ChatGPT 聊天接管：Tunnel ID、Keychain 运行密钥、客户端路径、
手动启停、就绪状态和默认关闭的随 App 启动开关。TunnelManager 管理官方客户端，
退出时清理自有进程；外部终端连接需用户 Ctrl+C 停止一次。单独私有 profile，不改
旧终端配置，不导入其进程凭据。新增 12 项生命周期与安全测试；App 内保存密钥后的
真实云端调用仍需单独验收。

### AI 设置页精简（2026-10-21）

按用户要求移除宣传信息、客户端配置复制/写入入口、工具清单、指令示例及 OpenAPI 导出入口；
页面仅保留原生隧道连接设置。删除对应未使用的 UI 辅助方法，不修改 MCP 工具、已安装插件、
隧道生命周期或已有连接配置。高级接入步骤继续保留在 CHATGPT_INTEGRATION.md。

`[VERIFIED]` 精简版 Release 已安装并通过签名校验，56 项 macOS 测试及安装版 23 项 MCP 协议检查通过。
修正连接诊断测试被周期性就绪消息覆盖的断言时序，仅调整测试隔离条件，未修改运行时逻辑。
用户完成系统钥匙串授权后，已实际核对安装版页面仅保留连接设置，高级选项默认收起；自动启用保持开启，App 托管隧道自动恢复就绪。

### ChatGPT Release 附件（2026-10-21）

新增明确文件清单的可复现插件打包脚本、独立用户接入说明及安装包测试；Release 工作流自动附带插件 ZIP、说明和 SHA-256，重跑保留已有 Release。修正文档中 URL/Actions 与本地插件适用入口的混淆。发布版 macOS ZIP 的 SHA-256 与在线附件一致，并实际通过 23 项 MCP 工具协议检查。

### 纯净版 Release 与线上发布（2026-10-21）

`[VERIFIED]` 彻底清空所有平台首启预置课程、日程、待办、专注历史及虚构图谱，默认仅保留标准 12 节作息网格骨架；彻底置空 S3/WebDAV 默认云端地址与区域代号（转为浅灰 Placeholder 提示），确保用户下载获得绝对纯净的应用。
`[VERIFIED]` 源码及 Release v1.0.0 标签已成功推送到远程仓库，通过 GitHub Actions 自动化工作流已在线发布最新的干净版本 DMG、ZIP、APK、RPK、本地插件包及 SHA256SUMS.txt 校验清单。全部 15 项 Release 线上附件哈希经 API 实测核验一致。

### Batch calendar import (2026-10-21)

Added import_calendar_events, tool 24: 1-500 rows, full validation, atomic save, occurrence deduplication, dry-run and persistent replay receipts. Old databases decode without the optional local receipt field. AppState preserves receipts; BYOC and backup do not include them. Refresh ChatGPT tool discovery after installing the updated App. Platform tool quotas cannot be increased by ClassFlow.

[VERIFIED] 66 macOS tests and 24-tool installed-release protocol smoke checks passed; three portable plugin packaging checks passed. Updated Release installed with a pre-update App/database backup. After user completed native Keychain authorization, a real App-executed batch preview confirmed the expected duplicate/new counts, then a single commit inserted 223 events. A separate read verified 258 unique occurrences matching the prepared source manifest and all 35 prior records unchanged. Google Calendar was read only; no Google writes were issued. Cloud-chat discovery of tool 24 still needs a connection refresh. Migration source and prepared request are private local application-support files, excluded from the repository.

## 2026-10-22: Complete editing contract (MCP v1.2, 28 tools)

[VERIFIED] Added get_editing_context (paginated, credential-free), manage_course_schedule, batch_edit_items (1–100 sequential edits in a private staging database, atomic rollback, dry-run and stable batch replay), and reset_focus_timer. Existing tools retain their names and now expose complete course/schedule, CalendarEvent and PomodoroTask editing schemas with unknown-field/type validation. Course metadata, multiple slots, slot teacher/room, precise time or sections, arbitrary week selections, scoped reschedule/cancel/delete/restore, calendar recurrence/reminder/end conditions/scoped detachment and future splitting, task linkage/start/deadline/precise minutes/repeat rules, and three real focus modes are covered. The original 500-row occurrence import remains available.

[VERIFIED] Shared CalendarEventProjector handles count termination, custom days/weeks, monthly/biweekly recurrence, multi-day clipping and optional section binding. Native Mac views, MCP reads, widget generation and reminder planning share it. Schedule mutation fixes retain custom times on future splits and place moved lessons on the correct date. Completing a repeating task generates a stable-ID successor without duplicating focus statistics. Focus sessions may retain optional course name/ID and classroom; count-up/countdown do not invent completed pomodoros.

[VERIFIED] Android codecs preserve timeMode, alertMinutesBefore, intervalWeeks and task courseId; full snapshots use complete codecs instead of dropping recurrence/deadline fields. Android projection matches count/day clipping/section binding and projects rescheduled arrivals across weeks. This does not provide a remote Android process controller: the MCP executor and active focus session remain on the Mac.

[VERIFIED] Source validation: 75 macOS tests, 62 core tests, 46 Android unit tests, 3 portable plugin-kit tests, and the real debug executable's 28-tool protocol smoke check passed. Tests use private temporary data. The local plugin kit is version 1.2.0. No GitHub Release publication is implied by generating the local kit.

[UNVERIFIED] Actual system notification delivery requires notification authorization; Android physical-device BYOC/notification delivery and refreshed ChatGPT cloud discovery require separate runtime checks. Reminders use UNUserNotificationCenter and a bounded rolling queue, not a simulated success. Single-occurrence course exceptions support target date, sections and classroom; precise-time changes use slot edits or future rule splits, matching the native sheet. Rich text, four-quadrant task priority, arbitrary settings/backup/system permission control must not be claimed: the native models have text notes and three priority values. See CHATGPT_INTEGRATION.md for the exact contract and refresh steps.

[VERIFIED] Installation/runtime follow-up: release build completed, staged and installed App passed strict deep signature verification and the real 28-tool stdio protocol smoke check. Native installed App reopened to the existing timetable. Its actual App executor returned paginated editing context with 258 events and completed batch_edit_items dry-run successfully; database bytes before/after those runtime checks were identical, and a final per-UUID comparison confirmed the pre-install inventory and every business field were unchanged (4 courses, 258 events, 0 tasks, 5 exceptions). App startup reordered the course array, so the final preservation check compares entities by UUID rather than relying on array-sensitive hashes. Prior App and database were preserved in a private local backup directory. Installed MCP reports version 1.2.0. Existing cloud connection still needs user-performed tool refresh; no browser was controlled and this change was not published to GitHub Releases.

## 2026-10-22: Cloud discovery refreshed

[VERIFIED] At the user's request, refreshed the existing Classflow custom MCP connection from ChatGPT's plugin settings. The cloud plugin's own tool inspector now shows Write 21 + Read 7 = 28 tools, including get_editing_context, manage_course_schedule, batch_edit_items and reset_focus_timer, and the expanded calendar editing descriptions. This supersedes the previous pending cloud-metadata-refresh note. No course/calendar/task data or plugin permission mode was changed by the refresh.

## 2026-10-22: Timetable long titles use available card height

[VERIFIED] CourseCardView no longer caps titles at two lines on cards at least 55 points tall. SwiftUI wraps within the available card height; compact cards retain one line. The existing long research-event titles now display completely across three lines in the installed Mac App, verified against the same native timetable before/after. The Release build, deep strict signature verification and installed 28-tool protocol smoke check passed. Per-UUID business records, including all 258 calendar events, were unchanged. Previous App and database were backed up locally. This is a presentation-only change; no new domain tests were added.

## 2026-10-22: v1.2.0 release preparation

Core/App and local plugin versions are 1.2.0; Android versionCode and macOS bundle build number are 2. The macOS packager derives filenames and bundle versions from ClassFlowVersion, preserves symlinks in ZIPs, targets macOS 15.8, and supports CLASSFLOW_SKIP_INSTALL=1 for packaging without replacing a running installation. The release workflow reads the unchanged band's own 1.0.0 version instead of relabeling old band artifacts as a new build. Android release APK was rebuilt and its signing certificate matches the previous release. See RELEASE_NOTES_v1.2.0.md for verified behavior and runtime limitations.

[VERIFIED] v1.2.0 release artifacts rebuilt: macOS App/Widget bundles report 1.2.0 (2), ZIP preserves the MCP symlink, both the bundle and ZIP-extracted executable pass strict signing and the 28-tool protocol check, and the DMG verifies successfully. Re-ran 62 core and 75 macOS tests, 46 Android unit tests with assembleRelease, 5 foundation suites and 3 portable plugin-package tests. README download hashes and the release checksum manifest are generated from these artifacts. Publishing uses the repository's existing GitHub Actions release workflow.


### 隧道代理与真实连通性修复（2026-10-22）

[VERIFIED] 77 项 macOS 测试通过。GUI 启动的隧道继承 macOS 静态 HTTPS / SOCKS 代理（显式代理环境变量优先），通过 env 引用传给 control-plane.http-proxy，不在命令行或配置文件中保存代理凭据。就绪探针改为同时检查本地 ready 与控制平面 status=ok，覆盖本地 ready=true、云端连续 timeout 的假就绪场景。PAC 解析未实现。

[VERIFIED] 修复安装后，实际云端 ClassFlow get_status 调用成功；control-plane、proxy、MCP 均为ok，轮询失败计数为0。本地 stdio 协议28项工具通过检查。


### MCP 批量扩容与事务性能（2026-10-22，v1.2.0 build 4）

[VERIFIED] 84项macOS测试、62项核心测试通过；新增独立临时数据库测试覆盖5000项删除、1000项混合编辑、2000条导入、选择器隔离、预检不写、整批回滚、提交后新实体不被旧批次重放误删、课程/时段/异常墓碑和App同步日志。开发环境并行测试实测：5000项删除约0.33秒，1000项混合编辑约0.53秒，App执行258项删除含日志与UI刷新约0.54秒；这些不是云端端到端耗时保证。

batch_edit_items保留原名及旧回执兼容，operations扩到1000；新增selection+action+changes一次处理最多5000个实体/完整系列，避免LLM列全量UUID再逐条调用。日程导入2000条、查询1000/页及过滤。无新工具名称。LocalDatabase仅为自动化增加显式内存工作区，最终业务数据与同步元数据一次原子持久化，本地数据库仍是唯一真理源。App变更对比用UUID索引，去除重复微件刷新，待办/专注不重排无关课表与日程通知。selection的日期范围是实体基础开始日期（待办截止/开始日期），不是循环实例；实例级作用域仍用operations。系统代理和云端配额边界见集成说明。

[VERIFIED] 发布前安装版为1.2.0 (4)，28项工具协议、App/Widget签名、ZIP解压与符号链接、DMG完整性检查通过。实际安装版选择器预检一次匹配170条日程，本地App通道约0.07秒，数据库字节保持一致；更新前后课程、日程、任务、异常与专注记录按UUID完全一致。隧道控制平面与代理均为ok；无真实删除测试。

### 日程投影性能与职责整理（2026-10-22，未发布源码变更）

[VERIFIED] 拆分 CalendarEventRecurrence（重复规则与日期枚举）和 CalendarEventProjector（周范围裁剪与 TimelineItem 构造），新增批量 project(events:semester:week:exceptions:)；保留原单项接口。每批复用日期格式器、周边界及异常索引，重复异常仍取首项。跨天实例只遍历本周可见部分；重复次数仍从系列原点计算，不承诺历史重复枚举为常数时间。

[VERIFIED] macOS 课表、微件与深链切换采用批量投影；MCP 课表查询/微件构建复用同一 payload，移除逐日程完整数据库读取。日期缓存与事项缓存使用独立周次/学期标记，防止切周缓存误命中；缓存命中前不再筛选课程，微件复用日期格式器并一次按星期分组。持久化格式、MCP Schema 与 Android 代码未修改。

[VERIFIED] 本轮实际运行：核心67项、macOS86项、debug可执行文件28工具协议检查、Foundation 5组及插件打包3项通过。新增测试覆盖批量顺序/UUID/异常隔离、首条异常优先、次数结束后的跨周尾段、夏令时/午夜结束、并发隔离、长跨天裁剪、500项查询数据库字节不变和切周缓存。

[VERIFIED] 同机Debug隔离单次样本：500项日程的queryTimetable从2.560秒降到0.105秒；20条2010年至2026年长跨天日程的周投影从0.454秒降到0.009秒（输出均为60个可见日片段）。基线为本轮修改前95361c6源码加同一测试数据；新投影调用改用批量入口。耗时不含编译，无时间阈值断言，不代表统计基准、Release性能或云端端到端保证。可用以下命令复测：

```bash
swift test --package-path apps/macos --filter largeTimetableQuery
swift test --package-path packages/core-engine --filter longSpan
```

[UNVERIFIED] 本轮未重新执行Android测试、GUI交互验收、云端调用、真机同步或通知送达；未打包、安装、发布或触碰用户真实业务数据库。

### 空重复筛选兼容与插件更新修复（2026-10-22，v1.2.0 build 5，本地未发布）

[VERIFIED] 用户报告事项管理保留日程、课表却不显示。只读备份检查发现19条日程的customWeeks均为[]，普通daily日程的customDaysOfWeek也为[]；Android将空集合解释为无额外限制，Swift此前却将其当作无匹配日期。对同一私有数据库副本独立运行HEAD旧投影器与修复后投影器，第8周旧结果0条、修复后60个实例（10月22日至25日每天15条）。该判断在性能优化前的HEAD已存在；上一轮性能测试未覆盖此跨端空数组形态，不能用测试通过代替实际数据兼容验收。

[VERIFIED] CalendarEventRecurrence读取兼容null/[]，非空星期和周次筛选仍生效；MCP写入对空customDays/customWeeks的校验保持不变。未迁移或重写已有日程。新增8种频次的参数化测试、非空筛选约束测试，以及与报告相同的学期起点/第8周/空数组原生AppState测试。顺带移除事项管理日期范围无条件标注“已截止”的错误文字。

[VERIFIED] install_mcp.sh支持CLASSFLOW_CODEX_BIN，PATH未找到时自动检查用户和系统Applications中的ChatGPT内置CLI。脚本在真实PATH=/usr/bin:/bin条件下注册成功；CLI返回ClassFlow插件1.2.0安装目录，缓存的manifest、技能和启动器与源码一致。安装测试覆盖显式路径、带空格的内置CLI路径、无效覆盖时不写客户端配置。不是通过直接改插件缓存或降低审批实现更新。

[VERIFIED] 核心69项、macOS87项、Foundation 5组、插件打包4项通过。build 5 Release已构建并安装；App/Widget严格签名、DMG完整性、ZIP解包版本/符号链接/签名、打包及解包可执行文件28工具协议通过。插件套件与本地SHA256SUMS已重新生成；未推送GitHub或替换线上Release。旧App和数据库已备份至用户私有Application Support目录，未进入仓库。

[VERIFIED] 新安装版首次GUI启动曾等待macOS钥匙串授权，进程采样确认AppState.init → KeychainService.readSecret → SecItemCopyMatching。用户在系统窗口亲自授权后，安装版真实第8周课表已显示早餐、科研、午休、技术学习等日程，截图与辅助功能树均核对通过。本地ClassFlow插件与ChatGPT云端Classflow插件分别实际调用query_timetable(week:8,day_of_week:4)，均返回16项（15条日程实例、1条课程）；云端get_status确认19条日程实体与第8周状态。未修改连接权限或凭据，未执行云端写操作。

[VERIFIED] 更新前后按UUID及全部业务字段比较，学期1、课程4、日程19、异常5、任务0、专注记录0均保持；其中一条日程的Set<Int>星期数组在重编码后顺序变化，按集合语义归一化后内容完全一致，不是用户规则被修改。更新后的本地插件已注册至1.2.0且原有只读调用已成功；新会话可加载新版技能文件。

[UNVERIFIED] 云端写操作、Android真机同步及通知送达仍不属于本轮验收；本轮产物仅在本机更新，未替换GitHub线上发布。

### v1.2.0 build 5 发布准备（2026-10-22）

按用户要求更新现有v1.2.0 Release，沿用原发布地址；修复源码、macOS build 5、README下载哈希及发布说明一并提交，v1.2.0标签随修复提交更新。发布工作流重新生成便携插件及校验清单，并覆盖现有同名附件；Android和手环附件保持原内容。线上完成状态需核对本次GitHub Actions结果及15个附件的实际摘要，不能以本地打包成功代替发布成功。

### Android 平板布局与独立 ChatGPT 执行（2026-10-23，本地预览）

用户明确要求11英寸Android平板借鉴Mac布局、保留Android业务，并选择“Mac关机也能独立管理平板课表”。Compose按可用窗口宽度840dp及系统字体缩放切换顶部导航/周次操作；窄分屏保留手机布局，日期栏与网格共享轴宽及边距，编辑/设置窗口限制宽度。旋转后保存页面、学期、周次及周/日模式，Activity重建不再重放启动导航。

Android新增四项独立MCP工具（非Mac28工具移植声明）：get_status、get_editing_context、query_timetable、batch_edit_items。批量支持课程/日程完整系列create/update/delete，最多1000操作；严格Schema、离线快照校验、一次SharedPreferences提交业务/墓碑/回执、dry_run与同ID同内容重放。常规持久化不再按名称/时间隐式合并UUID；云同步及强制拉取期间暂拒自动化，主界面监听回执刷新。当前不含单次/未来实例作用域、待办写入、专注控制或后台微件/提醒送达验收。

原生AI设置保存独立Tunnel ID及Android安全存储的运行密钥，Kotlin服务实现官方公开poll/response协议，固定HTTPS出站，无公网监听、原始响应日志或Mac凭据复制。用户手动启动specialUse前台服务，通知可停止；不自动开机启动。系统自动备份禁用，显式.classflow备份仍保留。Google Play前台服务用途审核尚未进行。连接配置和验证说明见ANDROID_CHATGPT.md。

[VERIFIED] 实际执行JAVA_HOME=/opt/homebrew/opt/openjdk@17 apps/android/gradlew -p apps/android testDebugUnitTest :app:assembleRelease成功；app54项、hyperos-adapter2项共56项测试，无失败。新增批量预检/回滚/重放/非法日期字段/UUID/课表投影/同步门禁测试与窗口宽度/字体策略测试。APK签名校验成功，打包内含4工具Schema；本地版本1.2.0 (versionCode3)。预览产物dist/ClassFlow-Android-1.2.0-build3-preview.apk，SHA256=6b4692bf730115ed32633bf3e6a9c82632a24ade3b649b6fe413d0cbae10d0ab。本轮未修改线上Release或原发布附件。

[UNVERIFIED] adb无已连接设备；未做联想11英寸真机布局/旋转验收、前台服务生存与安全存储运行检查，也未配置平板专用隧道或实际从ChatGPT调用Android。网络传输实现通过编译但尚无真实控制平面验收。不能以已有Mac云端连接成功或上述JVM测试代替。下一步安装预览APK、配置独立隧道，先执行只读get_status/query_timetable，再在隔离数据中验收写操作；不得用用户真实数据做破坏性测试。

### Mac 日程页实际时间排序修复（2026-10-23，build 6 本地准备）

用户截图显示23:30睡觉出现在上午事项之间，并显示第1–14节。原因是日程页与推荐卡片按startSection排序；时钟日程投影到网格的节次仅为绘图坐标，超出作息范围时的回退不能当作实际时间或业务节次。另有推荐使用开始时间减20分钟、全部结束后回退首项的问题。

新增AgendaTimelinePolicy：按具体日期、全天标记、起止时间与稳定ID排序；推荐仅考虑今天、本周有效、未取消、未结束的非全天实例，用结束时间识别仍在进行的长事项；无匹配返回空。视图每30秒刷新推荐，普通时钟日程不显示网格节次，课程/明确sections日程仍显示节次；日程卡片使用“开始时间”而非“开课时间”。不修改业务数据或持久化Schema。

[VERIFIED] 本轮macOS90项测试通过，新增晚间日程排序、长事项进行中、已全部结束、其他日期/取消/非本周排除和节次标签回归。build6安装包构建中，最终产物验证及GUI安装状态仍需分别记录，不能以测试通过冒充已安装或发布。

[VERIFIED] build6本地DMG/ZIP已生成，App/Widget严格签名校验、DMG完整性与产物28工具协议检查通过；核心69项和macOS90项测试通过。dist校验清单已与新产物同步。未覆盖/重启已安装App，尚未GUI验收，未发布GitHub；用户真实业务数据库未作任何修改。

### 通知错误课程与过期摘要修复（2026-10-23，build7本地准备）

用户报告非本周课程触发提醒，以及已不存在的大学物理反复出现在摘要。源码确认：课前提醒投影默认包含非本周占位，服务只排除取消项；早间摘要把某一天的课程正文设为repeats=true，后续可能每天复用旧正文，并且关闭摘要/无学期时不清理旧请求。不能据截图认定当前数据库仍包含大学物理。

生成提醒显式showNonCurrentWeekCourses=false，服务再次校验course/isCurrentWeek/noncancelled，按日期时间排序。按真实当前日期生成跨周7天窗口，摘要每个具体日期独立一次性调度、不重复；每轮完整替换待发送队列，无学期/关闭开关也清理旧请求。课前27+日程27+摘要7共最多61条，保留系统余量。AppState串行化重建，避免await交错让旧任务重新写入过期请求。主窗口启动时清理本App历史已送达通知，运行期间每小时刷新滚动窗口；退出后仅已提交队列可送达。

[VERIFIED] macOS92项测试通过，新增非本周/取消排除、逐日期摘要内容隔离、上午时间已过不调度今日、空日期不复用昨日课程。未修改用户实际课程数据。build7本地安装包准备中，系统授权、实际送达及通知中心清理效果尚未验收；不能以单元测试替代。

[VERIFIED] build7本地DMG/ZIP已完成，App/Widget严格签名、DMG完整性、28工具协议校验通过；最终源码复跑macOS92项测试通过。dist校验清单已更新。本轮未替换安装版或修改用户业务数据、未更新GitHub Release；系统通知清理与真实送达仍为[UNVERIFIED]，需启动新版执行迁移清理。

### MCP实测报告修复（2026-10-23，build8本地）

[VERIFIED] 按任务书先运行失败测试：每日重复26/27日查询缺失、10分钟提醒映射nil；另复现普通云同步网络等待期间MCP已确认提醒写入随后被旧快照清空。修复日期查询复用投影、提醒10分钟/自定义值保留、双周映射、同步按UUID/顶层字段重合并本地并发修改并重新入队。未改业务Schema或重置用户数据。详细报告见MCP_REPAIR_REPORT_2026-10-23.md。

[VERIFIED] 最终核心69项、macOS101项、插件打包4项、Foundation5组通过；debug、dist及ZIP解包可执行文件28工具协议检查通过。build8 App/Widget及ZIP解包签名、DMG完整性通过，校验清单已更新；未安装到Applications或发布GitHub。回归包含App执行器跨领域操作每步磁盘读取、重新构造App/MCP、提醒多值/三作用域和内存云存储交错同步及后续同步。

[UNVERIFIED] CF-004初始化错误未稳定复现；真实隧道重连、真实BYOC多设备冲突、编辑窗口实际交互和通知送达需分别验收。确认的丢失路径不证明此前19条提醒一定由同一路径清空，也不自动恢复历史值。本轮不修改用户业务数据库。

### 历史提醒恢复及真实重启验收（2026-10-23，build12已安装）

[VERIFIED] 用户明确授权按原Google提醒恢复。只读Google原系列后按标题/时间/拆分系列对应，19个原UUID一次批量恢复：0分钟2个、10分钟13个、无提醒4个；短暂休息和晚餐的10分钟也由源系列明确确认。先备份至私有Backups/reminder-recovery-20261023-152841，计划和回执不入仓库。

[VERIFIED] build9重启时再次真实复现全部提醒消失，进一步定位旧端快照无提醒字段整条覆盖，以及peerSequences仅内存导致重启重放。新增可选reminderConfigured表达明确提醒意图（含nil），快照/UPDATE兼容保护；Mac本机syncPeerSequences持久化已消费序号。Android编解码及编辑/自动化保留标记。新增CalendarEditorPreservation，保存时不再回写隐藏默认节次/次数和未变的表示格式。源数据不需删库迁移，旧客户端明确清空提醒需升级。

[VERIFIED] 最终核心69、macOS104、Android56项测试通过；Foundation5组及插件打包4项通过。build10实际连续两次退出重启后19条提醒保持，build12安装后再读仍全正确；指定科研整理系列实际编辑器显示提前10分钟，保存后云端MCP仍为10。0分钟曾实际显示日程开始时。按UUID/集合语义核对19条除提醒/兼容标记外业务字段与恢复前一致。首次旧UI试保存产生的无关字段已按备份在App退出时恢复。

[VERIFIED] build12已安装到Applications，App/Widget严格签名、DMG完整性及安装版28工具协议检查通过；本地校验清单更新。get_status新增只读notification_status。真实App返回已授权、当前队列23条（日程18条），最近17:20、17:50、18:50等符合提前10分钟规则。原生通知设置的独立测试通知实际得到系统deliveredNotifications回执，UI显示系统已确认测试通知送达；测试不新增业务日程，仅删除自己的诊断通知。云端get_status及23/26日查询实际成功（各15项）。

[UNVERIFIED] CF-004历史初始化错误未稳定复现，不宣称根除。CF-006自然日程全天/多日实际送达及完整队列覆盖仍需继续验收：独立纯计划计算27个日程请求与系统观测18个待发日程数不同，本轮不把发送测试通知成功等同于完整计划全部进入系统队列。CF-007本机重启/旧快照及内存并发已验证，全部跨设备并发/休眠/长离线组合未完成。用户真实云存储未用于破坏性测试，未发布GitHub。详见MCP_REMAINING_REPAIR_2026-10-23.md。

### 通知队列差异继续定位（2026-10-23，build15已安装）

[VERIFIED] 新增NotificationSnapshotTests先复现失败：已提交提醒10、滞后视图缓存nil时调度收到nil。AppState.rebuildNotifications现一次读取LocalDatabase持久化快照；macOS105项通过。通知状态增加计划数/系统add接受数，原生更新按钮不再无条件宣称全部完成。19条历史提醒再次逐UUID匹配恢复计划，除提醒及兼容标记外其他字段不变（customDaysOfWeek/customWeeks按集合语义核对）。

[VERIFIED] build14/15实际记录计划27、add成功27，但build15系统待发仅日程4/总7。usernoted日志明确“Too many requests, dropping”，涉及同App多个旧source和旧重复摘要。定向清理和removeAllPending等待均未解决；源码修复与系统队列完整性不能混为一谈。多来源与ad-hoc更新的因果关系仍未证实，不放宽签名、不修改系统通知数据库。已请求用户允许短暂关闭重开ClassFlow系统通知，尚待答复。当前CF-006未完成。

[VERIFIED] build15已安装；App/Widget严格签名、DMG完整性、安装版28工具协议、插件4项、Foundation5组通过。核心69/Android56的既有本轮测试结果仍有效，本次新增改动仅Mac。dist校验清单已更新；未发布GitHub。

### 2026-10-27 用户授权通知开关复验

[VERIFIED] 系统ClassFlow通知on→off→on，原生立即更新后计划27/系统接受27/实际日程27、总33；独立测试通知得到系统送达回执。最近3分钟未观测ClassFlow请求过多丢弃。database.json前后SHA256一致，19条恢复提醒仍全匹配。build15保持，不重构建或发布。

[UNVERIFIED] 开关操作前队列已经27/27，不将恢复归因于开关切换；历史多来源丢弃根因仍未最终证实，长期自然日程/休眠恢复送达仍待验收。详细证据见MCP_REMAINING_REPAIR_2026-10-23.md追加章节。

### 2026-10-27 课表末尾裁切与多行备注（本地构建）

安卓buildEffectiveSectionTimes原来把晚间结束时间向整点转换并封顶23:00，23:30–23:40末尾日程因而落在网格滚动内容之外。改为保留实际结束分钟（包括24:00），网格滚动内容末尾增加24dp留白。TimetableExtentTest覆盖23:40/23:59/24:00，验证晚间区间正向及卡片底部不越过内容高度。

安卓ScheduleAddDialog的日程/待办备注改为3–8行的可滚动多行BasicTextField；macOS ScheduleItemCreateSheet及独立PomodoroTaskCreateSheet采用vertical TextField，自动折行、3–8行纵向编辑。Mac事项管理日程备注从标题行单行胶囊移至独立完整折行文本，可选取复制。无持久化模型或业务数据变更。

[VERIFIED] macOS105项测试通过；Android app55项及适配2项无失败。最终包及构建状态见下方后续记录。[UNVERIFIED] 当前无可用adb真机连接，尚未在用户手机验证手势滚动、输入法及多行编辑；不将编译通过冒充设备UI验收。未替换运行中的安装版或发布GitHub。

[VERIFIED] 最终Android测试/assembleRelease成功（57项、versionCode4），APK签名校验通过；预览包dist/ClassFlow-Android-1.2.0-build4-preview.apk及独立SHA256。macOS build16 DMG/ZIP打包成功，App/Widget严格签名、DMG完整性、产物28工具协议通过；dist校验清单同步。本轮未安装至Applications、未发布GitHub；真实业务数据未修改。

### 2026-10-27 macOS课表末尾裁切（build17本地）

用户确认Android滚动已修复，同时提供Mac末尾睡觉卡片裁切截图。Mac TimetableGridView.effectiveSections同样把晚间结束整点封顶23:00，导致23:30之后时间映射到网格同一底边；上一轮遗漏了Mac对应路径。本次抽取expandedSections供视图和回归测试共用，保留实际结束分钟（含24:00），滚动内容增加24pt底部留白。只改显示布局，不改变日程时段、节次或持久化数据。

[VERIFIED] 新TimetableExtentTests先在旧逻辑下复现9处断言失败；修正后macOS106项全部通过，覆盖23:40/23:59/24:00结束时间、正向时段及映射边界。[UNVERIFIED] 新包尚未安装及执行GUI滚动验收。Android本轮不改，未发布GitHub。最终打包验证见后续记录。

[VERIFIED] build17 DMG/ZIP已生成；App/Widget严格签名、DMG完整性、产物28工具协议通过，dist校验清单更新。未覆盖运行中的安装版，需安装新版查看实际滚动效果；不把旧安装版画面当作新版验收。

### 2026-10-27 Android提醒与小组件（本地构建）

安卓原通知设置只存于Compose内存，缺少日程与课程的后台提醒调度，开启系统通知权限不会产生业务提醒。本轮新增ReminderPlan、ReminderScheduler、ReminderReceiver与系统广播接收器：以本地课程/日程及例外投影生成未来一周提醒，区分无提醒、0分钟及正数提前量，按实体独立投影避免同名事项被合并；保存相关数据后、启动、开机、时区/时间及授权变化时重建，每日补充后续计划。触发时重新读取本地数据核验，防止修改或删除后的旧提醒继续显示。课程提醒设置与提前量存储在本地；权限缺失时提示用户在系统设置启用，精确闹钟不可用时使用系统非精确闹钟，可能延迟。系统限制和省电策略可能影响送达。

小组件条目携带实例ID与周次，启动App后定位并展示原生课程/日程详情；周课表由Glance LazyColumn按整行滚动，时间轴及七天一同移动，拉高可显示更多节次，重叠卡片分别提供点击区域。依赖桌面启动器对可滚动Glance组件的支持。

[UNVERIFIED] 没有已连接的Android设备，尚未验证真实通知送达、重启/休眠/设备省电策略、权限授予状态变化、组件手势和详情点击；不得将本地单元测试或编译结果视为真机验收。Android真机与不同桌面启动器兼容性仍待验收。Super Island仍为[BLOCKED]。本轮不修改用户课表，不发布线上Release。

[VERIFIED] Android本轮最终`testDebugUnitTest :app:assembleRelease`成功：app56项、hyperos-adapter2项，共58项；新增ReminderPlanTest覆盖0/10分钟、无提醒、非上课周、单次取消及同名独立日程。build5预览APK已生成并通过签名校验，证书摘要与build4预览包一致。运行设备列表为空；以上仅为本机编译及自动化验证。产物dist/ClassFlow-Android-1.2.0-build5-preview.apk，未安装或发布。

### 2026-11-02 Android小组件显示与尺寸修订（本地准备）

用户报告HyperOS无法堆叠、周组件漏事项、长事项呈哑铃、颜色和文字难辨。代码确认同名同时间的不同UUID日程、同名课程及重叠时段此前在投影中按名称/位置合并，会丢失日期范围和身份；现只按UUID去重，避免跨系列覆盖。WidgetDataManager在当前学期周次与缓存不一致时重投影；写入时按当前日期计算周次，避免长时间运行后继续显示旧周。没有执行用户业务数据测试或数据迁移。

周组件使用事项原始颜色绘制高对比度黑/白文字；跨节片段只有整体首尾圆角，中段平直，避免逐格圆角造成哑铃；使用小尺寸位图减少RemoteViews开销。Glance根视图标记系统widget背景，Android 12+默认格数声明为2x2、4x2、4x4，4x4允许纵向缩到4x2；同时使用小米文档建议的minWidth/minHeight。新增WidgetProjectionTest覆盖分日期同名系列、重叠同名实例、真实颜色和对比度。

[UNVERIFIED] 本机无连接Android设备；长卡片拼接、显示密度、滚动、点击和深浅模式需在HyperOS真机及实际桌面验证。尺寸和背景声明不代表HyperOS堆叠已验证；小米专属小部件与Android原生组件不同，可能涉及其规范与平台审核，目前未冒充已实现。此前build5的提醒调度也仍需真机验收。详见小米开发者平台小部件技术规范、审核与上传说明。

[VERIFIED] 最终Android testDebugUnitTest与:app:assembleRelease成功：app59项、hyperos-adapter2项，共61项，APK签名验证通过且证书摘要与build5预览版相同。build6预览包及SHA256已生成；没有设备连接，未安装、发布或进行真机验收。新增样式构建和61项自动化不证明HyperOS堆叠或跨段位图实际渲染结果。

### 2026-11-02 HyperOS周小组件空白复现反馈（build7本地准备）

用户在build6真机截图反馈：日期标题可见，周组件节次轴和内容完全空白；2×2 ClassFlow组件无法堆叠到同等外观的小米钱包组件。截图只能确认列表区域未呈现，尚无法判断是Glance LazyColumn/ListView宿主兼容、远程集合、位图还是其它原因，也不能判断两者实际网格规格或桌面内部堆叠判定。

为消除对远程列表集合的依赖，周组件改用普通Glance静态行和实例独立的上一页/下一页状态；根据可用高度计算每页行数，遵守Glance Column最多10子项限制，翻页覆盖全部节次，页面边界处为跨节事项绘制圆角并重复标题。此兼容方案暂用点击翻页代替手势滚动；保留build6的原色/对比度、业务实例点击和build5通知实现。WidgetPaginationTest覆盖缩放、节次覆盖和最大10行。

[UNVERIFIED] 没有连接Android或HyperOS真机；不能声称空白问题或堆叠已在用户设备解决。2×2默认规格已按Android声明，用户钱包组件是否采用小米专属小部件、以及两者能否堆叠仍待实机/官方能力核实。小米官方区分安卓原生组件和需平台规范/审核的小米小部件；不伪造通过审核或宣称堆叠能力。

[VERIFIED] build7最终Android testDebugUnitTest与:app:assembleRelease成功，app60项、hyperos-adapter2项，共62项；APK签名校验成功且证书摘要与build6一致。预览包dist/ClassFlow-Android-1.2.0-build7-preview.apk及SHA256已生成，未安装和发布。没有连接真机，静态行及翻页在HyperOS上的显示和交互仍为[UNVERIFIED]。

### 2026-11-02 Android 周组件恢复滚动（build8 本地预览）

用户明确要求撤销 build7 分页并保留 build5 的上下滚动。周组件恢复 Glance LazyColumn 和完整节次数组，移除分页状态与控件，拉高组件可显示更多行；保留同名系列区分及单项点击。为降低 build6 空白重现风险，长事项按节次使用固定 XML 图形资源绘制连续矩形，颜色采用高对比度近似调色板；build6 空白原因尚未证实，不能断言已根治。删除 build7 专属分页代码及其测试。

[VERIFIED] 本地 :app:testDebugUnitTest 60 项通过，:app:assembleRelease 成功，build8 APK 与 build7 使用同一调试签名。无已连接 Android 设备，未安装或发布。[UNVERIFIED] 实际小米桌面的滚动、绘制、点击、尺寸变化及堆叠均须真机验证。

堆叠调查：现有组件为普通 Android AppWidget，分别声明 2×2、4×2、4×4，并非按小米专属组件规范接入的组件。小米公开文档区分两种组件并要求其专属组件遵守标识、进程和提交审核等规则；所查文档未明确普通与小米组件跨应用堆叠的判定或开放接口。无法仅凭截图确认钱包组件实际网格或堆叠失败原因，也不能声称小米未开放堆叠或专属组件审核能保证堆叠。

### 2026-11-02 HyperOS 真机周组件空白排查

用户连接 Android 17 / HyperOS 4 真机。build8 周组件只显示标题；系统日志曾在周组件更新附近报告 TransactionTooLargeException 和桌面宿主失联，但不足以证明它是唯一根因。尝试固定列表高度及单文本诊断时，桌面仍显示旧空白画面，故不能据此判断列表本身不受支持。重新添加周组件后画面恢复；恢复 LazyColumn 后，用户确认可以滚动至底部。build13 使用卡片外层间距与按空间限制标题行数，真机重新添加后相邻日期色块分离；尝试将 Glance 列表 dividerHeight 设零，但真机跨节细白线仍可见，未解决，标题狭窄时仍会省略。此前安装更新未及时刷新宿主画面，重新添加后才能观察新内容；不能宣称刷新稳定或长期卡顿解决。

[VERIFIED] build13 已通过 USB 覆盖安装；实际组件可见，用户确认前一相同滚动实现可滚动；最终 :app:testDebugUnitTest 60 项及 :app:assembleRelease 通过。build13 产物为本地预览包，未发布。未更改用户课程日程数据。[UNVERIFIED] 跨节白线、长标题完整显示、长期流畅度与再次升级后的刷新可靠性尚未解决或验收。

### 2026-11-02 周组件布局实验回退（build15）

build14 尝试将所有节次放入一个滚动列表项并将标题拆分到多节，真机重新添加后出现空白和严重卡顿；该尝试已撤回，手机已覆盖安装 build15 并重新添加组件，真机截图确认恢复到 build13 的逐节列表外观。build15 不包含标题拆分和单列表项实验。此前 build13 用户确认可滚动，build15 恢复同一实现，但本轮未重新验证滚动及长时间流畅度。[UNVERIFIED] 色块内白线、标题省略以及性能问题仍未修复；不得称为已解决。无需修改课表数据。

### 2026-11-02 周组件拼接实验（build16，尚未完成验收）

建立独立包 org.classflow.widgetlab，使用虚构事项真机对照，未读取用户课表。把连续相同事项在每组最多四节内合并成一个色块后，隔离组件的四节样例不再出现内部横线，标题能换行；不同事项或重叠处保留独立项。正式实现新增 WidgetRunPlanner，并保留实例点击 Intent，61 项安卓应用测试通过；build16 覆盖安装、用户重加正式组件后，真机画面仍显示类似原来的细线及省略文字，重新打开应用后仍如此。原因未确认，不能宣称正式组件修复或流畅性、点击完成验收；跨组、重叠及桌面缓存仍需排查。设备仍保留独立测试应用，用户可自行移除；未发布线上版本。

[VERIFIED] 用户随后在 build16 正式周组件真机确认上下滚动及点击事项详情均正常；这不等于白线、标题显示和性能问题已解决。

### 2026-11-02 周组件连续色块真机验证（build17）

隔离测试中核实：旧测试组件未实际移除，重建新实例后才显示包含重叠事项的最新布局。正式 build17 同样加载成功；连续四节分组内单一事项合为同一色块，事项重叠时仍分段，后续连续块重复完整标题；照片确认长标题可换行、连续部分没有逐节白线。重叠和分组边界仍可见细线，部分短而重叠的标题仍可能省略，无法认定完全消除。合并计算由 WidgetRunPlanner 单元测试覆盖空白、重叠和不同事项边界；本机安卓 app 61 项与 release 构建通过，用户数据未修改。build17 已在手机安装并生成本地预览包，未发布 GitHub。正式 build17 的滚动、点击和长期流畅性仍待单独验收；旧 build16 曾由用户确认滚动、点击正常。独立测试应用仍在手机上，可自行卸载。

[VERIFIED] 用户在 build17 真机复测确认周组件滚动和事项详情点击均正常；不等于长期卡顿问题完成验收。

### 2026-11-02 周组件统一时间轴重做（build18，待真机验收）

用户反馈 build17 仍有细线、科研标题重复、运动颜色混杂，以及相同开始时间显示不同高度。源码确认：四节分组与重叠分段重复创建同一实例标题；按节内事项数量分配垂直高度没有遵循真实起止时间；五色近似调色板改变原始颜色。

新增 WidgetWeekLayout 纯布局模型：以实际开始/结束时间为全周共用锚点（课程缺少显式时间时按配置节次回退），重叠事项横向分栏；每个实例只有一个 Card。列表只在没有卡片跨越的位置切分，不把卡片按节次或固定四节组拆开。WeekTimelineGrid 以单一原色背景和单个标题渲染每张卡片，保留原有实例详情 Intent。移除旧 WidgetRunPlanner、近似调色板及失败尝试留下的 cf_widget 图形和 Glance 列表资源覆盖；未修改业务模型或用户数据。新布局说明见 ANDROID_WIDGET_LAYOUT.md。

[VERIFIED] app 单元测试 71 项通过，release 构建成功，签名证书与此前预览包一致；build18 本地 APK 及 SHA256 已生成。新增测试覆盖实例不分段、同刻跨日对齐、两项/三项重叠、相接不重叠、颜色保留、去重、无效时段及周次、课程节次回退、午夜边界、时间解析和 2000 条密集实例无丢失/复制。

[UNVERIFIED] 独立 org.classflow.widgetlab 已安装新布局及虚构示例，已请求用户重建测试组件，目前手机锁屏、仍是旧组件实例，尚未检查新布局真机画面。正式 org.classflow 仍保持 build17，未覆盖安装 build18；不得宣称细线、标题、配色和实际时间对齐已完成真机验收。未发布 GitHub。

### 2026-11-02 build18 完成目标真机复验

[VERIFIED] 独立测试组件加载新实例后，实际截图确认长卡片完整、内部无拼接线且标题只出现一次；20:30 同时开始而结束时间不同的每日规划/力量训练跨天顶部对齐、原始红绿颜色保持。短卡片初次测试文字被裁，已将时间锚点间最小高度调为26dp并为窄重叠栏优先显示事项名称，复验短卡片文字可见。

[VERIFIED] 正式 org.classflow 已覆盖安装 build18；正式课表上午截图确认科研A标题单次且无内部细线，晚间截图确认实际每日复盘开始位置跨天一致、每个运动事项独立保持原色、卡片连续。用户复测确认滚动与科研详情点击均正常。自23:21到本次核验的目标日志未见 ClassFlow TransactionTooLarge 或 RemoteViews 加载异常；这是有限观察窗口，不是长期无故障保证。app 71 项测试/assembleRelease/签名和diff检查通过，本地 build18 APK 与 SHA256 已同步；未发布GitHub。

临时 org.classflow.widgetlab 测试应用已卸载，仅移除其虚构数据及测试组件；正式应用、课表与同步配置保留。本轮未修改业务持久化数据。[UNVERIFIED] 其他桌面/设备、长时间休眠及高密度重叠界面仍未真机全面验收，极窄或短事项标题仍可能省略并通过详情查看。

### 2026-11-04 macOS / Android 重复日程取消记录迁移与安全清理

本轮仅处理重复 CalendarEvent 的取消维护链路，保留此前周课表组件等未提交修改。未操作用户真实日程、未覆盖安装、未发布 GitHub。

根因：单次删除的事实仅存在于 CANCEL 例外，清掉例外即恢复原始重复规则。新增 CalendarEvent.excludedDates，默认空、排序去重，按原始实例日期排除；先计算重复次数，再过滤排除日期，跨天投影保留 recurrenceOriginDate。新单次删除直接保存日期；MCP 单次编辑产生的副本清空继承的排除日期，未来拆分按未过滤的原始次数计算。

两端新增纯 CalendarCancellationCleanup 预览/迁移计划和原生设置入口，支持批量、只迁移、迁移后清理、重复 CANCEL 去重与幂等重试。仅清理归属唯一重复日程、日期有效、无调动字段且同日没有其他类型的 CANCEL；保护课程时段、RESCHEDULE/MAKEUP/ROOM_CHANGE、未知类型、孤立/冲突例外。保留原 UUID，不拆分大量单独日程。

提交前检查预览是否过期，先做应用私有恢复备份，再将日程、例外和 tombstones 原子写入；备份失败停止，Android 检查 commit 结果并在失败时尝试恢复。Mac 通过既有变更日志发布同步更新/删除，随后刷新 UI、组件和提醒计划。普通编辑、同步及 rebase 合并同 UUID 排除日期，避免旧快照丢字段；同步不再用同名/同起始时间合并不同 UUID 的日程。UUID tombstone 大小写兼容。

顺带修复本功能依赖的备份缺口：两端新 .classflow 导出升级 formatVersion 2，包含并校验 calendarEvents.json，继续读 v1；Android 恢复读取 exceptions，校验错误或缺失组件拒绝导入。ICS 仅导出有效投影，ClassFlow X 属性保留完整原系列，重新导入不误建课程或拆分实例；Mac 课程 ICS 不再推断补齐缺少的周次。外部软件若移除 X 属性，不保证无损系列往返；完整备份仍用 .classflow。参与同步的客户端应全部更新后再做清理。

[VERIFIED] 本轮最终执行：
- `swift test --package-path packages/core-engine`：77 项通过。
- `swift test --package-path apps/macos`：112 项通过。
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home apps/android/gradlew -p apps/android :app:testDebugUnitTest :app:assembleRelease`：app 78 项、0 失败/错误/跳过，Release APK 构建成功。
- `python3 scripts/test_foundation.py`：5/5 组通过（含 JSON 语法检查）。
- `python3 scripts/test_mcp_transport.py apps/macos/.build/debug/ClassFlow`：实际可执行文件初始化、28 工具 Schema/注解、ping、错误协议和 EOF 退出通过，没有调用业务写工具。
- `git diff --check`：通过。

新增自动化验证包含：各重复频率与结束次数、跨天起点、预览不修改、两阶段迁移/清理、重复记录、重复执行、1000 系列/2000 CANCEL 批量、课程和调课保护、未知类型保留、清理备份/过期预览/备份 I/O 失败、重启读取、MCP 日期查询、旧编辑器字段保护、旧快照与并发排除日期并集、备份 v1/v2 与篡改校验、ICS 原系列往返及再次导入不重复。共同 JSON fixture 位于 packages/fixtures/sample_calendar_exclusions.json。

[UNVERIFIED] 尚未进行两端维护界面的人工点击验收、Android 真机磁盘故障注入、真实 BYOC 跨设备端到端、实际系统通知送达。本轮没有为用户执行迁移，没有重新生成 DMG，没有安装或发布新包。Android APK 位于本地常规 Gradle 输出；dist 仍为此前发布/打包基线。完整规则、文件/函数和入口见 docs/RECURRING_EVENT_CLEANUP.md。

### 2026-11-04 重复日程维护修复包构建（macOS build18 / Android build19）

按用户「打包构建」请求保持应用版本 1.2.0，macOS package_macos.sh 的 BUILD_NUMBER 从17升至18，Android versionCode从18升至19。沿用原有业务修复与此前组件改动，本轮没有追加业务功能。旧 macOS build17 DMG/ZIP已按带build编号文件名另存，原Android build18预览包保留。

[VERIFIED] `CLASSFLOW_SKIP_INSTALL=1 bash scripts/package_macos.sh` 退出0：Release主程序、原生WidgetKit扩展、DMG与ZIP生成。主程序与扩展均为1.2.0/build18，codesign严格校验通过（沿用ad-hoc签名，未进行Developer ID公证）；DMG hdiutil完整性校验通过。实际只读挂载DMG，包内主程序哈希与dist一致，挂载App/Widget签名与版本正确；dist App及DMG内实际可执行文件均通过28工具MCP传输测试，没有业务工具写调用。ZIP完整性、包内版本和主程序哈希校验通过。脚本确认跳过安装与组件注册。

[VERIFIED] `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home apps/android/gradlew -p apps/android :app:testDebugUnitTest :hyperos-adapter:testDebugUnitTest :app:assembleRelease` 成功：app78项、adapter2项，共80项，0失败/错误/跳过。APK包名org.classflow、versionName1.2.0/versionCode19、minSdk26/targetSdk35；apksigner验证通过，签名证书与build18一致（沿用项目现有debug签名配置，不是新建正式发布密钥）。

本轮产物：
- dist/ClassFlow-1.2.0-macOS-arm64-build18.dmg
- dist/ClassFlow-1.2.0-macOS-arm64-build18.zip
- dist/ClassFlow-Android-1.2.0-build19-preview.apk

各产物的.sha256及dist/SHA256SUMS.txt已更新；macOS不带build编号的DMG/ZIP与build18内容相同。`git diff --check`通过。此前核心77项/macOS112项结果属于上一修复轮，本打包轮未重复执行这两组单元测试，不混写成本轮执行。

[UNVERIFIED] 尚未覆盖安装、人工点击维护界面、执行真实取消迁移、验证真实BYOC跨设备及系统通知送达；未推送源码或更新GitHub Release。插件工具Schema未变化，本轮未重打插件附件。请先更新参与同步的两端，再在维护入口预览清理。

### 2026-11-04 MCP 历史取消维护

Mac 桌面执行器及 Android 独立执行器新增只读预览和确认提交，工具分别为 30、6 项。10 分钟单次令牌绑定当前预览，提交沿用原生过期检查、备份和原子保存。确认参数由调用方声明，不是系统级确认。不自动同步。
[VERIFIED] macOS 113 项、Android app 79 项通过，Mac stdio 30 工具协议通过。测试使用隔离数据，未执行真实清理。
[UNVERIFIED] 未重新打包、安装或进行 ChatGPT 云端验收；此前安装包不包含新接口。
