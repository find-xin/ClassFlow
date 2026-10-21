#!/usr/bin/env python3
"""Prepare/validate a private OpenAI tunnel, keeping runtime credentials out of files and argv."""
import argparse
import getpass
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tunnel-id', required=True)
    parser.add_argument('--binary', default='/Applications/ClassFlow.app/Contents/MacOS/ClassFlow')
    parser.add_argument('--profile', default='classflow')
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'tunnel_[A-Za-z0-9_-]+', args.tunnel_id):
        parser.error('Invalid tunnel ID; paste the ID from OpenAI Platform tunnel settings.')
    client = shutil.which('tunnel-client') or str(Path.home() / '.local/bin/tunnel-client')
    binary = Path(args.binary).expanduser().resolve()
    if not Path(client).is_file() or not os.access(client, os.X_OK):
        parser.error('Install tunnel-client from https://github.com/openai/tunnel-client/releases/latest')
    if not binary.is_file() or not os.access(binary, os.X_OK):
        parser.error('Install the current ClassFlow.app first.')
    env = dict(os.environ)
    # Profiles contain only env:CONTROL_PLANE_API_KEY, never a literal credential.
    subprocess.run([client, 'init', '--sample', 'sample_mcp_stdio_local', '--profile', args.profile,
                    '--tunnel-id', args.tunnel_id, '--mcp-command', shlex.join([str(binary), '--mcp']),
                    '--health-listen-addr', '127.0.0.1:0'], check=True, env=env)
    if args.prepare_only:
        print('Profile prepared. Cloud connection is not yet verified.')
        return
    if not env.get('CONTROL_PLANE_API_KEY'):
        if not sys.stdin.isatty():
            parser.error('Run in a terminal to enter the runtime key securely, or provide CONTROL_PLANE_API_KEY in the environment.')
        env['CONTROL_PLANE_API_KEY'] = getpass.getpass('OpenAI tunnel runtime key (hidden; not saved): ')
    if not env['CONTROL_PLANE_API_KEY'].strip():
        parser.error('The runtime key cannot be empty.')
    subprocess.run([client, 'doctor', '--profile', args.profile, '--explain'], env=env, check=True)
    print('Starting tunnel. Keep this terminal open; Ctrl+C disconnects it.')
    subprocess.run([client, 'run', '--profile', args.profile], env=env, check=True)


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as error:
        print(f'Tunnel command failed (exit {error.returncode}). Connection is not verified.', file=sys.stderr)
        sys.exit(error.returncode)
    except KeyboardInterrupt:
        sys.exit(130)
