#!/usr/bin/env python3
"""Destroy and restore a live Compose install, proving database and object recovery."""
from __future__ import annotations

import argparse
import datetime
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

from backup import COMPOSE, ROOT, digest

API = "http://127.0.0.1:8151"
MARKER_PATH = "/data/daily-signals/qualification/backup-roundtrip.txt"
MARKER = b"daily-signals backup round trip\n"
SETTINGS = {
    "schemaVersion": 1,
    "configured": True,
    "sources": [{
        "id": "backup-probe",
        "name": "Backup round-trip probe",
        "url": "https://news.example.invalid/backup-probe.xml",
        "priority": 1,
    }],
    "voice": "fixture",
    "zone": "UTC",
    "window": "05:45",
}


def command(arguments: list[str], payload: bytes | None = None, check: bool = True) -> subprocess.CompletedProcess:
    completed = subprocess.run(arguments, cwd=ROOT, input=payload, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check and completed.returncode:
        detail = completed.stderr.decode(errors="replace").strip()
        raise RuntimeError(f"{' '.join(arguments)} failed" + (f": {detail}" if detail else ""))
    return completed


def request_json(path: str, method: str = "GET", body: dict | None = None) -> dict:
    payload = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(API + path, data=payload, method=method,
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.load(response)


def wait_for_api(timeout_seconds: float = 120) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            if request_json("/health") == {"status": "ok"}:
                return
        except (OSError, urllib.error.URLError, json.JSONDecodeError):
            pass
        time.sleep(1)
    raise TimeoutError("Daily Signals API did not become healthy")


def write_marker(payload: bytes) -> None:
    script = (
        "import pathlib,sys; p=pathlib.Path(sys.argv[1]); "
        "p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(sys.stdin.buffer.read())"
    )
    command(COMPOSE + ["exec", "-T", "object-store", "python3", "-c", script, MARKER_PATH], payload)


def marker_exists() -> bool:
    script = "import pathlib,sys; raise SystemExit(0 if pathlib.Path(sys.argv[1]).is_file() else 1)"
    return command(COMPOSE + ["exec", "-T", "object-store", "python3", "-c", script, MARKER_PATH],
                   check=False).returncode == 0


def read_marker() -> bytes:
    return command(COMPOSE + ["exec", "-T", "object-store", "cat", MARKER_PATH]).stdout


def validate_wipe(settings: dict, has_marker: bool) -> None:
    if settings.get("configured") or has_marker:
        raise AssertionError("volume wipe left station settings or the object marker behind")


def validate_restore(settings: dict, marker: bytes) -> None:
    if settings != SETTINGS:
        raise AssertionError("restored station settings differ from the backup probe")
    if marker != MARKER:
        raise AssertionError("restored object marker differs from the backup probe")


def round_trip(backup: Path, report_path: Path) -> dict:
    report = {
        "schemaVersion": 1,
        "startedAt": datetime.datetime.now(datetime.UTC).isoformat(),
        "result": "FAIL",
        "stages": [],
    }
    try:
        command(COMPOSE + ["up", "-d", "--build"])
        wait_for_api()
        saved = request_json("/api/setup", "PUT", SETTINGS)
        if saved != SETTINGS:
            raise AssertionError("API did not persist the backup probe settings")
        write_marker(MARKER)
        report["stages"].append("seeded")

        command([sys.executable, str(ROOT / "tools" / "backup.py"), "--output", str(backup)])
        report["stages"].append("backed-up")

        command(COMPOSE + ["down", "--volumes", "--remove-orphans"])
        command(COMPOSE + ["up", "-d"])
        wait_for_api()
        validate_wipe(request_json("/api/setup"), marker_exists())
        report["stages"].append("wiped")

        command([sys.executable, str(ROOT / "tools" / "restore.py"), str(backup)])
        wait_for_api()
        validate_restore(request_json("/api/setup"), read_marker())
        report["stages"].append("restored")
        report["markerSha256"] = digest(MARKER)
        report["result"] = "PASS"
        return report
    except Exception as problem:
        report["error"] = f"{type(problem).__name__}: {problem}"
        raise
    finally:
        report["finishedAt"] = datetime.datetime.now(datetime.UTC).isoformat()
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, default=ROOT / "backup-roundtrip-report.json")
    arguments = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="daily-signals-backup-roundtrip-") as workspace:
        report = round_trip(Path(workspace) / "backup", arguments.report)
    print(f"backup round trip {report['result'].lower()}: {', '.join(report['stages'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
