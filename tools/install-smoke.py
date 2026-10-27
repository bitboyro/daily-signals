#!/usr/bin/env python3
"""Validate that a clean checkout contains a portable installation contract."""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
manifest = json.loads((ROOT / "deploy/install-manifest.json").read_text())
compose = (ROOT / "deploy/compose.yaml").read_text()
runtime = (ROOT / "deploy/runtime.env").read_text()

assert manifest["schemaVersion"] == 1
assert {item["name"] for item in manifest["services"]} == {"api", "streamer"}
for service in manifest["services"]:
    assert 1 <= service["port"] <= 65535
    assert service["healthPath"].startswith("/")
    assert f'{service["port"]}:{service["port"]}' in compose
    assert f"  {service['name']}:" in compose

declared = {match.group(1) for match in re.finditer(r"^([A-Z][A-Z0-9_]*)=", runtime, re.MULTILINE)}
assert set(manifest["runtimeVariables"]) <= declared
for item in manifest["immutableMountInputs"]:
    assert (ROOT / item).exists(), item
    assert f"../{item}:" in compose

for forbidden in ("target/", "node_modules/", str(ROOT)):
    assert forbidden not in json.dumps(manifest)
print(f"installation smoke passed: {len(manifest['services'])} services, {len(declared)} variables")
