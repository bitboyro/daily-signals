#!/usr/bin/env python3
"""Make the nightly report explicit about the pre-registered rollout evidence gate."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def rollout_section(report: dict, minimum_failing_changes: int) -> str:
    all_time = report.get("allTime", {})
    observed = int(all_time.get("failingChanges", 0))
    misses = int(all_time.get("changesWithAMiss", 0))
    enough = observed >= minimum_failing_changes
    status = "sample-size gate met" if enough else "insufficient evidence"
    consequence = (
        "This satisfies only the sample-size part of the rollout gate; replay and escape checks still apply."
        if enough
        else "Zero misses is encouraging, but enforcement remains blocked until the minimum is reached."
    )
    return (
        "## Rollout evidence\n\n"
        f"**{status.capitalize()}: {observed} of {minimum_failing_changes} required failing pull-request runs "
        f"audited; {misses} missed.** {consequence}\n"
    )


def enrich(report_json: Path, report_markdown: Path, minimum_failing_changes: int) -> str:
    report = json.loads(report_json.read_text())
    section = rollout_section(report, minimum_failing_changes)
    markdown = report_markdown.read_text()
    if "## Rollout evidence" in markdown:
        markdown = markdown.split("## Rollout evidence", 1)[0].rstrip() + "\n\n"
    markdown = markdown.replace(
        "**Everything passed.**", "**Reference and slow-tier checks passed.**")
    report_markdown.write_text(markdown.rstrip() + "\n\n" + section)
    return section


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", type=Path, default=Path("nightly-report.json"))
    parser.add_argument("--markdown", type=Path, default=Path("nightly-report.md"))
    parser.add_argument("--minimum-failing-changes", type=int, default=8)
    args = parser.parse_args()
    if args.minimum_failing_changes < 1:
        parser.error("--minimum-failing-changes must be positive")
    print(enrich(args.json, args.markdown, args.minimum_failing_changes), end="")


if __name__ == "__main__":
    main()
