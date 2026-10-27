#!/usr/bin/env python3
"""Scan tracked source for credential material without printing matched secrets."""
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
patterns = {
    "private-key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "github-token": re.compile(rb"\bgh[opsu]_[A-Za-z0-9]{30,}\b"),
    "aws-access-key": re.compile(rb"\bAKIA[0-9A-Z]{16}\b"),
    "generic-secret": re.compile(rb"(?i)(?:password|secret|api[_-]?key)\s*[:=]\s*['\"][^'\"]{12,}['\"]"),
}
failures = []
for relative in subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).split(b"\0"):
    if not relative:
        continue
    path = ROOT / relative.decode()
    data = path.read_bytes()
    for name, pattern in patterns.items():
        if pattern.search(data):
            failures.append(f"{path.relative_to(ROOT)}: {name}")
assert not failures, "credential-like material found:\n" + "\n".join(failures)
print("tracked-source secret scan passed")
