#!/usr/bin/env python3
"""Check the real stdio executable without invoking tools or changing user data."""
import argparse
import json
import selectors
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('binary')
args = parser.parse_args()
with subprocess.Popen([args.binary, '--mcp'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, bufsize=1) as process:
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    def request(identifier, method, params=None):
        process.stdin.write(json.dumps({'jsonrpc': '2.0', 'id': identifier, 'method': method, 'params': params or {}}) + '\n')
        process.stdin.flush()
        assert selector.select(10), f'Timed out waiting for {method}'
        result = json.loads(process.stdout.readline())
        assert result.get('id') == identifier, result
        return result
    init = request(1, 'initialize', {'protocolVersion': '2025-06-18', 'capabilities': {}, 'clientInfo': {'name': 'classflow-smoke', 'version': '1'}})
    assert init['result']['protocolVersion'] == '2025-06-18'
    process.stdin.write(json.dumps({'jsonrpc': '2.0', 'method': 'notifications/initialized'}) + '\n')
    process.stdin.flush()
    tools = request(2, 'tools/list')['result']['tools']
    names = {tool['name'] for tool in tools}
    assert len(names) == len(tools) == 28
    assert {'update_task', 'update_calendar_event', 'pause_focus_timer', 'resume_focus_timer', 'import_calendar_events'} <= names
    for tool in tools:
        assert tool['inputSchema']['type'] == 'object'
        assert isinstance(tool['annotations']['readOnlyHint'], bool)
    assert request(3, 'ping')['result'] == {}
    assert request(4, 'not-a-method')['error']['code'] == -32601
    process.stdin.close()
    assert process.wait(timeout=10) == 0
    print('PASS: initialize, notification, 28 tool schemas, annotations, ping, JSON-RPC error, clean EOF')
