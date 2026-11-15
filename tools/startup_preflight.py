#!/usr/bin/env python3
"""Fail early with actionable installation errors before Compose starts services."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE_VARIABLES = {
    "DAILY_SIGNALS_BIND_ADDRESS", "DAILY_SIGNALS_WEB_ROOT", "DAILY_SIGNALS_SETUP_PATH",
    "STREAMER_BIND_ADDRESS", "STREAMER_PUBLIC_PREFIXES", "OBJECT_STORE_ENDPOINT",
    "OBJECT_STORE_BUCKET", "OBJECT_STORE_MANIFEST_KEY", "OBJECT_STORE_ACCESS_KEY",
    "OBJECT_STORE_SECRET_KEY",
}
KOKORO_VARIABLES = {
    "DAILY_SIGNALS_KOKORO_ENDPOINT", "DAILY_SIGNALS_KOKORO_MODEL_REVISION",
    "DAILY_SIGNALS_KOKORO_MODEL_SHA256", "DAILY_SIGNALS_KOKORO_VOICE_ID",
    "DAILY_SIGNALS_KOKORO_TIMEOUT_SECONDS",
}


def read_environment(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for number, line in enumerate(path.read_text().splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"{path.name}:{number} must use NAME=value")
        name, value = line.split("=", 1)
        values[name] = value
    return values


def configuration_errors(root: Path, profile: str, environment: dict[str, str] | None = None) -> list[str]:
    deploy = root / "deploy"
    errors: list[str] = []
    try:
        values = read_environment(deploy / "runtime.env")
    except (OSError, ValueError) as problem:
        return [f"runtime environment cannot be read: {problem}"]
    missing = sorted(name for name in BASE_VARIABLES | (KOKORO_VARIABLES if profile == "kokoro" else set())
                     if not values.get(name, "").strip())
    if missing:
        errors.append("runtime.env is missing: " + ", ".join(missing))
    if values.get("OBJECT_STORE_SECRET_KEY") == values.get("OBJECT_STORE_ACCESS_KEY"):
        errors.append("object-store secret must differ from its access key")
    if profile == "kokoro":
        try:
            lock = json.loads((deploy / "kokoro/model.lock.json").read_text())
            if values.get("DAILY_SIGNALS_KOKORO_MODEL_REVISION") != lock.get("revision"):
                errors.append("Kokoro revision differs from deploy/kokoro/model.lock.json")
            if values.get("DAILY_SIGNALS_KOKORO_MODEL_SHA256") != lock.get("sha256"):
                errors.append("Kokoro checksum differs from deploy/kokoro/model.lock.json")
            host = environment or os.environ
            model_dir = Path(host.get("KOKORO_MODEL_DIR", deploy / "kokoro/models"))
            voices_dir = Path(host.get("KOKORO_VOICES_DIR", deploy / "kokoro/voices"))
            model = model_dir / lock["file"]
            if not model.is_file():
                errors.append(f"Kokoro model is missing: {model}; provision the pinned model before selecting Kokoro")
            if not voices_dir.is_dir() or not any(voices_dir.iterdir()):
                errors.append(f"Kokoro voices are missing: {voices_dir}; provision the pinned voice assets")
        except (OSError, KeyError, json.JSONDecodeError) as problem:
            errors.append(f"Kokoro model lock cannot be read: {problem}")
    return errors


def occupied_port_errors(ports: tuple[int, ...] = (8151, 8152)) -> list[str]:
    errors: list[str] = []
    for port in ports:
        with socket.socket() as probe:
            probe.settimeout(.2)
            if probe.connect_ex(("127.0.0.1", port)) == 0:
                errors.append(f"port {port} is already in use; stop the existing service or change the published port")
    return errors


def tooling_errors(root: Path, profile: str) -> list[str]:
    if shutil.which("docker") is None:
        return ["Docker was not found; install Docker with the Compose plugin and retry"]
    commands = [["docker", "compose", "version"],
                ["docker", "compose", "--env-file", "deploy/runtime.env", "-f", "deploy/compose.yaml"]
                + (["--profile", "kokoro"] if profile == "kokoro" else []) + ["config", "--quiet"]]
    errors: list[str] = []
    for command in commands:
        completed = subprocess.run(command, cwd=root, capture_output=True, text=True)
        if completed.returncode:
            detail = (completed.stderr or completed.stdout).strip().splitlines()
            errors.append(f"{' '.join(command)} failed" + (f": {detail[-1]}" if detail else ""))
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", choices=("base", "kokoro"), default="base")
    parser.add_argument("--configuration-only", action="store_true",
                        help="validate repository configuration without probing Docker or host ports")
    parser.add_argument("--json", action="store_true", help="write the result as JSON")
    arguments = parser.parse_args(argv)
    errors = configuration_errors(ROOT, arguments.profile)
    if not arguments.configuration_only:
        errors += tooling_errors(ROOT, arguments.profile)
        errors += occupied_port_errors()
    result = {"schemaVersion": 1, "profile": arguments.profile,
              "status": "FAIL" if errors else "PASS", "errors": errors}
    if arguments.json:
        print(json.dumps(result, indent=2))
    elif errors:
        print("Daily Signals cannot start:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
    else:
        print(f"startup preflight passed for the {arguments.profile} profile")
    return 2 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
