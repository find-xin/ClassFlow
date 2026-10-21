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
