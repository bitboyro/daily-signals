#!/usr/bin/env python3
"""Create a checksummed PostgreSQL and object-store backup from the Compose install."""
from __future__ import annotations

import argparse
import datetime
import hashlib
import io
import json
from pathlib import Path
import posixpath
import shutil
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ["docker", "compose", "--env-file", "deploy/runtime.env", "-f", "deploy/compose.yaml"]


def digest(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def object_checksums(archive: bytes) -> dict[str, dict[str, object]]:
    checksums: dict[str, dict[str, object]] = {}
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:*") as bundle:
        for member in bundle.getmembers():
            name = posixpath.normpath(member.name.removeprefix("./"))
            if member.issym() or member.islnk() or name.startswith("/") or name == ".." or name.startswith("../"):
                raise ValueError(f"unsafe object archive member: {member.name}")
            if not member.isfile():
                continue
            source = bundle.extractfile(member)
            if source is None:
                raise ValueError(f"object archive member cannot be read: {member.name}")
            payload = source.read()
            checksums[name] = {"sha256": digest(payload), "sizeBytes": len(payload)}
    if not checksums:
        raise ValueError("object-store archive contains no objects")
    return dict(sorted(checksums.items()))


def write_backup(output: Path, database: bytes, objects: bytes, created_at: str | None = None) -> dict:
    if not database or not objects:
        raise ValueError("database and object-store snapshots must be nonempty")
    if output.exists():
        raise FileExistsError(f"backup already exists: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(f".{output.name}.tmp")
    if temporary.exists():
        shutil.rmtree(temporary)
    temporary.mkdir()
    try:
        files = {"postgres.dump": database, "objects.tar": objects}
        for name, payload in files.items():
            (temporary / name).write_bytes(payload)
        manifest = {
            "schemaVersion": 1,
            "createdAt": created_at or datetime.datetime.now(datetime.UTC).isoformat(),
            "components": {
                name: {"sha256": digest(payload), "sizeBytes": len(payload)}
                for name, payload in files.items()
            },
            "objects": object_checksums(objects),
        }
        (temporary / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
        temporary.replace(output)
        return manifest
    except Exception:
        shutil.rmtree(temporary, ignore_errors=True)
        raise


def capture(command: list[str]) -> bytes:
    completed = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if completed.returncode:
        detail = completed.stderr.decode(errors="replace").strip()
        raise RuntimeError(f"{' '.join(command)} failed" + (f": {detail}" if detail else ""))
    if not completed.stdout:
        raise RuntimeError(f"{' '.join(command)} returned an empty snapshot")
    return completed.stdout


def capture_running_install() -> tuple[bytes, bytes]:
    database = capture(COMPOSE + ["exec", "-T", "postgres", "pg_dump", "--format=custom",
                                  "--username", "daily_signals", "daily_signals"])
    archive_script = (
        "import sys,tarfile; "
        "archive=tarfile.open(fileobj=sys.stdout.buffer,mode='w|'); "
        "archive.add('/data',arcname='.'); archive.close()"
    )
    objects = capture(COMPOSE + ["exec", "-T", "object-store", "python3", "-c", archive_script])
    return database, objects


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="new backup directory")
    arguments = parser.parse_args()
    timestamp = datetime.datetime.now(datetime.UTC).strftime("%Y%m%dT%H%M%SZ")
    output = arguments.output or ROOT / "backups" / timestamp
    database, objects = capture_running_install()
    manifest = write_backup(output, database, objects)
    print(f"backup written to {output}: {sum(item['sizeBytes'] for item in manifest['components'].values())} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
