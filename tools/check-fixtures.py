#!/usr/bin/env python3
"""Validate fixed synthetic source and playable audio without external services."""
import json
import pathlib
import wave

ROOT = pathlib.Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "radio-api/src/main/resources/fixtures"

source = json.loads((FIXTURES / "source-snapshot.json").read_text())
assert source["license"] == "synthetic-test-content"
assert source["snapshotId"] and len(source["stories"]) >= 2
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
print("3 fixture checks passed")
