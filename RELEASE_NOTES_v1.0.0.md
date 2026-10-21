# ClassFlow v1.0.0

🎉 **ClassFlow 1.0.0 正式发布！**
ClassFlow 是一款专为 macOS 与 Android 打造的高颜值、本地离线优先、原生跨平台课程表与时间规划应用，并同步提供小米手环端腕上快应用，以及 **ChatGPT / AI 全场景接管套件（23 个 MCP 工具与安全云端隧道）**。

---

## 🌟 核心特性亮点

### 🍏 原生 macOS 体验 (Swift 6 + SwiftUI + AppKit)
- **WakeUp 唯美课表美学**：全景流动壁纸底色、高斯磨砂顶栏、悬浮时间抽屉与马卡龙色系课程卡片；
- **智能联想输入（WakeUp Style）**：添加与编辑课程时，自动检索已选课程、老师与教室，点击即可快速填入；
- **高对比度夜间模式**：深度适配系统深色外观，独立暗夜渐变壁纸、柔和卡片反光与高对比度文字排版；
- **原生 macOS 桌面小组件**：内置独立编译并签名的原生 WidgetKit 扩展（`.appex`），完美适配 macOS 通知中心与桌面；
- **快捷键与手势导航**：支持周次键盘快捷导航（`[` / `]` / `0`）、触控板平滑手势翻周。

### 🤖 现代 Android 架构 (Jetpack Compose + Room)
- **WakeUp 唯美顶栏与悬浮周次抽屉**：左侧点击展开悬浮滑动周次抽屉与学期缩略卡，右侧配备 `+` 添加、`↓` 导入、`⤴` 导出、`⋮` 快捷菜单四大独立操作；
- **WakeUp 风格桌面小组件**：深色磨砂玻璃微件，支持“下节课”倒计时胶囊磁贴、“今日课表”垂直色条磁贴与“一周课表”迷你全周网格；
- **全量多端云同步 (BYOC)**：跨设备完整同步课程、日程、待办清单、专注任务打卡统计与外观配置；支持用户自定义同步间隔与一键立即全量同步；
- **智能导入与多格式导出**：支持直接粘贴教务解析脚本 / WakeUp JSON 一键导入课表，并支持 `.classflow` 全量备份包和 `.ics` 系统通用日历文件导入导出与系统分享；
- **动漫暮光与深色模式**：新增动漫暮光紫蓝渐变预设，卡片透明度可自由滑动调节（30%~100%），夜间模式高对比度深度优化。

### 🧠 ChatGPT 与 AI 全功能接管套件 (MCP Server & Tunnel)
- **23 个标准 MCP 工具**：涵盖课表查询、课程增删改查、日程日历管理、待办任务生命周期、番茄专注计时器打卡与统计、学期周次切换、全量云同步与应用调度；
- **原生桌面桥接机制**：通过本地安全 IPC 邮箱（`~/Library/Application Support/ClassFlow/automation`）直接驱动主线程 AppState，实现无感即时 UI 刷新；
- **安全云端 MCP 隧道**：内置 `tunnel-client` 进程生命周期管理与安全健康探针，支持云端 ChatGPT（网页版/移动端）直接安全穿透连接本地 ClassFlow 实例；
- **零明文密钥安全**：隧道 API Token 严格加密存储于 macOS 钥匙串（Keychain），严禁明文写入配置文件或命令行参数。

### ⌚️ 小米手环 9 / 10 腕上快应用
- **原生轻应用规范**：基于 Vela 快应用标准打造（`.rpk`），轻量低功耗；
- **腕上课程速览**：抬腕即看当前正在进行与即将开始的课程，展示教室与节次时间；
- **离线运行与震动提醒**：无需依赖手机常驻后台即可在手环端独立查看。

### ☁️ 自建云同步与隐私优先 (BYOC)
- **零中心化账号**：无需官方账户，所有数据保存在本地 SQLite/Room 数据库中；
- **标准 S3 & WebDAV 协议**：支持配置专属对象存储或 WebDAV（如坚果云、自建私有云）多端增量安全漫游；
- **防止误删复活 (Tombstone)**：采用逻辑删除墓碑机制与 field-level 合并策略，彻底杜绝多端同步中的数据复活与覆盖丢失。

---

## 📦 安装包与 SHA-256 校验

| 平台 | 架构 / 类型 | 文件名 | 大小 | SHA-256 校验和 |
| :--- | :--- | :--- | :--- | :--- |
| **macOS 安装镜像** | Apple Silicon (arm64) | `ClassFlow-1.0.0-macOS-arm64.dmg` | 4.4 MB | `db91423f6297fe9c9988a5e27c156fc4ca29cffa133d374ea10c5dd86214e316` |
| **macOS 绿色免安装** | Apple Silicon (arm64) | `ClassFlow-1.0.0-macOS-arm64.zip` | 6.8 MB | `6acd32451d3924477053ea63e3677e1a56612c4b2474035906ea84eb426208a8` |
| **Android 客户端** | Universal (ARM/x86) | `ClassFlow-1.0.0-android.apk` | 13.4 MB | `0dc583a250f3ae4ff3f22b78b1aff4c6e766c9834abaf30af4170c6e20bdc884` |
| **小米手环快应用** | 手环快应用安装包 | `ClassFlow-1.0.0-xiaomi-band.rpk` | 5.0 KB | `b296eb9640944c38a6c559074f04c1033d2c353eb92c62a20a3b01ad4322bf74` |
| **小米手环离线包** | 开发者安装源码包 | `ClassFlow-1.0.0-xiaomi-band.zip` | 5.0 KB | `b296eb9640944c38a6c559074f04c1033d2c353eb92c62a20a3b01ad4322bf74` |

---

## 🚀 各平台安装与配置指南

### 🍏 macOS
1. 下载 `ClassFlow-1.0.0-macOS-arm64.dmg` 并双击挂载；
2. 将 `ClassFlow.app` 拖入 `Applications`（应用程序）文件夹即可直接运行；
3. **桌面小组件**：打开 macOS「通知中心」->「编辑小组件」-> 搜索 `ClassFlow` 即可添加课程小组件。

### 🤖 Android
1. 下载 `ClassFlow-1.0.0-android.apk` 至手机；
2. 允许浏览器/文件管理器“安装未知来源应用”权限并点击安装；
3. 首次启动授权日历和通知权限以享受自动日历导入与课前提醒。

### 🧠 ChatGPT / Codex AI 接管配置
1. **本地 Codex 模式**：
   - 运行 `./scripts/install_mcp.sh` 一键注册 ClassFlow 本地 stdio MCP 服务器；
   - 在支持 MCP 的客户端（如 Claude Desktop、Cursor、Codex）中直接调用。
2. **云端 ChatGPT 聊天接管模式**：
   - 打开 ClassFlow 设置 ->「AI 助手」选项卡；
   - 填入您的安全隧道服务 URL 与 Token（安全存储在 Keychain），点击「连接隧道」；
   - 在 ChatGPT 网页端/客户端添加 Action 或自定义 GPT，直接用自然语言管理课程、日程与专注打卡。

### ⌚️ 小米手环 9 / 10
1. 下载 `ClassFlow-1.0.0-xiaomi-band.rpk`；
2. 使用小米官方「小米运动健康」开发者模式、AstroBox 或 Nexio 手环管理工具将应用侧载推送至手环；
3. 手环进入应用列表即可打开「ClassFlow 课表」。
