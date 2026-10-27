#!/usr/bin/env python3
"""Scan container descriptors for unsafe runtime and secret-handling policy."""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
for path in sorted((ROOT / "deploy").glob("*.Dockerfile")):
    text = path.read_text()
    assert ":latest" not in text, f"{path.name}: floating latest tag"
    final = text.split("\nFROM ")[-1]
    user = re.search(r"^USER\s+(\S+)", final, re.MULTILINE)
    assert user and user.group(1) not in {"root", "0"}, f"{path.name}: final image must be non-root"
    assert re.search(r"^ENTRYPOINT \[", final, re.MULTILINE), f"{path.name}: exec-form ENTRYPOINT required"
compose = (ROOT / "deploy/compose.yaml").read_text()
assert "privileged:" not in compose and "/var/run/docker.sock" not in compose
runtime = (ROOT / "deploy/runtime.env").read_text().lower()
assert not any(name in runtime for name in ("password=", "token=", "secret="))
print("container descriptor security scan passed")
