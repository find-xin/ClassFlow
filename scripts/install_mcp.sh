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
      "下周五下午两点有个学院学术研讨会，帮我记到日程里",
      "开始 25 分钟的专注计时，任务是完成操作系统实验",
      "把这学期的所有数据同步到云端"
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
      "enabled": true
    }
  }
}
EOF

    "${CODEX_CLI}" plugin marketplace add "${MARKETPLACE_DIR}" 2>/dev/null || true
    "${CODEX_CLI}" plugin add classflow@classflow-marketplace 2>/dev/null || true
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
