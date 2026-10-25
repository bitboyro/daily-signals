#!/usr/bin/env python3
"""Audit public-annotations.yaml against the tracked tree and its history.

The annotation file is JSON-compatible YAML. Rules map fnmatch path patterns (where `*`
also crosses `/`, so a moved file keeps its rule) to feature relations or to one of the
explicit classifications. Checks: every tracked path is covered by an active rule; no
active rule is stale; retired rules match nothing; recorded introduction/retirement
revisions agree with first-parent history (null introducedIn means the
paths arrived in the commit that added the rule); intersections name known features and
existing tests. Merge-aware topology history keeps authored PR commits visible after a
normal merge. `--suggest` prints the revisions history implies; `--index` prints the
feature-to-path index for the current tree.
"""
import fnmatch
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RELATIONS = {'implements', 'calls', 'configures', 'guards', 'tests', 'documents'}
CLASSIFICATIONS = {'shared', 'generated', 'documentation', 'unresolved'}
CONFIDENCE = {'high', 'medium', 'low'}


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True)


def matches(rule, paths):
    return sorted(p for p in paths if any(fnmatch.fnmatchcase(p, pattern) for pattern in rule['paths']))


def history():
    """(sha, tracked paths) along topology-aware reachable history of HEAD, oldest first."""
    shas = git('rev-list', '--topo-order', '--reverse', 'HEAD').split()
    return [(sha, set(git('ls-tree', '-r', '--name-only', sha).split())) for sha in shas]


def rule_added_in(rule, commits):
    """First commit whose annotation file contains a rule with these exact paths."""
    for sha, paths in commits:
        if 'public-annotations.yaml' in paths:
            rules = json.loads(git('show', f'{sha}:public-annotations.yaml'))['rules']
            if any(r['paths'] == rule['paths'] for r in rules):
                return sha
    return None


def implied(rule, commits):
    present = [i for i, (_, paths) in enumerate(commits) if matches(rule, paths)]
    if not present:
        return None, None
    last = present[-1]
    retired = commits[last + 1][0] if last + 1 < len(commits) else None
    return commits[present[0]][0], retired


def main():
    annotations = json.loads((ROOT / 'public-annotations.yaml').read_text())
    features = annotations['features']
    errors, warnings = [], []
    for n, rule in enumerate(annotations['rules']):
        where = f"rule {n} {rule.get('paths')}"
        has_features, has_class = bool(rule.get('features')), 'classification' in rule
        if has_features == has_class:
            errors.append(f'{where}: needs exactly one of features or classification')
        for link in rule.get('features', []):
            if link.get('id') not in features or link.get('relation') not in RELATIONS:
                errors.append(f'{where}: unknown feature or relation {link}')
        if has_class and rule['classification'] not in CLASSIFICATIONS:
            errors.append(f"{where}: unknown classification {rule['classification']}")
        if rule.get('confidence') not in CONFIDENCE or not rule.get('provenance'):
            errors.append(f'{where}: confidence and provenance are required')

    tracked = set(git('ls-files').split())
    active = [r for r in annotations['rules'] if r.get('retiredIn') is None]
    covered = set()
    for rule in annotations['rules']:
        found = matches(rule, tracked)
        if rule.get('retiredIn') is None and not found:
            errors.append(f"stale rule {rule['paths']}: matches no tracked path; set retiredIn")
        if rule.get('retiredIn') is not None and found:
            errors.append(f"retired rule {rule['paths']} still matches {found[:3]}")
        if rule.get('retiredIn') is None:
            covered.update(found)
    for path in sorted(tracked - covered):
        errors.append(f'unmapped path {path}')
    unresolved = sorted({p for r in active if r.get('classification') == 'unresolved' for p in matches(r, tracked)})
    warnings += [f'unresolved path {p}' for p in unresolved]

    commits = history()
    suggestions = []
    for rule in annotations['rules']:
        introduced, retired = implied(rule, commits)
        suggestions.append({'paths': rule['paths'], 'introducedIn': introduced, 'retiredIn': retired})
        if introduced is None:
            # Paths staged in the commit being prepared have no history yet.
            if rule.get('introducedIn') is not None or not matches(rule, tracked):
                errors.append(f"rule {rule['paths']} matches nothing in history")
            continue
        # null introducedIn: the paths arrived in the same commit that added this rule (a file cannot name its own SHA).
        recorded = rule.get('introducedIn') or (introduced if rule_added_in(rule, commits) in (introduced, None) else None)
        if recorded != introduced or rule.get('retiredIn') != retired:
            errors.append(f"rule {rule['paths']}: history implies introducedIn {introduced[:12]} retiredIn "
                          f"{retired[:12] if retired else None}, recorded {str(rule.get('introducedIn'))[:12]} "
                          f"{str(rule.get('retiredIn'))[:12]}")

    for n, item in enumerate(annotations.get('intersections', [])):
        for feature in item['features']:
            if feature not in features:
                errors.append(f'intersection {n}: unknown feature {feature}')
        for test in item['tests']:
            if test['path'] not in tracked:
                errors.append(f"intersection {n}: missing test {test['path']}")

    if '--suggest' in sys.argv:
        print(json.dumps(suggestions, indent=1))
        return 0
    if '--index' in sys.argv:
        index = {}
        for rule in active:
            for link in rule.get('features', []):
                for path in matches(rule, tracked):
                    index.setdefault(link['id'], []).append({'path': path, 'relation': link['relation']})
        print(json.dumps(index, indent=1, sort_keys=True))
        return 0
    for warning in warnings:
        print('warning:', warning)
    for error in errors:
        print('error:', error, file=sys.stderr)
    if errors:
        return 1
    implemented = {link['id'] for r in active for link in r.get('features', []) if link['relation'] == 'implements'}
    print(f"{len(tracked)} paths covered by {len(active)} active rules; {len(annotations['rules']) - len(active)} retired; "
          f"{len(implemented)}/{len(features)} features implemented; {len(annotations.get('intersections', []))} intersections; "
          f"{len(unresolved)} unresolved")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
