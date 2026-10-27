#!/usr/bin/env python3
"""Probe a deployed listener/API/streamer boundary and write machine-readable evidence."""
import argparse
import json
import pathlib
import time
import urllib.request


def fetch(url: str, headers: dict[str, str] | None = None) -> tuple[int, dict[str, str], bytes]:
    request = urllib.request.Request(url, headers=headers or {})
    for attempt in range(30):
        try:
            with urllib.request.urlopen(request, timeout=2) as response:
                return response.status, dict(response.headers), response.read()
        except Exception:
            if attempt == 29:
                raise
            time.sleep(1)
    raise AssertionError("unreachable")


parser = argparse.ArgumentParser()
parser.add_argument("--api-base", default="http://127.0.0.1:8151")
parser.add_argument("--stream-base", default="http://127.0.0.1:8152")
parser.add_argument("--report", type=pathlib.Path, default=pathlib.Path("post-deploy-health.json"))
args = parser.parse_args()

checks: list[dict[str, object]] = []
for name, url in (
    ("api-health", f"{args.api_base}/health"),
    ("stream-health", f"{args.stream_base}/health"),
    ("listener", f"{args.api_base}/"),
    ("listener-module", f"{args.api_base}/app/main.js"),
    ("listener-style", f"{args.api_base}/styles.css"),
):
    status, _, body = fetch(url)
    assert status == 200 and body
    checks.append({"name": name, "status": status, "bytes": len(body)})

status, _, body = fetch(f"{args.api_base}/api/editions")
catalog = json.loads(body)
assert status == 200 and catalog["schemaVersion"] == 2 and catalog["editions"]
edition = catalog["editions"][0]
audio_url = f"{args.stream_base}{edition['audioPath']}"
status, headers, audio = fetch(audio_url)
assert status == 200 and audio[:4] == b"RIFF" and headers.get("Content-Type") == "audio/wav"
assert headers.get("ETag") and "immutable" in headers.get("Cache-Control", "")
checks.append({"name": "full-audio", "status": status, "bytes": len(audio), "etag": headers["ETag"]})
status, _, first = fetch(audio_url, {"Range": "bytes=0-3"})
assert status == 206 and first == b"RIFF"
checks.append({"name": "audio-range", "status": status, "bytes": len(first)})

report = {"schemaVersion": 1, "editionId": edition["editionId"], "checks": checks, "result": "PASS"}
args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
print(f"post-deploy health passed: {len(checks)} checks")
