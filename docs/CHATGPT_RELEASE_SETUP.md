# ClassFlow：ChatGPT / Work / Codex 接入

请先安装 Release 中的 macOS 版 ClassFlow.app，并放入 `/Applications`。

| 你使用的入口 | 需要下载什么 | 如何连接 |
| --- | --- | --- |
| 普通 ChatGPT 聊天 | macOS App；本说明 | 在 App 设置中连接 Secure MCP Tunnel，然后在 ChatGPT 添加同一隧道 |
| 本地 Work / Codex | macOS App；`ClassFlow-Local-Plugin-1.2.0.zip` | 解压后运行下面的本地安装脚本 |

插件版本 1.2.0 与 App 版本 1.2.0 分别编号。插件包包含本地插件、安装脚本、许可证和接入文档，不含 App、官方隧道客户端或任何人的密钥。

## 普通 ChatGPT 聊天

1. 从 [OpenAI 官方发布页](https://github.com/openai/tunnel-client/releases/latest) 安装适合 Mac 的 `tunnel-client`。例如放在 `~/.local/bin/tunnel-client`，确保文件可执行；也可在 ClassFlow 的高级选项中选择客户端文件。
2. 在 [Platform 隧道设置](https://platform.openai.com/settings/organization/tunnels) 创建自己的隧道，并关联使用的 ChatGPT 工作区。创建需要 Tunnels Read + Manage；专用运行密钥需要 Tunnels Read + Use。账户和工作区必须允许使用自定义 MCP。
3. 打开 ClassFlow → 课表设置 → **AI / ChatGPT**。填写 `tunnel_…` 格式的 **Tunnel ID** 和专用运行密钥，保存后点击「连接 ChatGPT」。密钥存入本机钥匙串；不要发给他人或放进 GitHub。
4. 等待显示「隧道已就绪」，按需勾选「打开 ClassFlow 时自动启用」。连接由 App 后台管理，不需常驻终端；退出 App、Mac 离线或休眠会中断连接。
5. 在 ChatGPT 的插件入口添加自定义 MCP 服务器，Connection 选择 **Tunnel**，选择或填入同一个 Tunnel ID，按页面要求完成认证和授权。
6. 在聊天中选用 ClassFlow，先询问「今天有什么课」，确认返回真实课表，再使用添加、修改等操作。

本地插件 ZIP 适用于本地执行入口；普通云端聊天使用上述隧道路径。GitHub 发布插件包不会自动将插件上架到 ChatGPT 官方公共目录。

## 本地 Work / Codex

先安装并登录支持插件的本地 Work / Codex，并安装 Python 3。安装脚本会自动查找终端 PATH 和 ChatGPT App 内置的 `codex` CLI；特殊安装路径可通过 `CLASSFLOW_CODEX_BIN` 指定。解压插件 ZIP 后，在 `ClassFlow-Local-Plugin` 文件夹内运行：

```bash
bash scripts/install_mcp.sh
```

脚本会注册本地插件库和 ClassFlow 插件。新建本地会话并选用 ClassFlow，调用 `get_status` 或查询今天的课程即可验证。安装时可能需要终端；日常调用不需要额外常驻终端。不要同时保留旧版同名 MCP 连接。

Claude 等其他支持 stdio MCP 的客户端可自行配置 `/Applications/ClassFlow.app/Contents/MacOS/ClassFlow`，参数为 `["--mcp"]`；上述脚本只安装 Work / Codex 插件。

详细工具列表和排错见 [项目集成指南](https://github.com/find-xin/ClassFlow/blob/main/docs/CHATGPT_INTEGRATION.md)。官方连接方式见 [OpenAI Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels)。


### 编辑接口 v1.2 更新

使用包含 28 项工具的 Mac App 后，在已有 ChatGPT ClassFlow 连接中刷新工具列表（本地客户端重连 MCP）。应能看到 `get_editing_context`、`manage_course_schedule`、`batch_edit_items`、`reset_focus_timer`。只上传新的本地插件 ZIP 不会自动替换云端连接缓存。无需重新填写隧道 Token；凭据仍保存在系统钥匙串。完整字段与作用域说明见 `docs/CHATGPT_INTEGRATION.md`。Mac 提供执行器；手机经已配置的 BYOC 接收数据，不会接收 Mac 的运行中计时器。


### v1.2.0 修订包（macOS build 4）

修复 GUI 隧道未沿用系统静态 HTTPS/SOCKS 代理造成的连接超时；状态同时检查云端轮询。连接时读取系统代理，修改代理后在 App 停止并重连；PAC 自动代理脚本暂不解析。

批量工具仍为 `batch_edit_items`：operations 上限1000；selection按条件统一修改/删除/完成上限5000；日程导入上限2000。删除全部日程可一次提交 selection={kind:events,all:true}, action=delete，无需逐条查询和删除。更新 App 后请在 ChatGPT 原连接刷新工具，再新建聊天，以便加载新增参数。App不能修改ChatGPT每轮工具调用配额。
