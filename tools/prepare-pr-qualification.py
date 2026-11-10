#!/usr/bin/env python3
"""Create a stable, reviewable qualification decision from a pull-request event."""

import argparse
import fnmatch
import json
from pathlib import Path


FULL_QUALIFICATION_LABEL = "run-all-checks"


def _decision_lab_context(plan, required_policy):
    if plan is None:
        return None
    full_run = bool(plan.get("fullRun"))
    selected = sorted(set(plan.get("selected", [])))
    skipped = [] if full_run else sorted(set(plan.get("skipped", [])))
    required = sorted(set((required_policy or {}).get("requiredChecks", [])))
    conflicts = sorted(check for check in skipped
                       if check in required or check.split(":", 1)[-1] in required)
    allowed = (required_policy or {}).get("allowedWhenSelective", [])
    disallowed = sorted(check for check in required if not any(fnmatch.fnmatch(check, pattern) for pattern in allowed))
    return {
        "mode": plan.get("mode"),
        "fullRun": full_run,
        "selectedChecks": selected,
        "skippedChecks": skipped,
        "requiredStatus": {
            "protection": (required_policy or {}).get("protection", "unknown"),
            "requiredChecks": required,
            "allowedWhenSelective": allowed,
            "skippedRequiredChecks": conflicts,
            "disallowedRequiredChecks": disallowed,
            "compatible": not conflicts and not disallowed,
        },
    }


def qualification_context(event, plan=None, required_policy=None):
    pull_request = event.get("pull_request")
    if not isinstance(pull_request, dict):
        raise ValueError("GitHub pull_request event is required")
    labels = sorted(
        label["name"] for label in pull_request.get("labels", [])
        if isinstance(label, dict) and isinstance(label.get("name"), str)
    )
    mode = "full" if FULL_QUALIFICATION_LABEL in labels else "standard"
    context = {
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
    decision_lab = _decision_lab_context(plan, required_policy)
    if decision_lab is not None:
        context["decisionLab"] = decision_lab
    return context


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event", type=Path, required=True)
    parser.add_argument("--plan", type=Path)
    parser.add_argument("--required-policy", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    plan = json.loads(args.plan.read_text()) if args.plan else None
    policy = json.loads(args.required_policy.read_text()) if args.required_policy else None
    context = qualification_context(json.loads(args.event.read_text()), plan, policy)
    args.output.write_text(json.dumps(context, indent=2) + "\n")
    print(f"PR #{context['number']}: {context['qualificationMode']} ({context['reason']})")
    decision_lab = context.get("decisionLab")
    if decision_lab:
        print(f"Decision Lab: {len(decision_lab['selectedChecks'])} selected; "
              f"{len(decision_lab['skippedChecks'])} skipped; required-status compatible: "
              f"{decision_lab['requiredStatus']['compatible']}")
        if not decision_lab["requiredStatus"]["compatible"]:
            raise SystemExit("skipped jobs conflict with the required-status policy")


if __name__ == "__main__":
    main()
