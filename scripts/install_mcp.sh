#!/usr/bin/env bash
# Configure the local Work/Codex plugin. ChatGPT cloud uses Secure MCP Tunnel instead.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CLASSFLOW_BIN="${CLASSFLOW_APP_BIN:-/Applications/ClassFlow.app/Contents/MacOS/ClassFlow}"
if [[ ! -x "$CLASSFLOW_BIN" ]]; then
    echo "Install the current ClassFlow.app first, or set CLASSFLOW_APP_BIN." >&2
    exit 1
fi
CODEX_BIN="$(command -v codex || true)"
if [[ -z "$CODEX_BIN" ]]; then
    echo "Codex CLI not found. Open ChatGPT Work/Codex and install its CLI before continuing." >&2
    exit 1
fi
MARKETPLACE_DIR="${HOME}/.codex/marketplaces/classflow-marketplace"
python3 - "$ROOT_DIR" "$MARKETPLACE_DIR" "$CLASSFLOW_BIN" <<'PY'
import json, pathlib, shutil, sys
source, target, binary = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3]
plugin = target / 'plugins/classflow'
shutil.copytree(source / 'plugins/classflow', plugin, dirs_exist_ok=True)
# Serialize paths; never interpolate user-controlled paths into shell/Python program text.
config = {'mcpServers': {'classflow': {'command': binary, 'args': ['--mcp']}}}
(plugin / '.mcp.json').write_text(json.dumps(config, ensure_ascii=False, indent=2) + '\n')
marketplace = target / '.agents/plugins/marketplace.json'
marketplace.parent.mkdir(parents=True, exist_ok=True)
marketplace.write_text(json.dumps({'name': 'classflow-marketplace',
    'interface': {'displayName': 'ClassFlow 本地插件库'},
    'plugins': [{'name': 'classflow', 'source': {'source': 'local', 'path': './plugins/classflow'},
                 'policy': {'installation': 'AVAILABLE', 'authentication': 'ON_INSTALL'}, 'category': 'Productivity'}]},
    ensure_ascii=False, indent=2) + '\n')
PY
"$CODEX_BIN" plugin marketplace add "$MARKETPLACE_DIR"
"$CODEX_BIN" plugin add classflow@classflow-marketplace
# Do not suppress errors, edit plugin caches, or lower approval settings.
echo "Local plugin registered. Open a new local Work/Codex chat and verify get_status."
echo "ChatGPT cloud chat is a separate connection; follow docs/CHATGPT_INTEGRATION.md."
