#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_dir"

profile="base"
dry_run=false
while (($#)); do
  case "$1" in
    --profile)
      [[ $# -ge 2 ]] || { echo "--profile requires base or kokoro" >&2; exit 2; }
      profile="$2"; shift 2 ;;
    --dry-run) dry_run=true; shift ;;
    -h|--help)
      echo "Usage: ./install.sh [--profile base|kokoro] [--dry-run]"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done
[[ "$profile" == "base" || "$profile" == "kokoro" ]] || {
  echo "profile must be base or kokoro" >&2; exit 2;
}

compose=(docker compose --env-file deploy/runtime.env -f deploy/compose.yaml)
[[ "$profile" == "kokoro" ]] && compose+=(--profile kokoro)

if $dry_run; then
  python3 tools/startup_preflight.py --profile "$profile" --configuration-only
  printf 'would run:'; printf ' %q' "${compose[@]}" up -d --build; printf '\n'
  echo "would verify: python3 tools/post-deploy-health.py --revision local-install --report install-health.json"
  exit 0
fi

SECONDS=0
python3 tools/startup_preflight.py --profile "$profile"
echo "Starting the Daily Signals $profile profile..."
if ! "${compose[@]}" up -d --build; then
  echo "Startup failed. Recent service logs:" >&2
  "${compose[@]}" logs --tail 100 >&2 || true
  exit 1
fi
if ! python3 tools/post-deploy-health.py --revision local-install --report install-health.json; then
  echo "Health verification failed. Recent service logs:" >&2
  "${compose[@]}" logs --tail 100 >&2 || true
  exit 1
fi

elapsed=$SECONDS
if ((elapsed > 1800)); then
  echo "Installation succeeded but exceeded the 30-minute quick-start target (${elapsed}s)." >&2
  exit 1
fi
echo "Daily Signals is ready in ${elapsed}s: http://127.0.0.1:8151/"
echo "Open the station setup page there to choose sources, voice and schedule."
