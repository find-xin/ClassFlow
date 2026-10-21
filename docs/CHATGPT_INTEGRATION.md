# ClassFlow 与 ChatGPT 接入

更新时间：2026-10-21。目标为 macOS 的本地 Work/Codex 和普通 ChatGPT 云端聊天。

## 两条连接路径

| 入口 | 连接方式 | 要求 |
| --- | --- | --- |
| 本地 Work / Codex | 本地插件 → stdio MCP → ClassFlow App | 当前版 ClassFlow.app、支持本地执行的会话 |
| 普通 ChatGPT 聊天 | Secure MCP Tunnel → 本机 stdio MCP → ClassFlow App | 隧道、运行凭据、对应组织与 ChatGPT 工作区权限、本机隧道进程在线 |

模型是否在云端与工具在哪里执行是两个不同的问题。本地插件不能仅通过名称、SKILL.md 或任意 `ChatGPT/mcp.json` 配置文件，使云端会话获得本机执行能力。

官方参考：
- [Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels)
- [ChatGPT 自定义 MCP](https://developers.openai.com/api/docs/guides/custom-mcp-server)
- [本地桌面插件的适用范围](https://developers.openai.com/plugins/deploy/desktop-only)

本地插件在公开目录发布需要官方相应资格；本仓库安装脚本用于用户自己的本地安装。
隧道用于私有连接；不把本地数据库或云存储密钥搬到外部服务器。工具查询和操作的参数、结果会进入所连接的 ChatGPT 会话。

## 安装本地插件

1. 下载并安装 Release 中的 macOS 版 ClassFlow.app，打开一次。
2. 下载 `ClassFlow-Local-Plugin-1.1.0.zip`，解压后在其 `ClassFlow-Local-Plugin` 文件夹内运行 `bash scripts/install_mcp.sh`；源码仓库内也可直接运行同一脚本。需要 Python 3 与支持插件的 `codex` CLI。
3. 新建本地 Work / Codex 会话并选用 ClassFlow 插件。
4. 调用 `get_status`，确认返回的是自己的学期和日期；再查询课表。

脚本通过 Codex CLI 注册插件，不吞掉错误，不手写插件缓存，不调整用户审批策略。
所有启动命令显式带 `--mcp`，不依赖二进制文件名识别。不要同时保留多个版本的 ClassFlow MCP 连接。
若之前通过 `codex mcp add classflow` 单独注册过同名服务，可在客户端的 MCP 设置中停用旧连接，保留插件连接。

## 连接普通 ChatGPT 聊天

1. 从 [官方发布页](https://github.com/openai/tunnel-client/releases/latest) 安装适合本机的 `tunnel-client`，执行 `tunnel-client help quickstart` 核对当前 CLI。
2. 在 [Platform 隧道设置](https://platform.openai.com/settings/organization/tunnels) 创建私有隧道，关联实际使用的 Platform 组织和 ChatGPT 工作区。
3. 为隧道客户端准备专用运行凭据，赋予 Tunnels Read + Use；创建或编辑隧道另需 Tunnels Read + Manage。不要把管理员密钥提供给常驻运行进程。
4. 打开 ClassFlow → 设置 → AI / ChatGPT →「ChatGPT 聊天接管」，填写 Tunnel ID 和专用运行密钥，点击「保存连接配置」。已安装客户端路径会自动检测，也可在高级选项中选择。旧终端 profile 中的 Tunnel ID 会自动填入；密钥需要在 App 内保存一次，不读取其他进程的环境。
5. 如果旧终端仍在运行同一隧道，先在该终端按 Ctrl+C 停止一次，再点击 App 内「连接 ChatGPT」。显示「隧道已就绪」后，客户端已通过本机 `/readyz` 检查。可开启「打开 ClassFlow 时自动启用」，默认关闭。
6. 在 ChatGPT 插件中添加自定义 MCP，Connection 选择 Tunnel，再选择同一个隧道；已有插件通常可继续使用。网页设置由用户完成。在**普通聊天会话**中调用 `get_status` 和 `query_timetable`，拿到真实工具结果才算该入口通过。

### App 后台连接的生命周期

- 无需终端窗口。App 持有官方客户端进程，提供连接、停止、状态及分类错误提示；关闭设置窗口不停止连接，退出 App 则停止其拥有的客户端。自动启用随 App 启动生效，不是系统登录启动项。
- Mac 休眠、离线或 App 退出后，云端无法操作该 Mac。网络恢复时由官方客户端尝试重连；进程已退出则需要重新连接。
- Tunnel ID、客户端路径和启动开关仅存本机 UserDefaults，不进入数据库、BYOC 同步或备份；运行密钥仅存 macOS Keychain，在启动时进入客户端环境，不进入参数、配置文件或日志。
- App 使用私有 `~/Library/Application Support/ClassFlow/Tunnel/profiles/classflow-app.yaml`，配置只引用 `CONTROL_PLANE_API_KEY`。原终端 profile 保留。进程锁防止多个 App 副本重复管理同一运行目录。
- 健康检查只允许本机 `127.0.0.1` HTTP 端口；客户端原始输出仅排空并分类，不写入日志或展示可能包含凭据的文本。

### 可选：手动终端方式

高级排错时可运行 `python3 scripts/connect_chatgpt.py --tunnel-id <隧道 ID>`。脚本隐藏读取运行密钥，仅放入子进程环境；此方式必须保持终端进程运行，不要与 App 同时连接同一隧道。`--prepare-only` 不连接，不能视为验收通过。

如果 `init` 报已有配置，不自动覆盖；先检查已有 profile。已有正确 profile 可用 `tunnel-client doctor --profile classflow --explain` 和 `tunnel-client run --profile classflow` 复用，运行密钥仍来自环境引用。账户或工作区功能不可用时记录 `[BLOCKED]` 及实际错误，不伪装成已连接。

## 23 个工具的覆盖范围

- 状态和课表：`get_status`、`query_timetable`、`list_courses`。
- 课程：`add_course`、`update_course`、`delete_course`；属性包括教师、学分、颜色、备注、地点。修改星期、节次、周次时，多课时课程必须提供 `schedule_id`。
- 日程：`list_calendar_events`、`add_calendar_event`、`update_calendar_event`、`delete_calendar_event`；支持标题、日期、时间、全天、地点、颜色、备注。
- 待办：`list_tasks`、`add_task`、`update_task`、`complete_task`、`delete_task`；支持备注、预估番茄、截止日期和优先级。待办颜色由优先级决定，现有领域模型没有独立颜色字段。
- 专注：`start_focus_timer`、`pause_focus_timer`、`resume_focus_timer`、`stop_focus_timer`、`get_focus_statistics`。按实际经过时间记账，暂停时间排除；未满一分钟不编造一分钟记录。重复结束返回错误，避免重复记账。
- App 与同步：`open_app`、`switch_semester_or_week`、`trigger_cloud_sync`。云同步使用 App 内已有 BYOC 配置，不接受或返回密钥。

这些工具不代表 macOS 的所有系统功能或 ClassFlow 所有设置页面均有接口。学期创建、备份导入导出、系统日历授权等需要另行定义明确工具，不应声称已覆盖。

## 执行与数据规则

- stdio MCP 使用 JSON-RPC；常规输出仅包含协议数据。
- App 内的主线程执行课程、日程、待办和专注操作。stdio 进程通过同用户私有 mailbox 请求操作，等待结果确认。
- mailbox 位于 Application Support/ClassFlow/automation，目录权限 0700。通过执行器 PID 标识判断图形 App 是否在线，并在启动锁内冷启动，避免把 stdio 进程或挂载的旧 App 当成执行器。超期请求不再执行；请求 UUID 防止同一请求重复执行。超时后先查询结果，不自动重试有副作用的请求。
- 写操作完成后 App 重新载入，记录同步操作与序列号，再返回确认。发生损坏的数据库不允许自动化使用样例数据覆盖。
- 专注使用独立本地 `focusSession`，不复用课表配置 ID；UI 与 MCP 共用会话。该运行状态不进入 BYOC 同步或备份包。
- 密钥存于 Keychain；旧 UserDefaults 凭据仅在 Keychain 保存成功后移除。两种执行入口共用 App 的偏好域。
- 隧道只承担传输，仍须在 ChatGPT 按实际账户权限完成认证与授权。

## 验收与状态

运行：

```bash
swift test --package-path apps/macos
python3 scripts/test_mcp_transport.py apps/macos/.build/debug/ClassFlow
```

自动化用临时数据库，禁止在真实课表中通过随意新增、删除课程完成测试。

分别记录：
1. 本地处理函数、数据兼容和 App 执行器测试。
2. 真实 stdio 初始化、工具发现、协议错误与 EOF。
3. 安装后的 App 通过跨进程 MCP 返回真实只读结果。
4. 本地 Work 会话实际工具调用。
5. 隧道 doctor / readiness。
6. 普通 ChatGPT 聊天实际工具调用。

后两步依赖账户授权和运行凭据，未实际通过前必须标记 `[UNVERIFIED]`，不能以第 1–3 步代替。

### 本机验收记录（2026-10-21）

- `[VERIFIED]` macOS 自动化与原有界面模型测试；核心引擎 57 项测试。
- `[VERIFIED]` 真正的 stdio 进程握手、23 项工具发现、错误响应与 EOF。
- `[VERIFIED]` 独立签名的 App 验证副本成功冷启动，显示实际课表，并通过 mailbox 返回实际学期、周次和课程数量。
- `[VERIFIED]` MCP 启动真实 UI 的 25 分钟倒计时，暂停、继续及结束生效；短于一分钟的测试未生成虚假专注记录。
- 修复原生进程用自己的 bundle ID 创建 UserDefaults suite 返回 nil 时的启动崩溃；GUI 使用 `.standard`，伴随进程显式打开共享域，失败时安全回退。
- `[UNVERIFIED]` 普通 ChatGPT 云端会话：仍需最终隧道授权、运行凭据及目标会话实际调用。

开发构建的签名变化后，macOS 可能弹出 ClassFlow 钥匙串访问授权。用户需在系统窗口亲自核对并允许；不要把电脑密码或任何凭据发送到聊天。首次授权期间启动就绪检查可能超时，授权完成后再做只读状态查询。

- `[VERIFIED]` 最终 Release 已安装到 `/Applications/ClassFlow.app`，系统钥匙串授权后正常显示实际课表，并通过安装版跨进程状态查询。macOS 测试共 44 项通过，核心引擎 57 项通过。

### 原生隧道设置（2026-10-21）

- `[VERIFIED]` macOS 共 56 项测试通过，其中新增 12 项连接管理测试：安全保存、启停、自动启动、重复实例、取消竞态、健康检查及真实子进程清理。
- `[VERIFIED]` 原生 App 验证副本正常打开课表及连接设置；正确导入已有 Tunnel ID，默认关闭自动启动，缺少密钥时显示明确错误。官方 tunnel-client v0.0.16 实际生成私有 profile 成功，保留环境变量凭据引用及随机 loopback 健康端口；真实 stdio 23 项工具协议检查通过。
- `[UNVERIFIED]` App 托管连接的最终云端调用：需要用户将专用运行密钥保存到本机钥匙串，并在目标聊天实际调用；旧终端连接成功不替代此次验收。

- `[VERIFIED]` 含原生隧道设置的 Release 已安装至 `/Applications/ClassFlow.app`，签名及安装版 23 项 stdio 工具检查通过。安装版首次 GUI 启动曾等待已有云同步凭据的 macOS 钥匙串授权；堆栈位于 `SecItemCopyMatching`，不是启动崩溃。用户授权后已验证实际课表正常打开，AI / ChatGPT 设置显示安装版路径、已有 Tunnel ID、默认关闭的自动启用开关。运行密钥尚未保存；App 托管云端调用仍需单独验收。

- `[VERIFIED]` 用户保存运行密钥并开启自动启用后，安装版设置实际显示 App 管理的隧道已就绪；这验证了 App 内后台连接，普通云端聊天的实际工具调用仍为独立验收项。

## Release 附件打包

`python3 scripts/package_chatgpt.py` 从明确文件清单生成本地插件 ZIP、独立接入说明及各自的 SHA-256 文件，不读取用户配置、隧道 profile 或钥匙串。隐藏的插件 manifest 与 `.mcp.json` 包含在 ZIP 中；无需克隆源码仓库即可运行其中的安装脚本。普通 ChatGPT 聊天使用 App 中的隧道连接，本地插件附件不表示已上架官方公共目录。简明用户说明见 [CHATGPT_RELEASE_SETUP.md](CHATGPT_RELEASE_SETUP.md)。
