#!/usr/bin/env python3
"""Validate that a clean checkout contains a portable installation contract."""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
manifest = json.loads((ROOT / "deploy/install-manifest.json").read_text())
compose = (ROOT / "deploy/compose.yaml").read_text()
runtime = (ROOT / "deploy/runtime.env").read_text()

assert manifest["schemaVersion"] == 2
assert manifest["installCommand"] == "./install.sh"
assert manifest["quickStartTargetMinutes"] == 30
assert (ROOT / "install.sh").is_file()
assert {item["name"] for item in manifest["services"]} == {"postgres", "object-store", "api", "streamer", "kokoro"}
assert {item["name"] for item in manifest["services"] if item["optional"]} == {"kokoro"}
assert "  postgres:" in compose
assert "  object-store:" in compose
assert "condition: service_healthy" in compose
for variable in ("DAILY_SIGNALS_DATABASE_URL", "DAILY_SIGNALS_DATABASE_USER", "DAILY_SIGNALS_DATABASE_PASSWORD"):
    assert variable in compose, f"compose does not configure {variable}"
for service in manifest["services"]:
    assert f"  {service['name']}:" in compose
    if "port" in service:
        assert 1 <= service["port"] <= 65535
    if "healthPath" in service:
        assert service["healthPath"].startswith("/")
        assert f'{service["port"]}:{service["port"]}' in compose

for volume in manifest["persistentVolumes"]:
    assert compose.count(f"{volume}:") >= 2, f"{volume} is not declared and mounted"
assert "dockerfile: deploy/object-store.Containerfile" in compose
assert "daily-signals-objects:/data" in compose
assert "--profile kokoro up --build" in runtime
for variable in ("DAILY_SIGNALS_KOKORO_ENDPOINT", "DAILY_SIGNALS_KOKORO_MODEL_REVISION",
                 "DAILY_SIGNALS_KOKORO_MODEL_SHA256", "DAILY_SIGNALS_KOKORO_VOICE_ID"):
    assert variable in runtime, f"runtime does not configure {variable}"

declared = {match.group(1) for match in re.finditer(r"^([A-Z][A-Z0-9_]*)=", runtime, re.MULTILINE)}
assert set(manifest["runtimeVariables"]) <= declared
for item in manifest["immutableMountInputs"]:
    assert (ROOT / item).exists(), item
    assert f"../{item}:" in compose

for forbidden in ("target/", "node_modules/", str(ROOT)):
    assert forbidden not in json.dumps(manifest)
print(f"installation smoke passed: {len(manifest['services'])} services, "
      f"{len(manifest['persistentVolumes'])} durable volumes, {len(declared)} variables")
