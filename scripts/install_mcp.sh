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
echo "✔ Successfully updated ChatGPT Desktop configuration: ${CHATGPT_CONFIG}"

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
