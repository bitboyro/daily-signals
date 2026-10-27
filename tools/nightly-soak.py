#!/usr/bin/env python3
"""Run a bounded, deterministic nightly soak profile and emit JSON evidence."""
import argparse
import json
import pathlib
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--profile", type=pathlib.Path, default=ROOT / "qualification/nightly-soak.json")
parser.add_argument("--iterations", type=int)
parser.add_argument("--report", type=pathlib.Path, default=pathlib.Path("nightly-soak-report.json"))
parser.add_argument("--validate-only", action="store_true")
args = parser.parse_args()

profile = json.loads(args.profile.read_text())
assert profile["schemaVersion"] == 1
iterations = args.iterations if args.iterations is not None else profile["iterations"]
assert 1 <= iterations <= 100
assert 1 <= profile["maximumDurationSeconds"] <= 86_400
assert profile["tasks"] and len({task["name"] for task in profile["tasks"]}) == len(profile["tasks"])
for task in profile["tasks"]:
    assert task["command"] and all(isinstance(part, str) and part for part in task["command"])

if args.validate_only:
    print(f"nightly soak profile valid: {len(profile['tasks'])} tasks x {iterations} iterations")
    raise SystemExit(0)

started = time.monotonic()
results = []
for iteration in range(1, iterations + 1):
    for task in profile["tasks"]:
        task_started = time.monotonic()
        subprocess.run(task["command"], cwd=ROOT, check=True)
        results.append({"iteration": iteration, "task": task["name"],
                        "durationMs": round((time.monotonic() - task_started) * 1000), "result": "PASS"})
        assert time.monotonic() - started <= profile["maximumDurationSeconds"], "soak duration budget exceeded"

report = {"schemaVersion": 1, "iterations": iterations, "result": "PASS", "tasks": results,
          "durationMs": round((time.monotonic() - started) * 1000)}
args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
print(f"nightly soak passed: {len(results)} task executions")
