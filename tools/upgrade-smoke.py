#!/usr/bin/env python3
"""Check compatibility and prepare a real tagged v0.x upgrade rehearsal."""
import argparse
import io
import json
import pathlib
import re
import shutil
import subprocess
import tarfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
PREVIOUS_TAG = "v0.2.0"
prior_root = ROOT / "examples/upgrade/v0.1.0"
prior = json.loads((prior_root / "release.json").read_text())
current = json.loads((ROOT / "deploy/install-manifest.json").read_text())
legacy_manifest = json.loads((ROOT / "contracts/publication-manifest-v1.json").read_text())
prior_env = (prior_root / "runtime.env").read_text()

assert prior["release"] == "0.1.0"
assert prior["publicationManifestSchema"] == 1
assert legacy_manifest["schemaVersion"] == 1
declared = {match.group(1) for match in re.finditer(r"^([A-Z][A-Z0-9_]*)=", prior_env, re.MULTILINE)}
assert declared == set(prior["runtimeVariables"])
missing = declared - set(current["runtimeVariables"])
assert not missing, f"prior runtime variables removed: {sorted(missing)}"


def prepare_state(destination: pathlib.Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    release_root = destination / "release"
    state_root = destination / "state"
    release_root.mkdir(parents=True)
    state_root.mkdir()
    revision = subprocess.check_output(
        ["git", "rev-list", "-n", "1", PREVIOUS_TAG], cwd=ROOT, text=True
    ).strip()
    archive = subprocess.check_output(["git", "archive", "--format=tar", PREVIOUS_TAG], cwd=ROOT)
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:") as bundle:
        for member in bundle.getmembers():
            target = (release_root / member.name).resolve()
            if not target.is_relative_to(release_root.resolve()):
                raise AssertionError(f"unsafe release archive member: {member.name}")
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                target.parent.mkdir(parents=True, exist_ok=True)
                source = bundle.extractfile(member)
                if source is None:
                    raise AssertionError(f"release file has no content: {member.name}")
                target.write_bytes(source.read())
    shutil.copyfile(release_root / "contracts/edition-list-v1.json", state_root / "editions.json")
    (state_root / "station-settings.json").write_text(json.dumps({
        "schemaVersion": 1,
        "configured": True,
        "sources": [{"id": "legacy-desk", "name": "Legacy desk",
                     "url": "https://news.example.invalid/legacy.xml", "priority": 10}],
        "voice": "fixture",
        "zone": "Europe/Bucharest",
        "window": "06:00",
    }, indent=2) + "\n")
    (state_root / "provenance.json").write_text(json.dumps({
        "schemaVersion": 1, "tag": PREVIOUS_TAG, "revision": revision,
    }, indent=2) + "\n")


parser = argparse.ArgumentParser()
parser.add_argument("--prepare-state", type=pathlib.Path)
arguments = parser.parse_args()
if arguments.prepare_state:
    prepare_state(arguments.prepare_state)
print(f"upgrade smoke passed: {prior['release']} with schema v{legacy_manifest['schemaVersion']}")
