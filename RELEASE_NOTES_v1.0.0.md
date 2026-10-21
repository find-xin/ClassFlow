# ClassFlow v1.0.0

🎉 **ClassFlow 1.0.0 正式发布！**
ClassFlow 是一款专为 macOS 与 Android 打造的高颜值、本地离线优先、原生跨平台课程表与时间规划应用，并同步提供小米手环端腕上快应用，以及 **ChatGPT / AI 全场景接管套件（23 个 MCP 工具与安全云端隧道）**。

---

## 🌟 核心特性亮点

### 🍏 原生 macOS 体验 (Swift 6 + SwiftUI + AppKit)
- **WakeUp 唯美课表美学**：全景流动壁纸底色、高斯磨砂顶栏、悬浮时间抽屉与马卡龙色系课程卡片；
- **智能排版与冲突避让**：非本周课程仅在时段真正空白无安排时才透视显示；当该时段有任何生效课程或日程安排时，非本周课程自动让位隐藏，彻底消除并排挤占。日程支持根据物理时间动态智能反推作息节次；
- **智能联想输入（WakeUp Style）**：添加与编辑课程时，自动检索已选课程、老师与教室，点击即可快速填入；
- **高对比度夜间模式**：深度适配系统深色外观，独立暗夜渐变壁纸、柔和卡片反光与高对比度文字排版；
- **原生 macOS 桌面小组件**：内置独立编译并签名的原生 WidgetKit 扩展（`.appex`），完美适配 macOS 通知中心与桌面；
- **快捷键与手势导航**：支持周次键盘快捷导航（`[` / `]` / `0`）、触控板平滑手势翻周。

### 🤖 现代 Android 架构 (Jetpack Compose + Room)
- **WakeUp 唯美顶栏与悬浮周次抽屉**：左侧点击展开悬浮滑动周次抽屉与学期缩略卡，右侧配备 `+` 添加、`↓` 导入、`⤴` 导出、`⋮` 快捷菜单四大独立操作；
- **智能排版对齐**：双端对齐非本周课程智能避让逻辑，遇本周活跃日程与课程自动隐藏让位；
- **WakeUp 风格桌面小组件**：深色磨砂玻璃微件，支持“下节课”倒计时胶囊磁贴、“今日课表”垂直色条磁贴与“一周课表”迷你全周网格；
- **全量多端云同步 (BYOC)**：跨设备完整同步课程、日程、待办清单、专注任务打卡统计与外观配置；支持用户自定义同步间隔与一键立即全量同步；
- **智能导入与多格式导出**：支持直接粘贴教务解析脚本 / WakeUp JSON 一键导入课表，并支持 `.classflow` 全量备份包和 `.ics` 系统通用日历文件导入导出与系统分享；
- **动漫暮光与深色模式**：新增动漫暮光紫蓝渐变预设，卡片透明度可自由滑动调节（30%~100%），夜间模式高对比度深度优化。

### 🧠 ChatGPT 与 AI 全功能接管套件 (MCP Server & Tunnel)
- **24 个标准 MCP 工具**：涵盖课表查询、课程增删改查、日程日历管理与批量导入（`import_calendar_events`）、待办任务生命周期、番茄专注计时器打卡与统计、学期周次切换、全量云同步与应用调度；
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
| **macOS 安装镜像** | Apple Silicon (arm64) | `ClassFlow-1.0.0-macOS-arm64.dmg` | 4.4 MB | `2e3092d356a7ac2ef50703d980bb429fd50383d92ac0d15ca039beb3bfdd3d7a` |
| **macOS 绿色免安装** | Apple Silicon (arm64) | `ClassFlow-1.0.0-macOS-arm64.zip` | 6.8 MB | `3096621d7ac6bc60fc2d7847c2a2015311b186d5f2bb752d455abe7719c40db7` |
| **Android 客户端** | Universal (ARM/x86) | `ClassFlow-1.0.0-android.apk` | 13.4 MB | `a07137a280094137b487640c04bf3dccf0702f6107decc7c3ab7261b27912b8a` |
| **小米手环快应用** | 手环快应用安装包 | `ClassFlow-1.0.0-xiaomi-band.rpk` | 4.7 KB | `8a3b4c6cbcfd3d6ec4e4a7580b13ea6dc98c6b543bca3735de98aae926938602` |
| **小米手环离线包** | 开发者安装源码包 | `ClassFlow-1.0.0-xiaomi-band.zip` | 4.7 KB | `8a3b4c6cbcfd3d6ec4e4a7580b13ea6dc98c6b543bca3735de98aae926938602` |
| **ChatGPT 本地插件** | Codex / Work 插件包 | `ClassFlow-Local-Plugin-1.1.0.zip` | 15.0 KB | `dd7a7de60a2c57dd28d10f918849acffe56616f64bf75f706647a919a3962ddc` |
| **ChatGPT 配置指南** | 接入说明与文档 | `ClassFlow-ChatGPT-Setup.md` | 3.0 KB | `4820a4864226ff9b9fbe1a356464071bb5ea49e325fdb51f77dd9b3fe747375c` |

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

### 🧠 ChatGPT / Work / Codex 接入与插件下载

Release 现提供独立附件：

- [ClassFlow-Local-Plugin-1.1.0.zip](https://github.com/find-xin/ClassFlow/releases/download/v1.0.0/ClassFlow-Local-Plugin-1.1.0.zip)：本地 Work / Codex 插件、安装脚本及文档；插件版本与 App 版本分别编号。
- [ClassFlow-ChatGPT-Setup.md](https://github.com/find-xin/ClassFlow/releases/download/v1.0.0/ClassFlow-ChatGPT-Setup.md)：两种入口的安装与连接步骤。对应 `.sha256` 文件用于下载校验。

**本地 Work / Codex**：先安装 macOS App 和支持插件的客户端，确保 `codex` CLI 与 Python 3 可用；解压插件 ZIP，在 `ClassFlow-Local-Plugin` 文件夹运行 `bash scripts/install_mcp.sh`，新建本地会话选用 ClassFlow。脚本不自动配置 Claude 等其他客户端。

**普通 ChatGPT 聊天**：安装官方 [tunnel-client](https://github.com/openai/tunnel-client/releases/latest)，在 Platform 创建自己的隧道和专用运行密钥。打开 ClassFlow → 课表设置 → **AI / ChatGPT**，填写 **Tunnel ID（不是 URL）**及运行密钥，连接至「隧道已就绪」。随后在 ChatGPT 添加自定义 MCP，Connection 选择 **Tunnel** 并选用同一 ID。可开启 App 内自动启用，日常不需要常驻终端。

普通聊天使用隧道连接；本地插件 ZIP 适用于本地执行入口。每位用户需要自己的账户、工作区权限、隧道及密钥，发布包不含任何预置凭据。GitHub 发布插件包不等于上架 ChatGPT 官方公共目录。详见 [官方隧道说明](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels)。

### ⌚️ 小米手环 9 / 10
1. 下载 `ClassFlow-1.0.0-xiaomi-band.rpk`；
2. 使用小米官方「小米运动健康」开发者模式、AstroBox 或 Nexio 手环管理工具将应用侧载推送至手环；
3. 手环进入应用列表即可打开「ClassFlow 课表」。
