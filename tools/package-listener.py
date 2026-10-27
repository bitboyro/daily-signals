#!/usr/bin/env python3
"""Create or verify a deterministic Daily Signals listener release archive."""
import argparse
import hashlib
import json
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
DIST = ROOT / "web/dist"
ARTIFACTS = ROOT / "web/artifacts"
FIXED_TIME = (2026, 1, 1, 0, 0, 0)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def entry(name: str, data: bytes) -> tuple[zipfile.ZipInfo, bytes]:
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.external_attr = 0o100644 << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    return info, data


def create() -> pathlib.Path:
    package = json.loads((ROOT / "web/package.json").read_text())
    files = {path.relative_to(DIST).as_posix(): path.read_bytes() for path in sorted(DIST.rglob("*")) if path.is_file()}
    if not files:
        raise SystemExit("web/dist is empty; run the listener build first")
    manifest = {
        "schemaVersion": 1,
        "name": package["name"],
        "version": package["version"],
        "files": [{"path": name, "sha256": digest(data)} for name, data in files.items()],
    }
    files["listener-package.json"] = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    target = ARTIFACTS / f"daily-signals-listener-{package['version']}.zip"
    with zipfile.ZipFile(target, "w") as archive:
        for name, data in files.items():
            archive.writestr(*entry(name, data))
    return target


def verify(target: pathlib.Path) -> None:
    with zipfile.ZipFile(target) as archive:
        manifest = json.loads(archive.read("listener-package.json"))
        assert manifest["schemaVersion"] == 1
        for item in manifest["files"]:
            assert digest(archive.read(item["path"])) == item["sha256"]
        expected = sorted(item["path"] for item in manifest["files"]) + ["listener-package.json"]
        assert sorted(archive.namelist()) == sorted(expected)


parser = argparse.ArgumentParser()
parser.add_argument("--verify", type=pathlib.Path)
args = parser.parse_args()
if args.verify:
    verify(args.verify)
    print(f"verified {args.verify}")
else:
    result = create()
    verify(result)
    print(result)
