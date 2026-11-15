#!/usr/bin/env python3
"""Check that documentation links resolve and operations examples are valid."""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

links = 0
for doc in sorted((ROOT / 'docs').rglob('*.md')):
    for target in re.findall(r'\]\(([^)#\s]+)(?:#[^)]*)?\)', doc.read_text()):
        if '://' in target:
            continue
        assert (doc.parent / target).exists(), f'{doc.relative_to(ROOT)} links to missing {target}'
        links += 1

policy = json.loads((ROOT / 'examples/operations/retention-policy.json').read_text())
assert policy['schemaVersion'] == 1
names = [entry['name'] for entry in policy['classes']]
assert names == ['published', 'replaced', 'staging'], names
for entry in policy['classes']:
    assert isinstance(entry['minimumDays'], int) and entry['minimumDays'] > 0, entry
    assert entry['deleteWhilePublished'] is False, entry
assert policy['incidentHold']['overridesClasses'] is True

operator = (ROOT / 'docs/operations/installation.md').read_text()
shell_examples = '\n'.join(re.findall(r'```sh\n(.*?)\n```', operator, re.DOTALL))
for command in (
    './install.sh --dry-run',
    'python3 tools/backup.py --output',
    'python3 tools/restore.py backups/<timestamp> --verify-only',
    'python3 tools/upgrade-smoke.py',
    'python3 tools/post-deploy-health.py --revision',
):
    assert command in shell_examples, f'operator guide is missing runnable example: {command}'

for command in (
    [str(ROOT / 'install.sh'), '--help'],
    [sys.executable, str(ROOT / 'tools/startup_preflight.py'), '--help'],
    [sys.executable, str(ROOT / 'tools/backup.py'), '--help'],
    [sys.executable, str(ROOT / 'tools/restore.py'), '--help'],
    [sys.executable, str(ROOT / 'tools/upgrade-smoke.py'), '--help'],
    [sys.executable, str(ROOT / 'tools/post-deploy-health.py'), '--help'],
):
    subprocess.run(command, cwd=ROOT, check=True, stdout=subprocess.DEVNULL,
                   stderr=subprocess.PIPE, text=True)

producer = (ROOT / 'docs/source-configuration.md').read_text()
producer_stages = [
    '## Review today\'s candidates', '## Edit the script', '## Check pronunciation',
    '## Generate and approve audio', '## Publish',
]
positions = [producer.index(stage) for stage in producer_stages]
assert positions == sorted(positions), 'producer guide workflow is out of order'
for state in ('`READY`', '`PUBLISHED`', '`FAILED`'):
    assert state in producer, f'producer guide is missing publication state {state}'

print(f'{links} documentation links, 6 runnable commands, producer workflow and retention example passed')
