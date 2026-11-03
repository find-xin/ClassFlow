# 新 AI 对话接手提示词

复制下面整段到新对话，然后填写最后一行的任务。

```text
你正在接手 ClassFlow（https://github.com/find-xin/ClassFlow），本机仓库为 /Users/xin/Desktop/Schedule。先读 AGENTS.md、docs/ARCHITECTURE.md 和 docs/PROJECT_HANDOVER.md 的最新几节，再按本轮任务查阅相关文件。涉及 ChatGPT/MCP 时读 docs/CHATGPT_INTEGRATION.md；涉及重复日程时读 docs/RECURRING_EVENT_CLEANUP.md；涉及 Android 小组件时读 docs/ANDROID_WIDGET_LAYOUT.md。

以 git status、当前源码和实际测试为准。macOS 是 SwiftUI，Android 是 Kotlin/Compose；当前业务持久化仍是本地 JSON，云同步采用用户自配存储。保持课程和通用日程领域隔离，保留 UUID，不在仓库、日志或回答中暴露凭据。

最近的改动：重复日程单次取消写入系列的永久排除日期；历史 CANCEL 通过只读预览、确认、备份和原子提交清理，保留课程及调课记录。macOS MCP 有 30 项工具，Android 独立执行器有 6 项工具。Codex 的本地 stdio 与 macOS ChatGPT 桌面应用的普通 Chat 模式是不同入口；已验证本地工具发现，普通 Chat 是否发现新工具仍未验收。不要把连通性或本地工具发现当作普通 Chat 验收。

最近本地包是 macOS v1.2.0 build19 和 Android v1.2.0 build21；Android 工具栏在日期右侧。源码推送不等同于发布新版 GitHub Release，核对实际附件版本。每次修改应用后默认跑相关验证、递增受影响平台的构建号、生成安装包并校验签名及 SHA256，将包放到 dist/ 并附链接；macOS 使用 CLASSFLOW_SKIP_INSTALL=1。除非我明确要求，不覆盖安装、不发布、不修改真实课表数据。用 [VERIFIED]、[UNVERIFIED]、[BLOCKED] 区分实际结果。

本轮任务：<填入要做的事>
```
