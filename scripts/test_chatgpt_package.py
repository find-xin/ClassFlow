#!/usr/bin/env python3
"""Exercise the actual release kit and installer without touching the user's client."""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from package_chatgpt import package

ROOT = Path(__file__).resolve().parent.parent


class ChatGPTReleaseTests(unittest.TestCase):
    def test_reproducible_archive_contents_and_permissions(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            artifacts = package(ROOT, output)
            archive = artifacts[0]
            first = archive.read_bytes()
            package(ROOT, output)
            self.assertEqual(first, archive.read_bytes())
            with ZipFile(archive) as bundle:
                self.assertIsNone(bundle.testzip())
                names = bundle.namelist()
                self.assertEqual(len(names), 8)
                self.assertIn('ClassFlow-Local-Plugin/plugins/classflow/.mcp.json', names)
                self.assertIn('ClassFlow-Local-Plugin/plugins/classflow/.codex-plugin/plugin.json', names)
                wrapper = bundle.getinfo('ClassFlow-Local-Plugin/plugins/classflow/scripts/classflow-mcp')
                self.assertEqual((wrapper.external_attr >> 16) & 0o777, 0o755)
                self.assertTrue(all('..' not in Path(name).parts for name in names))
            for path in artifacts[:2]:
                digest, filename = path.with_name(path.name + '.sha256').read_text().strip().split('  ')
                self.assertEqual(digest, hashlib.sha256(path.read_bytes()).hexdigest())
                self.assertEqual(filename, path.name)

    def test_extracted_installer_works_without_source_checkout(self):
        with tempfile.TemporaryDirectory(prefix='classflow release ') as folder:
            work = Path(folder)
            archive = package(ROOT, work / 'output')[0]
            with ZipFile(archive) as bundle:
                bundle.extractall(work / 'extracted')
            kit = work / 'extracted/ClassFlow-Local-Plugin'
            home = work / 'isolated-home'
            home.mkdir()
            commands = work / 'bin'
            commands.mkdir()
            codex = commands / 'codex'
            codex.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$CLASSFLOW_TEST_CALLS"\n')
            codex.chmod(0o755)
            calls = work / 'calls.txt'
            env = dict(os.environ, HOME=str(home), PATH=str(commands) + os.pathsep + os.environ['PATH'],
                       CLASSFLOW_APP_BIN=sys.executable, CLASSFLOW_TEST_CALLS=str(calls))
            env.pop('CLASSFLOW_CODEX_BIN', None)
            result = subprocess.run(['bash', str(kit / 'scripts/install_mcp.sh')], cwd=work,
                                    env=env, capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stderr)
            marketplace = home / '.codex/marketplaces/classflow-marketplace'
            config = json.loads((marketplace / 'plugins/classflow/.mcp.json').read_text())
            self.assertEqual(config['mcpServers']['classflow'], {'command': sys.executable, 'args': ['--mcp']})
            self.assertEqual(calls.read_text().splitlines(), [
                'plugin marketplace add ' + str(marketplace), 'plugin add classflow@classflow-marketplace'])
            self.assertTrue((marketplace / '.agents/plugins/marketplace.json').is_file())

    def test_installer_discovers_cli_outside_terminal_path(self):
        for mode in ('explicit', 'desktop_bundle', 'invalid_explicit'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory(prefix='classflow cli ') as folder:
                work = Path(folder)
                home = work / 'home'
                home.mkdir()
                if mode == 'desktop_bundle':
                    cli = home / 'Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex'
                else:
                    cli = work / 'outside path/codex'
                cli.parent.mkdir(parents=True)
                cli.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$CLASSFLOW_TEST_CALLS"\n')
                cli.chmod(0o755)
                calls = work / 'calls.txt'
                env = dict(os.environ, HOME=str(home), PATH='/usr/bin:/bin',
                           CLASSFLOW_APP_BIN=sys.executable, CLASSFLOW_TEST_CALLS=str(calls))
                env.pop('CLASSFLOW_CODEX_BIN', None)
                if mode != 'desktop_bundle':
                    env['CLASSFLOW_CODEX_BIN'] = str(cli if mode == 'explicit' else work / 'missing')
                result = subprocess.run(['/bin/bash', str(ROOT / 'scripts/install_mcp.sh')],
                                        env=env, capture_output=True, text=True, timeout=15)
                if mode == 'invalid_explicit':
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn('CLASSFLOW_CODEX_BIN', result.stderr)
                    self.assertFalse(calls.exists())
                    self.assertFalse((home / '.codex').exists())
                else:
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertEqual(len(calls.read_text().splitlines()), 2)
                    self.assertTrue((home / '.codex/marketplaces/classflow-marketplace/plugins/classflow/.mcp.json').exists())

    def test_missing_required_file_fails_without_partial_artifact(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = root / 'plugins/classflow/.codex-plugin/plugin.json'
            manifest.parent.mkdir(parents=True)
            manifest.write_text('{"version":"1.1.0"}')
            output = root / 'output'
            with self.assertRaises(ValueError):
                package(root, output)
            self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
