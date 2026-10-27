#!/usr/bin/env python3
"""Record immutable local artifacts produced by a successful qualification run."""
import argparse
import hashlib
import json
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


parser = argparse.ArgumentParser()
parser.add_argument("--candidate", default="producer-listener")
parser.add_argument("--deployment-report", type=pathlib.Path, default=ROOT / "e6-cross-stack.json")
parser.add_argument("--output", type=pathlib.Path, default=ROOT / "e6-qualification-evidence.json")
args = parser.parse_args()

archives = sorted((ROOT / "web/artifacts").glob("daily-signals-listener-*.zip"))
if len(archives) != 1:
    raise SystemExit(f"expected one listener release archive, found {len(archives)}")
deployment = json.loads(args.deployment_report.read_text())
assert deployment["result"] == "PASS"
evidence = {
    "schemaVersion": 1,
    "candidate": args.candidate,
    "sourceRevision": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
    "listenerArtifact": {"path": archives[0].relative_to(ROOT).as_posix(), "sha256": sha256(archives[0])},
    "deploymentEvidence": {"path": args.deployment_report.name, "sha256": sha256(args.deployment_report)},
    "checks": [item["name"] for item in deployment["checks"]],
    "result": "PASS",
}
args.output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
print(f"recorded E6 qualification evidence for {evidence['sourceRevision']}")
