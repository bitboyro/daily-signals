#!/usr/bin/env python3
"""Verify and restore a Daily Signals backup into the Compose installation."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess

from backup import COMPOSE, ROOT, digest, object_checksums


def verify_backup(folder: Path) -> tuple[dict, bytes, bytes]:
    try:
        manifest = json.loads((folder / "manifest.json").read_text())
    except (OSError, json.JSONDecodeError) as problem:
        raise ValueError(f"backup manifest cannot be read: {problem}") from problem
    if manifest.get("schemaVersion") != 1:
        raise ValueError("backup schema version 1 required")
    expected_components = {"postgres.dump", "objects.tar"}
    if set(manifest.get("components", {})) != expected_components:
        raise ValueError("backup must contain PostgreSQL and object-store components")
    payloads: dict[str, bytes] = {}
    for name in sorted(expected_components):
        try:
            payload = (folder / name).read_bytes()
        except OSError as problem:
            raise ValueError(f"backup component cannot be read: {name}: {problem}") from problem
        metadata = manifest["components"][name]
        if len(payload) != metadata.get("sizeBytes") or digest(payload) != metadata.get("sha256"):
            raise ValueError(f"backup component checksum mismatch: {name}")
        payloads[name] = payload
    actual_objects = object_checksums(payloads["objects.tar"])
    if actual_objects != manifest.get("objects"):
        raise ValueError("recording/object checksum mismatch")
    return manifest, payloads["postgres.dump"], payloads["objects.tar"]


def run(command: list[str], payload: bytes | None = None) -> None:
    completed = subprocess.run(command, cwd=ROOT, input=payload, capture_output=True)
    if completed.returncode:
        detail = completed.stderr.decode(errors="replace").strip()
        raise RuntimeError(f"{' '.join(command)} failed" + (f": {detail}" if detail else ""))


def restore(folder: Path) -> dict:
    manifest, database, objects = verify_backup(folder)
    run(COMPOSE + ["stop", "api", "streamer"])
    try:
        run(COMPOSE + ["exec", "-T", "postgres", "pg_restore", "--clean", "--if-exists",
                       "--no-owner", "--username", "daily_signals", "--dbname", "daily_signals"], database)
        extract_script = (
            "import pathlib,shutil,sys,tarfile; root=pathlib.Path('/data'); "
            "[(shutil.rmtree(p) if p.is_dir() else p.unlink()) for p in list(root.iterdir())]; "
            "tarfile.open(fileobj=sys.stdin.buffer,mode='r|').extractall(root,filter='data')"
        )
        run(COMPOSE + ["exec", "-T", "object-store", "python3", "-c", extract_script], objects)
    finally:
        run(COMPOSE + ["up", "-d", "api", "streamer"])
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("backup", type=Path)
    parser.add_argument("--verify-only", action="store_true")
    arguments = parser.parse_args()
    if arguments.verify_only:
        manifest, _, _ = verify_backup(arguments.backup)
        print(f"backup verified: {len(manifest['objects'])} objects")
    else:
        manifest = restore(arguments.backup)
        print(f"backup restored: {len(manifest['objects'])} objects; run post-deploy health next")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
