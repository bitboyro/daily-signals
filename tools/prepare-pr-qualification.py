#!/usr/bin/env python3
"""Create a stable, reviewable qualification decision from a pull-request event."""

import argparse
import json
from pathlib import Path


FULL_QUALIFICATION_LABEL = "run-all-checks"


def qualification_context(event):
    pull_request = event.get("pull_request")
    if not isinstance(pull_request, dict):
        raise ValueError("GitHub pull_request event is required")
    labels = sorted(
        label["name"] for label in pull_request.get("labels", [])
        if isinstance(label, dict) and isinstance(label.get("name"), str)
    )
    mode = "full" if FULL_QUALIFICATION_LABEL in labels else "standard"
    return {
        "number": pull_request["number"],
        "headSha": pull_request["head"]["sha"],
        "baseSha": pull_request["base"]["sha"],
        "labels": labels,
        "eventAction": event["action"],
        "qualificationMode": mode,
        "reason": (
            f"label:{FULL_QUALIFICATION_LABEL}"
            if mode == "full"
            else "default:standard-pr-qualification"
        ),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    context = qualification_context(json.loads(args.event.read_text()))
    args.output.write_text(json.dumps(context, indent=2) + "\n")
    print(f"PR #{context['number']}: {context['qualificationMode']} ({context['reason']})")


if __name__ == "__main__":
    main()
