#!/usr/bin/env python3
"""Validate the declared v1 release version, artifacts and qualification policy."""
import json
import pathlib
import re
import tomllib

ROOT = pathlib.Path(__file__).resolve().parents[1]
candidate = json.loads((ROOT / "qualification/release-v1.0.0.json").read_text())
assert candidate["schemaVersion"] == 1
assert candidate["release"] == "1.0.0"
assert candidate["tag"] == "v1.0.0"
assert candidate["profile"] == "full-platform"
assert candidate["tagAfterMergeOnly"] is True

pom = (ROOT / "pom.xml").read_text()
assert re.search(r"<artifactId>daily-signals</artifactId>\s*<version>1\.0\.0</version>", pom)
web = json.loads((ROOT / "web/package.json").read_text())
web_lock = json.loads((ROOT / "web/package-lock.json").read_text())
rust = tomllib.loads((ROOT / "streamer/Cargo.toml").read_text())
assert web["version"] == web_lock["version"] == web_lock["packages"][""]["version"] == "1.0.0"
assert rust["package"]["version"] == "1.0.0"

release_workflow = (ROOT / ".github/workflows/release.yml").read_text()
for phrase in (
    "Build v1 container images", "Retain listener release candidate",
    "Build source-complete install bundle", "Retain release manifest",
):
    assert phrase in release_workflow, f"release workflow missing {phrase}"

all_workflows = "\n".join(path.read_text() for path in (ROOT / ".github/workflows").glob("*.yml"))
workflow_markers = {
    "java-postgresql": "Java and PostgreSQL qualification",
    "rust": "Rust qualification",
    "web-browser": "Web qualification",
    "container": "Cross-process and container qualification",
    "install-smoke-matrix": "install-smoke-matrix:",
    "upgrade-smoke": "upgrade-smoke:",
    "post-deploy-health": "post-deploy-health:",
    "container-scan": "container-scan:",
}
assert set(candidate["requiredQualification"]) == set(workflow_markers)
for check, marker in workflow_markers.items():
    assert marker in all_workflows, f"qualification job is not declared: {check}"

expected_artifacts = {"api-image", "streamer-image", "listener-zip", "install-tarball", "release-manifest"}
assert set(candidate["artifacts"]) == expected_artifacts
print("v1.0.0 release candidate declaration passed")
