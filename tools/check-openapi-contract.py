#!/usr/bin/env python3
"""Checks the checked-in edition fixture against its minimal OpenAPI contract."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = json.loads((ROOT / "contracts/openapi/editions-v1.json").read_text())
fixture = json.loads((ROOT / "contracts/edition-list-v2.json").read_text())

assert spec["openapi"].startswith("3.")
assert "/api/editions" in spec["paths"]
required = set(spec["components"]["schemas"]["EditionSummary"]["required"])
assert fixture["schemaVersion"] == 2
for edition in fixture["editions"]:
    missing = required - edition.keys()
    if missing:
        raise SystemExit(f"edition fixture misses required fields: {sorted(missing)}")
print("edition OpenAPI contract matches fixture")
