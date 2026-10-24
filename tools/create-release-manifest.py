#!/usr/bin/env python3
"""Write a tag-bound, auditable manifest only after release qualification passes."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def create_manifest(tag, output):
    head = git('rev-parse', 'HEAD')
    if git('rev-parse', f'refs/tags/{tag}^{{commit}}') != head:
        raise ValueError('release tag must resolve to the checked-out source SHA')
    reports = sorted(ROOT.glob('**/target/surefire-reports/TEST-*.xml'))
    rust_log = ROOT / 'rust-test.log'
    if not reports or not rust_log.is_file():
        raise ValueError('Java JUnit and Rust test reports are required')
    all_reports = reports + [rust_log]
    manifest = {
        'schemaVersion': 1,
        'tag': tag,
        'sourceSha': head,
        'contractSha256': digest(ROOT / 'contracts/publication-manifest-v1.json'),
        'reports': [{'path': str(path.relative_to(ROOT)), 'sha256': digest(path)} for path in all_reports],
    }
    output.write_text(json.dumps(manifest, indent=2) + '\n')
    return manifest

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--tag', required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'release-manifest.json')
    args = parser.parse_args()
    print(json.dumps(create_manifest(args.tag, args.output), indent=2))
