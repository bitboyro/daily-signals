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

def published_audio():
    """The fixture edition shipped with the release must match its manifest checksum."""
    manifest = json.loads((ROOT / 'contracts/publication-manifest-v2.json').read_text())
    audio = ROOT / 'radio-api/src/main/resources' / manifest['audioPath']
    if digest(audio) != manifest['checksumSha256'] or audio.stat().st_size != manifest['sizeBytes']:
        raise ValueError('published fixture audio does not match its manifest')
    return {'editionId': manifest['editionId'], 'audioPath': manifest['audioPath'], 'sha256': manifest['checksumSha256']}

def create_manifest(tag, output):
    head = git('rev-parse', 'HEAD')
    if git('rev-parse', f'refs/tags/{tag}^{{commit}}') != head:
        raise ValueError('release tag must resolve to the checked-out source SHA')
    java = sorted(ROOT.glob('**/target/surefire-reports/TEST-*.xml'))
    web = sorted((ROOT / 'web/reports').glob('*.xml'))
    rust_log = ROOT / 'rust-test.log'
    if not java or not web or not rust_log.is_file():
        raise ValueError('Java JUnit, web JUnit and Rust test reports are required')
    manifest = {
        'schemaVersion': 2,
        'tag': tag,
        'sourceSha': head,
        'contracts': {path.name: digest(path) for path in sorted((ROOT / 'contracts').glob('*.json'))},
        'publishedAudio': published_audio(),
        'reports': [{'path': str(path.relative_to(ROOT)), 'sha256': digest(path)} for path in java + web + [rust_log]],
    }
    output.write_text(json.dumps(manifest, indent=2) + '\n')
    return manifest

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--tag', required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'release-manifest.json')
    args = parser.parse_args()
    print(json.dumps(create_manifest(args.tag, args.output), indent=2))
