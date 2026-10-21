# ClassFlow

> **Native First, Local First, Bring Your Own Cloud.**  
> 一款专为 macOS 与 Android 打造的高颜值、本地离线优先、原生跨平台课程表与时间规划应用，内置 **ChatGPT 全功能接管套件（28 个 MCP 工具 + 安全云端隧道）** 与小米手环腕上快应用。

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![macOS 15.8+](https://img.shields.io/badge/macOS-15.8%2B%20%28Apple%20Silicon%29-black?logo=apple)](#target-platforms)
[![Android API 26+](https://img.shields.io/badge/Android-API%2026%2B%20%2F%20HyperOS-green?logo=android)](#target-platforms)
[![MCP Server: 28 Tools](https://img.shields.io/badge/MCP%20Server-28%20Tools%20Ready-purple?logo=openai)](#chatgpt--ai-全功能接管套件)
[![Release: v1.2.0](https://img.shields.io/badge/Release-v1.2.0-orange)](https://github.com/find-xin/ClassFlow/releases/tag/v1.2.0)

---

## 📖 目录

- [1. 核心设计理念](#1-核心设计理念)
- [2. 当前项目状态与验证矩阵](#2-当前项目状态与验证矩阵)
- [3. 平台与架构概览](#3-平台与架构概览)
- [4. 核心功能亮点](#4-核心功能亮点)
  - [🍏 原生 macOS 客户端](#-原生-macos-客户端)
  - [🤖 原生 Android 客户端](#-原生-android-客户端)
  - [🧠 ChatGPT / AI 全功能接管套件](#-chatgpt--ai-全功能接管套件)
  - [☁️ 自建云同步 (BYOC) 与离线优先](#️-自建云同步-byoc-与离线优先)
  - [⌚️ 小米手环 9 / 10 腕上快应用](#️-小米手环-9--10-腕上快应用)
- [5. 下载与安装](#5-下载与安装)
- [6. 快速开始与编译构建](#6-快速开始与编译构建)
- [7. 项目文档导航](#7-项目文档导航)
- [8. 开源协议](#8-开源协议)

---

## 1. 核心设计理念

1. **Native First（原生优先）**：
   - macOS 采用 Swift 6 + SwiftUI + AppKit 深度融合构建；
   - Android 采用 Kotlin + Jetpack Compose + Room 原生构建；
   - 彻底摒弃高资源开销的 WebView 与跨平台 Web 包装层，启动毫秒级响应，交互丝滑。
2. **Local First & Offline First（本地离线优先）**：
   - 本地 SQLite / Room 数据库为唯一真实数据源（Single Source of Truth）；
   - 断网离线完全可用，网络同步异步解耦，绝不阻塞用户主界面。
3. **Bring Your Own Cloud（BYOC 自建云）**：
   - 无官方中心化账号，无强制商业云绑定；
   - 支持主流对象存储（AWS S3、七牛云 Kodo）与标准 WebDAV（坚果云、自建 NAS、Nextcloud）跨端增量漫游。
4. **Clean Domain Separation（清晰领域模型分离）**：
   - 课程与周期排课模型（`Course` / `CourseSchedule`）与通用日历日程（`CalendarEvent`）严格分层解耦，运行时统一投影至 `TimelineItem`。
5. **Privacy & Security First（隐私安全第一）**：
   - 云存储密钥（AK/SK）与 AI 隧道凭证绝不以明文形式写入配置文件或日志；
   - 强制使用 macOS Keychain 与 Android KeyStore 进行硬件级安全保护。
6. **No Fake Implementations（真实规范，绝不伪造）**：
   - 严禁使用系统顶层悬浮窗伪造系统级灵动岛等未授权功能；未验证能力明确标记，保障系统稳定性与合规性。

---

## 2. 当前项目状态与验证矩阵

本项目遵循严格的自动化测试与平台规范验证（Status Tracking Tags）：

| 模块 / 特性 | 实现栈 | 测试覆盖 | 验证状态 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| **Core Engine** | Swift 6 Package | 62 单元测试通过 | `[VERIFIED]` | 覆盖冲突检测、周次计算、ICS 编解码、ZIP 备份包、S3/WebDAV 协议与 3-way 合并 |
| **macOS Client** | SwiftUI + AppKit | 75 单元测试通过 | `[VERIFIED]` | macOS 15.8 Sequoia 原生运行，支持深色模式、键盘导航、联想输入与多学期管理 |
| **macOS Widget** | WidgetKit Extension | 已嵌入并本地签名 | `[VERIFIED]` | 独立 `.appex` 扩展，支持系统桌面与通知中心课表微件 |
| **MCP AI Server** | Model Context Protocol | 自动化测试通过 | `[VERIFIED]` | 28 个标准工具覆盖课程、日程、待办、番茄专注、云同步与学期切换 |
| **ChatGPT 安全隧道** | Swift Native Subprocess | 进程与钥匙串测试通过 | `[VERIFIED]` | 安全管理 `tunnel-client` 守护进程，本地健康探针，Token 存入 Keychain |
| **Android Client** | Jetpack Compose + Room | 46 项单元测试及 APK 构建 | `[VERIFIED]`（真机同步待验收） | 支持 Material 3/WakeUp 风格网格、周次悬浮抽屉、跨模块同步与多尺寸小组件 |
| **Xiaomi Band 快应用** | Vela QuickApp (`.rpk`) | 规范校验通过 | `[VERIFIED]` | 适配小米手环 9/10，独立离线查看与课程时间震动提醒 |
| **Xiaomi Super Island** | HyperOS Adaptation | 架构设计完成 | `[BLOCKED]` | 需小米开放平台官方 App ID 审批与白名单资质，已优雅降级为常驻通知卡片 |

---

## 3. 平台与架构概览

```
ClassFlow Repository
├── packages/
│   ├── core-engine/         # 跨平台核心业务逻辑 (Swift Package: 调度、同步引擎、冲突检测)
│   ├── schemas/             # 官方数据 JSON Schema 规范 (Course, Semester, Backup)
│   └── fixtures/            # 跨端兼容性与导入测试用例
├── apps/
│   ├── macos/               # macOS 原生客户端 (SwiftUI 6 + AppKit + WidgetKit .appex)
│   └── android/             # Android 原生客户端 (Jetpack Compose + Room + Glance)
├── plugins/
│   └── classflow/           # 本地 Work/Codex 插件、MCP 入口与技能说明
├── dist/                    # 正式发布打包镜像 (DMG, ZIP, APK, RPK)
├── docs/                    # 完整架构规范、同步协议与 ADR 记录
└── scripts/                 # 构建与自动化安装脚本 (package_macos.sh, install_mcp.sh)
```

---

## 4. 核心功能亮点

### 🍏 原生 macOS 客户端
- **WakeUp 唯美课表美学**：流动渐变背景壁纸、高斯磨砂顶栏、悬浮时间轴与优雅马卡龙色卡；
- **智能联想输入（WakeUp Style）**：添加课程时实时匹配已有课程库、教师与教室名称，点击一键填入；
- **学分管理与精准排课**：支持单独设置课程总学分、课号、不同时间段的独立教师与教室，并提供周次冲突检测；
- **原生桌面小组件**：内置独立构建并签名的 `ClassFlowWidgets.appex`，原生支持通知中心中号/大号课程预览卡片；
- **系统日历自动同步**：基于 EventKit 安全双向增量同步，在系统日历中清晰规划学期课程。

### 🤖 原生 Android 客户端
- **WakeUp 唯美顶栏与侧拉抽屉**：左侧一键呼出悬浮周次滚轮与学期概览卡片，右侧集成添加、导入、导出、设置；
- **多尺寸桌面微件**：深色磨砂玻璃（Frosted Dark Glass）微件，提供“下节课胶囊”、“今日课表色条”与“全周迷你网格”；
- **跨平台教务快速导入**：支持一键粘贴教务解析脚本、WakeUp 课程表 JSON、AISchedule 格式及 `.classflow` 备份包；
- **系统通知与课前提醒**：支持提前 10/15/30 分钟自定义通知，课间自动切换下一节状态。

### 🧠 ChatGPT / AI 全功能接管套件
ClassFlow 提供本地 stdio MCP 与云端 Secure MCP Tunnel 两种连接方式，可通过自然语言管理课程、日程、待办和专注。系统授权、钥匙串及所有设置页面不属于业务编辑接口。

#### 28 个标准 MCP 工具支持
1. **完整读取**：状态、课表与列表查询，以及 `get_editing_context` 分页读取完整字段和独立时段 UUID。
2. **课程与时段**：课程元数据、多时段、独立教师/教室、节次/具体时间和任意周次；`manage_course_schedule` 提供三级范围调课/停课和单次恢复。
3. **日程**：完整编辑标题、时间模式、跨天/全天、重复规则与结束日期/次数、提醒、颜色与备注；支持三级范围修改和删除。
4. **待办**：课程关联、三级优先级、起止日期、精确分钟数、备注与重复规则；支持完成、恢复及周期任务后续生成。
5. **专注**：番茄钟、正向计时、目标倒计时，暂停、继续、结束、`reset_focus_timer` 重置和真实统计。
6. **批量与同步**：`batch_edit_items` 每批 1–100 项原子编辑与预检；`import_calendar_events` 每批最多 500 条展开实例迁移；保留 App 导航、学期切换与已配置 BYOC 同步。

更新 App 后，请在 ChatGPT 原 Classflow 连接点击“刷新工具”，再新建聊天；出现 28 项工具后使用新版参数。本地客户端重连 MCP。详见 [完整接口及限制](docs/CHATGPT_INTEGRATION.md)。

#### 架构与安全特性
- **本地 IPC 桌面邮箱**：位于 `~/Library/Application Support/ClassFlow/automation`（0700 权限），毫秒级通知主线程 AppState，AI 改动在界面实时呈现；
- **安全云端隧道 (Secure MCP Tunnel)**：ClassFlow 内置安全子进程守护与健康探测，配合 `tunnel-client` 使得云端 ChatGPT 能够端到端加密访问本地客户端；
- **硬件级凭证保险箱**：隧道 Token 与云存储秘钥严格存入 macOS Keychain，杜绝明文泄露。

### ☁️ 自建云同步 (BYOC) 与离线优先
- **零中心化账户依赖**：支持私有 S3（MinIO、Cloudflare R2、AWS）与 WebDAV（坚果云、NAS）；
- **逻辑删除与防复活机制 (Tombstone)**：删除操作生成加密墓碑并同步漫游，彻底解决“先删后同步导致课程复活”的常见顽疾；
- **三方字段级合并 (3-Way Merge)**：支持多设备并发修改不同字段时的无损平滑合并。

### ⌚️ 小米手环 9 / 10 腕上快应用
- 基于快应用规范（Vela RPK）深度轻量化定制；
- 抬腕即看正在进行中与即将开始的课程，支持脱离手机独立离线显示与震动提醒。

---

## 5. 下载与安装

### 正式发布文件 (Release v1.2.0)

| 平台 | 安装包名称 | 文件大小 | SHA-256 校验和 |
| :--- | :--- | :--- | :--- |
| **macOS (Apple Silicon)** | `ClassFlow-1.2.0-macOS-arm64.dmg` | 4.5 MB | `f950ff0a82512f431ccab852e278a30dd39f56ca33d76c1359e6b5c5c93ad527` |
| **macOS ZIP** | `ClassFlow-1.2.0-macOS-arm64.zip` | 3.9 MB | `23ae708475f84578ff8b04b694543353f5b6b148ae3ac2bda7203546e2e19fc5` |
| **Android 客户端** | `ClassFlow-1.2.0-android.apk` | 13.4 MB | `3837bcd7ce6c0ea3bdf4d6e023fd51defeef587e6ffe7e7bc953f6362b2adf3a` |
| **小米手环（未变更）** | `ClassFlow-1.0.0-xiaomi-band.rpk` | 4.7 KB | `8a3b4c6cbcfd3d6ec4e4a7580b13ea6dc98c6b543bca3735de98aae926938602` |
| **小米手环源码包（未变更）** | `ClassFlow-1.0.0-xiaomi-band.zip` | 4.7 KB | `8a3b4c6cbcfd3d6ec4e4a7580b13ea6dc98c6b543bca3735de98aae926938602` |
| **本地 Work / Codex 插件** | `ClassFlow-Local-Plugin-1.2.0.zip` | 18.4 KB | `7140b87a1d6502dc9ff4e1de255368e41ae4ecd06a7b9275f6d77fa6a40037f7` |

> 校验命令：`cd dist && shasum -a 256 -c SHA256SUMS.txt`

---

## 6. 快速开始与编译构建

### macOS 客户端开发与打包
```bash
# 1. 运行所有单元与自动化测试
swift test --package-path packages/core-engine
swift test --package-path apps/macos

# 2. 一键编译并打包包含 Widget 扩展的 DMG 镜像
./scripts/package_macos.sh
```

### 接入 ChatGPT / Work / Codex (MCP)

| 入口 | Release 下载 | 安装方式 |
| --- | --- | --- |
| 普通 ChatGPT 聊天 | macOS App + [接入说明](https://github.com/find-xin/ClassFlow/releases/download/v1.2.0/ClassFlow-ChatGPT-Setup.md) | 安装官方 tunnel-client，在 App 的 AI / ChatGPT 设置填写 Tunnel ID 和专用运行密钥，再在 ChatGPT 添加同一 Tunnel 连接 |
| 本地 Work / Codex | macOS App + [本地插件包 1.2.0](https://github.com/find-xin/ClassFlow/releases/download/v1.2.0/ClassFlow-Local-Plugin-1.2.0.zip) | 解压插件包，在其目录运行 `bash scripts/install_mcp.sh`，新建本地会话选用插件 |

本地插件包不等于普通云端聊天的连接方式，也不代表已上架 ChatGPT 官方公共目录。插件版本与 App 版本分别编号。详见 [简明接入说明](docs/CHATGPT_RELEASE_SETUP.md)。

---

## 7. 项目文档导航

- 📘 [系统架构设计 (ARCHITECTURE.md)](docs/ARCHITECTURE.md)
- 🧠 [ChatGPT 与 AI 接管集成指南 (CHATGPT_INTEGRATION.md)](docs/CHATGPT_INTEGRATION.md)
- 📋 [跨平台测试计划与验收报告 (TEST_PLAN.md)](docs/TEST_PLAN.md)
- 📦 [版本发布记录 (RELEASE_NOTES_v1.2.0.md)](RELEASE_NOTES_v1.2.0.md)
- 🤝 [贡献者与开发规范 (AGENTS.md)](AGENTS.md)
- 📐 [架构决策记录 (docs/ADR/)](docs/ADR/)

---

## 8. 开源协议

ClassFlow 遵循 [Apache License 2.0](LICENSE) 开源协议。
Copyright © 2026 ClassFlow Contributors. All rights reserved.
