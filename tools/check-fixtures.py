#!/usr/bin/env python3
"""Validate fixed synthetic source and playable audio without external services."""
import json
import hashlib
import pathlib
import wave

ROOT = pathlib.Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "radio-api/src/main/resources/fixtures"

source = json.loads((FIXTURES / "source-snapshot.json").read_text())
assert source["license"] == "synthetic-test-content"
assert source["snapshotId"] and len(source["stories"]) == 3
assert source["stories"][2]["category"] == "transport"
assert len({story["id"] for story in source["stories"]}) == len(source["stories"])
alternate = json.loads((FIXTURES / "alternate-source-snapshot.json").read_text())
assert alternate["license"] == "synthetic-test-content"
assert alternate["source"]["id"] != source["source"]["id"]
assert len(alternate["stories"]) == 2
with wave.open(str(FIXTURES / "demo-edition.wav"), "rb") as recording:
    assert recording.getnchannels() == 1
    assert recording.getframerate() == 8000
    assert recording.getsampwidth() == 2
    assert recording.getnframes() == 8000

contracts = ROOT / "contracts"
manifests = {
    version: json.loads((contracts / f"publication-manifest-v{version}.json").read_text())
    for version in (1, 2)
}
assert {manifest["schemaVersion"] for manifest in manifests.values()} == {1, 2}
assert "airDate" not in manifests[1]
assert manifests[2]["airDate"] == "2026-01-15"
common_fields = ("editionId", "status", "audioPath", "contentType", "sizeBytes", "checksumSha256")
assert all(manifests[1][field] == manifests[2][field] for field in common_fields)
audio = FIXTURES / pathlib.PurePosixPath(manifests[2]["audioPath"]).name
assert audio.stat().st_size == manifests[2]["sizeBytes"]
assert hashlib.sha256(audio.read_bytes()).hexdigest() == manifests[2]["checksumSha256"]
assert manifests[2]["audioPath"] == f"fixtures/{audio.name}"
print("4 fixture groups passed")
