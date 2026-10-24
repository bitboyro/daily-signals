#!/usr/bin/env python3
"""Check that documentation links resolve and operations examples are valid."""
import json
import re
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
print(f'{links} documentation links and the retention policy example passed')
