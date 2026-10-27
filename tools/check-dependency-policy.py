#!/usr/bin/env python3
"""Enforce reviewable, locked dependency declarations across the monorepo."""
import json
import pathlib
import re
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
policy = json.loads((ROOT / "security/dependency-policy.json").read_text())
for lock in policy["requiredLocks"]:
    assert (ROOT / lock).is_file(), f"missing dependency lock: {lock}"

namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(ROOT / "pom.xml").getroot()
properties = root.find("m:properties", namespace)
assert properties is not None
for name in policy["mavenProperties"]:
    value = properties.find(f"m:{name}", namespace)
    assert value is not None and re.fullmatch(r"\d+(?:\.\d+)+(?:[-.][A-Za-z0-9]+)?", value.text or ""), f"unpinned Maven property: {name}"

texts = [path.read_text() for path in [ROOT / "pom.xml", ROOT / "streamer/Cargo.toml", ROOT / "web/package.json"]]
for token in policy["forbiddenVersionTokens"]:
    assert not any(token in text for text in texts), f"forbidden dependency version token: {token}"
if policy["forbidLatestContainerTag"]:
    for dockerfile in (ROOT / "deploy").glob("*.Dockerfile"):
        assert not any(line.startswith("FROM ") and ":latest" in line for line in dockerfile.read_text().splitlines()), f"floating image in {dockerfile.name}"
print("dependency policy passed for Maven, Cargo, npm and container declarations")
