#!/usr/bin/env python3
"""Check compatibility of a prior release configuration with the current install."""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
prior_root = ROOT / "examples/upgrade/v0.1.0"
prior = json.loads((prior_root / "release.json").read_text())
current = json.loads((ROOT / "deploy/install-manifest.json").read_text())
legacy_manifest = json.loads((ROOT / "contracts/publication-manifest-v1.json").read_text())
prior_env = (prior_root / "runtime.env").read_text()

assert prior["release"] == "0.1.0"
assert prior["publicationManifestSchema"] == 1
assert legacy_manifest["schemaVersion"] == 1
declared = {match.group(1) for match in re.finditer(r"^([A-Z][A-Z0-9_]*)=", prior_env, re.MULTILINE)}
assert declared == set(prior["runtimeVariables"])
missing = declared - set(current["runtimeVariables"])
assert not missing, f"prior runtime variables removed: {sorted(missing)}"
print(f"upgrade smoke passed: {prior['release']} with schema v{legacy_manifest['schemaVersion']}")
