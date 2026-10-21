#!/usr/bin/env bash
# ClassFlow MCP Auto-Installer for ChatGPT Desktop and Claude Desktop on macOS
set -euo pipefail

echo "============================================================"
echo " ClassFlow Native MCP Server Installer"
echo " Configuring ChatGPT Desktop and Claude Desktop on macOS"
echo "============================================================"

APP_PATH="/Applications/ClassFlow.app"
MCP_BIN="${APP_PATH}/Contents/MacOS/classflow-mcp"
LOCAL_BUILD="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/apps/macos/.build/release/ClassFlow"

TARGET_BIN=""
if [[ -x "${MCP_BIN}" ]]; then
    TARGET_BIN="${MCP_BIN}"
    echo "✔ Found ClassFlow app bundle at ${APP_PATH}"
elif [[ -x "${APP_PATH}/Contents/MacOS/ClassFlow" ]]; then
    TARGET_BIN="${APP_PATH}/Contents/MacOS/ClassFlow"
    echo "✔ Found ClassFlow binary at ${TARGET_BIN}"
elif [[ -x "${LOCAL_BUILD}" ]]; then
    TARGET_BIN="${LOCAL_BUILD}"
    echo "✔ Found local release build at ${LOCAL_BUILD}"
else
    echo "⚠ ClassFlow not found in /Applications. Searching local debug build..."
    DEBUG_BUILD="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/apps/macos/.build/arm64-apple-macosx/debug/ClassFlow"
    if [[ -x "${DEBUG_BUILD}" ]]; then
        TARGET_BIN="${DEBUG_BUILD}"
        echo "✔ Found local debug build at ${TARGET_BIN}"
    else
        echo "Error: ClassFlow binary not found. Please install ClassFlow.app or run swift build first."
        exit 1
    fi
fi

# 1. Create CLI Symlink
CLI_DIR="/usr/local/bin"
if [[ -w "${CLI_DIR}" ]]; then
    ln -sf "${TARGET_BIN}" "${CLI_DIR}/classflow-mcp"
    echo "✔ Created symlink: ${CLI_DIR}/classflow-mcp -> ${TARGET_BIN}"
else
    USER_BIN="${HOME}/.local/bin"
    mkdir -p "${USER_BIN}"
    ln -sf "${TARGET_BIN}" "${USER_BIN}/classflow-mcp"
    echo "✔ Created symlink: ${USER_BIN}/classflow-mcp -> ${TARGET_BIN}"
fi

# 2. Configure Claude Desktop
CLAUDE_CONFIG_DIR="${HOME}/Library/Application Support/Claude"
CLAUDE_CONFIG="${CLAUDE_CONFIG_DIR}/claude_desktop_config.json"
mkdir -p "${CLAUDE_CONFIG_DIR}"

python3 -c "
import json, os

config_path = os.path.expanduser('${CLAUDE_CONFIG}')
target_bin = '${TARGET_BIN}'

data = {}
if os.path.exists(config_path):
    try:
        with open(config_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except Exception:
        data = {}

if 'mcpServers' not in data:
    data['mcpServers'] = {}

data['mcpServers']['classflow'] = {
    'command': target_bin,
    'args': []
}

with open(config_path, 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2, ensure_ascii=False)
"
echo "✔ Successfully updated Claude Desktop configuration: ${CLAUDE_CONFIG}"

# 3. Configure ChatGPT Desktop
CHATGPT_DIR="${HOME}/Library/Application Support/OpenAI/ChatGPT"
mkdir -p "${CHATGPT_DIR}"
CHATGPT_CONFIG="${CHATGPT_DIR}/mcp.json"

python3 -c "
import json, os

config_path = os.path.expanduser('${CHATGPT_CONFIG}')
target_bin = '${TARGET_BIN}'

data = {}
if os.path.exists(config_path):
    try:
        with open(config_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except Exception:
        data = {}

if 'mcpServers' not in data:
    data['mcpServers'] = {}

data['mcpServers']['classflow'] = {
    'command': target_bin,
    'args': []
}

with open(config_path, 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2, ensure_ascii=False)
"
echo "✔ Successfully updated ChatGPT Desktop JSON configuration: ${CHATGPT_CONFIG}"

# 4. Configure ChatGPT Desktop Codex Plugin Engine
CODEX_CLI="/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex"
if [[ -x "${CODEX_CLI}" ]]; then
    echo "✔ Found ChatGPT Codex Engine CLI at ${CODEX_CLI}"
    "${CODEX_CLI}" mcp add classflow -- "${TARGET_BIN}" 2>/dev/null || true

    MARKETPLACE_DIR="${HOME}/.codex/marketplaces/classflow-marketplace"
    mkdir -p "${MARKETPLACE_DIR}/.agents/plugins"
    mkdir -p "${MARKETPLACE_DIR}/plugins/classflow/.codex-plugin"
    mkdir -p "${MARKETPLACE_DIR}/plugins/classflow/skills/classflow"

    cat << 'EOF' > "${MARKETPLACE_DIR}/.agents/plugins/marketplace.json"
{
  "name": "classflow-marketplace",
  "interface": {
    "displayName": "ClassFlow 本地插件库"
  },
  "plugins": [
    {
      "name": "classflow",
      "source": {
        "source": "local",
        "path": "./plugins/classflow"
      },
      "policy": {
        "installation": "AVAILABLE",
        "authentication": "ON_INSTALL"
      },
      "category": "Productivity"
    }
  ]
}
EOF

    cat << 'EOF' > "${MARKETPLACE_DIR}/plugins/classflow/.codex-plugin/plugin.json"
{
  "name": "classflow",
  "version": "1.0.0",
  "description": "ClassFlow 课程表与日程专注管理：全权接管课表、日程、番茄待办、专注时钟与云端同步",
  "author": {
    "name": "ClassFlow"
  },
  "license": "Apache-2.0",
  "mcpServers": "./.mcp.json",
  "skills": "./skills/",
  "interface": {
    "displayName": "ClassFlow 课程与日程",
    "shortDescription": "课程、日程、待办与专注全权接管",
    "longDescription": "ClassFlow 原生智能助手插件，让 ChatGPT 在对话中直接查询与管理课表、安排日程、添加待办任务、开启番茄钟专注计时并执行云端同步。",
    "developerName": "ClassFlow",
    "category": "Productivity",
    "capabilities": [
      "Read",
      "Write",
      "Interactive"
    ],
    "defaultPrompt": [
      "今天下午我有什么课？",
      "帮我添加一门机器学习课，周三 3-4 节，理科楼 201，张教授，3 学分",
      "开始 25 分钟的专注计时，任务是完成操作系统实验"
    ],
    "brandColor": "#FF5A5F"
  }
}
EOF

    cat << EOF > "${MARKETPLACE_DIR}/plugins/classflow/.mcp.json"
{
  "mcpServers": {
    "classflow": {
      "command": "${TARGET_BIN}",
      "args": [],
      "enabled": true,
      "default_tools_approval_mode": "approve",
      "enabled_tools": [
        "get_status",
        "query_timetable",
        "add_course",
        "update_course",
        "delete_course",
        "list_courses",
        "list_calendar_events",
        "add_calendar_event",
        "delete_calendar_event",
        "list_tasks",
        "add_task",
        "complete_task",
        "delete_task",
        "start_focus_timer",
        "stop_focus_timer",
        "get_focus_statistics",
        "trigger_cloud_sync",
        "switch_semester_or_week",
        "open_app"
      ],
      "tools": {
        "query_timetable": { "approval_mode": "none" },
        "get_status": { "approval_mode": "none" },
        "list_courses": { "approval_mode": "none" },
        "list_calendar_events": { "approval_mode": "none" },
        "list_tasks": { "approval_mode": "none" },
        "get_focus_statistics": { "approval_mode": "none" }
      }
    }
  }
}
EOF

    cat << 'EOF' > "${MARKETPLACE_DIR}/plugins/classflow/skills/classflow/SKILL.md"
---
name: classflow
description: "ClassFlow 课程表与日程专注管理：全权接管课表查询、课程增删改查、日程安排、番茄待办、专注时钟计时与云端同步。当用户询问今天有什么课、添加课程、修改课程、查询日程、创建待办、开始专注、云同步时，必须调用此插件工具。"
---

# ClassFlow 课程与日程管理助手

本插件通过本地 MCP 服务（或 `classflow-mcp` CLI）直接连接本地 ClassFlow 应用数据库，支持双向读写与 macOS 客户端实时联动。

## 可用工具列表及参数说明

### 1. 课表查询与课程管理
- `query_timetable(week: Int?, day_of_week: Int?)`:
  - 查询指定周次与星期几的课程列表。`day_of_week` 为 1-7（1代表周一，7代表周日）。
  - 返回当天的所有课程数组，包含：`title`（课程名）、`start_section`（开始节次）、`end_section`（结束节次）、`start_time`（开始时间）、`end_time`（结束时间）、`location`（教室）、`teacher`（任课老师）、`credit`（学分）、`notes`（备注）、`color`（颜色）。
  - **重要用法**：当用户问“今天下午有什么课”时，调用 `query_timetable`（传入今日的星期几），然后根据 `start_time`（>= 12:00 或第 5 节及之后）筛选出下午的课程并回答用户！
- `get_status()`:
  - 获取当前学期名称、当前周次、今日日期（如 2026-10-21）、今日星期几、课程总数与未完成待办数。
- `add_course(...)`:
  - 必填：`name`（课程名）、`day_of_week`（1-7）、`start_section`（如 3）。
  - 可选：`end_section`（如 4）、`classroom`（地点）、`teacher`（教师）、`credit`（学分）、`color`（十六进制如 #10B981）、`notes`（备注）、`start_week`（起始周）、`end_week`（结束周）、`week_pattern`（"all"、"odd"、"even"）。
- `update_course(name: String, new_name: String?, teacher: String?, credit: Double?, color: String?, classroom: String?, notes: String?)`:
  - 按课程名称模糊或精确匹配并更新属性。
- `delete_course(name: String, scope: String?)`:
  - `scope` 可为 "all"（彻底删除整门课，默认）。
- `list_courses()`:
  - 汇总查看当前学期所有课程列表。

### 2. 日程管理 (通用活动/考试/会议)
- `list_calendar_events(date: String?)`:
  - 查询日程列表，可按 `date`（YYYY-MM-DD）过滤。
- `add_calendar_event(title: String, date: String, start_time: String?, end_time: String?, location: String?, color: String?, notes: String?, is_all_day: Bool?)`:
  - 添加日程事件。
- `delete_calendar_event(title: String)`:
  - 按标题删除日程。

### 3. 番茄待办与专注时钟
- `list_tasks(filter: String?)`:
  - 查询待办事项，`filter` 可为 "pending"（进行中）、"completed"（已完成）或 "all"。
- `add_task(title: String, estimated_pomodoros: Int?, priority: String?, due_date: String?)`:
  - 添加待办，`priority` 可为 "high"、"medium"、"low"。
- `complete_task(title: String, is_completed: Bool?)`:
  - 标记待办已完成或未完成。
- `delete_task(title: String)`:
  - 删除待办任务。
- `start_focus_timer(task_title: String?, duration_minutes: Int?)`:
  - 开启专注倒计时（默认 25 分钟），**会自动联动唤醒本地 ClassFlow 界面切换至专注时钟并开始倒计时**。
- `stop_focus_timer(task_title: String?, actual_minutes: Int?)`:
  - 结束专注并将时长记入历史统计。
- `get_focus_statistics()`:
  - 获取今日专注时长与累计番茄数。

### 4. 系统与云端同步
- `trigger_cloud_sync()`:
  - 触发本地配置的 WebDAV / S3 / 七牛云云端双向同步。
- `switch_semester_or_week(week: Int?, semester_name: String?)`:
  - 切换当前查看的周次。
- `open_app(tab: String?)`:
  - 呼出 ClassFlow 应用并定位到指定标签页（"courses"、"schedule"、"study"、"settings"）。

## 规则与行为指引
1. **优先调用工具**：当用户提出与课表、日程、待办、专注、同步相关的请求时，**必须直接调用对应的工具函数**，而不要回复“我无法读取数据”或要求用户提供截图。
2. **回答格式友好**：
   - 查询今天下午课程时，如果查询到课程，清晰列出课程名、时间、节次、教室与教师。
   - 如果今天下午没有课，则温馨提示用户下午没有安排课程。
EOF

    "${CODEX_CLI}" plugin marketplace add "${MARKETPLACE_DIR}" 2>/dev/null || true
    "${CODEX_CLI}" plugin add classflow@classflow-marketplace 2>/dev/null || true

    # Mirror into plugin cache
    CACHE_DIR="${HOME}/.codex/plugins/cache/classflow-marketplace/classflow/1.0.0"
    if [[ -d "${CACHE_DIR}" ]]; then
        cp -R "${MARKETPLACE_DIR}/plugins/classflow/.codex-plugin" "${CACHE_DIR}/"
        cp "${MARKETPLACE_DIR}/plugins/classflow/.mcp.json" "${CACHE_DIR}/.mcp.json"
        cp -R "${MARKETPLACE_DIR}/plugins/classflow/skills" "${CACHE_DIR}/"
    fi

    # Ensure config.toml has default_tools_approval_mode = "approve" for classflow
    CODEX_CONFIG="${HOME}/.codex/config.toml"
    if [[ -f "${CODEX_CONFIG}" ]]; then
        python3 -c "
with open('${CODEX_CONFIG}', 'r', encoding='utf-8') as f:
    content = f.read()
if '[mcp_servers.classflow]' in content and 'default_tools_approval_mode' not in content:
    content = content.replace('[mcp_servers.classflow]', '[mcp_servers.classflow]\ndefault_tools_approval_mode = \"approve\"')
    with open('${CODEX_CONFIG}', 'w', encoding='utf-8') as f:
        f.write(content)
" 2>/dev/null || true
    fi

    echo "✔ Successfully registered and enabled ClassFlow in ChatGPT Plugins!"
fi

echo ""
echo "============================================================"
echo " 🎉 MCP Installation Complete!"
echo " ClassFlow is now directly accessible in ChatGPT & Claude!"
echo ""
echo " Quick Test in Terminal:"
echo "   classflow-mcp tools"
echo "   classflow-mcp status"
echo ""
echo " In ChatGPT / Claude Desktop:"
echo "   1. Restart the desktop client."
echo "   2. Ask: '今天下午我有什么课？'"
echo "   3. Ask: '帮我加一门机器学习课，周三3-4节，理科楼201，张老师，3学分'"
echo "============================================================"
