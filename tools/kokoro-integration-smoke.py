#!/usr/bin/env python3
"""Validate optional Kokoro pins; optionally synthesize through a running local runtime."""
import argparse
import hashlib
import json
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--live", action="store_true")
parser.add_argument("--endpoint", default="http://127.0.0.1:8880")
parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("kokoro-smoke.wav"))
args = parser.parse_args()

compose = (ROOT / "deploy/compose.yaml").read_text()
lock = json.loads((ROOT / "deploy/kokoro/model.lock.json").read_text())
assert 'profiles: ["kokoro"]' in compose
assert "ghcr.io/remsky/kokoro-fastapi-cpu:v0.8.0@sha256:" in compose
assert "${KOKORO_MODEL_DIR:-./kokoro/models}:/models:ro" in compose
assert lock["revision"] != "latest" and len(lock["revision"]) == 40
assert len(lock["sha256"]) == 64 and lock["license"] == "Apache-2.0"

if not args.live:
    print("Kokoro profile pins and mounts passed (static mode)")
    raise SystemExit(0)

model = pathlib.Path(ROOT / "deploy/kokoro/models" / lock["file"])
assert model.exists(), f"operator must supply {model}"
assert hashlib.sha256(model.read_bytes()).hexdigest() == lock["sha256"]
payload = json.dumps({"model": lock["revision"], "voice": "af_heart", "input": "Daily Signals local check."}).encode()
request = urllib.request.Request(args.endpoint + "/v1/audio/speech", data=payload,
                                 headers={"Content-Type": "application/json"}, method="POST")
with urllib.request.urlopen(request, timeout=120) as response:
    audio = response.read()
assert audio[:4] == b"RIFF"
args.output.write_bytes(audio)
print(f"Kokoro live synthesis passed: {len(audio)} bytes")
