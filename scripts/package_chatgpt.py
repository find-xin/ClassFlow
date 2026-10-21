#!/usr/bin/env python3
"""Build the portable local-plugin release kit from an explicit file allowlist."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

PLUGIN_FILES = (
    '.codex-plugin/plugin.json', '.mcp.json',
    'scripts/classflow-mcp', 'skills/classflow/SKILL.md',
)


def package(root: Path, output: Path) -> list[Path]:
    plugin = root / 'plugins/classflow'
    manifest = json.loads((plugin / '.codex-plugin/plugin.json').read_text())
    version = manifest['version']
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise ValueError('Invalid plugin version')
    sources = {f'plugins/classflow/{name}': plugin / name for name in PLUGIN_FILES}
    sources.update({
        'scripts/install_mcp.sh': root / 'scripts/install_mcp.sh',
        'README.md': root / 'docs/CHATGPT_RELEASE_SETUP.md',
        'docs/CHATGPT_INTEGRATION.md': root / 'docs/CHATGPT_INTEGRATION.md',
        'LICENSE': root / 'LICENSE',
    })
    # Read and validate everything before writing any artifact. Never traverse user configuration.
    payload = {}
    for name, source in sources.items():
        if source.is_symlink() or not source.is_file():
            raise ValueError(f'Missing or symlinked release source: {name}')
        payload[name] = source.read_bytes()
    output.mkdir(parents=True, exist_ok=True)
    archive = output / f'ClassFlow-Local-Plugin-{version}.zip'
    with ZipFile(archive, 'w', compression=ZIP_DEFLATED) as bundle:
        for name, data in sorted(payload.items()):
            info = ZipInfo(f'ClassFlow-Local-Plugin/{name}', date_time=(2020, 1, 1, 0, 0, 0))
            info.create_system = 3
            executable = name in ('scripts/install_mcp.sh', 'plugins/classflow/scripts/classflow-mcp')
            info.external_attr = (0o100755 if executable else 0o100644) << 16
            info.compress_type = ZIP_DEFLATED
            bundle.writestr(info, data)
    guide = output / 'ClassFlow-ChatGPT-Setup.md'
    guide.write_bytes(payload['README.md'])
    artifacts = [archive, guide]
    checksums = []
    for artifact in artifacts:
        line = f'{hashlib.sha256(artifact.read_bytes()).hexdigest()}  {artifact.name}\n'
        checksum = artifact.with_name(artifact.name + '.sha256')
        checksum.write_text(line)
        checksums.append(checksum)
    return artifacts + checksums


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parent.parent / 'dist')
    args = parser.parse_args()
    for artifact in package(Path(__file__).resolve().parent.parent, args.output):
        print(artifact)
