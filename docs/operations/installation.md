# Installation and upgrade

Daily Signals installs two services from `deploy/compose.yaml`: the Java API/listener on port 8151 and the Rust streamer on port 8152. Both expose `GET /health`. The installation manifest at `deploy/install-manifest.json` is the machine-readable contract checked from a clean checkout.

## Runtime variables

| Variable | Default | Secret | Purpose |
| --- | --- | --- | --- |
| `DAILY_SIGNALS_BIND_ADDRESS` | `0.0.0.0` | no | API listener address inside the container |
| `DAILY_SIGNALS_WEB_ROOT` | `/app/web` | no | Built listener asset directory |
| `STREAMER_BIND_ADDRESS` | `0.0.0.0` | no | Streamer listener address inside the container |
| `STREAMER_PUBLIC_PREFIXES` | `editions/,fixtures/` | no | Object-key prefixes visible to listeners |
| `DAILY_SIGNALS_REVIEW_TOKENS` | unset | yes | Optional `token=ROLE` producer authorization pairs; supply through a secret store |

The checked-in `runtime.env` contains only non-secret defaults. Do not add review tokens or provider credentials to it.

## Clean installation

```sh
python3 tools/install-smoke.py
docker compose -f deploy/compose.yaml config --quiet
docker compose -f deploy/compose.yaml up -d --build
python3 tools/post-deploy-health.py --revision local-install
```

The streamer mounts the publication manifest and fixture recording read-only. A production deployment replaces those inputs with its published object-store integration.

## Upgrade and rollback

Before upgrading, run `python3 tools/upgrade-smoke.py`. It proves that the v0.1.0 runtime variables and v1 publication manifests remain supported, while the database migration suite checks populated and legacy data.

Keep the previous images and runtime configuration until post-deploy health evidence passes. Roll back both services together if health or playback fails. Database rollback follows the separate [database migration runbook](database-migration.md); never reverse a populated migration by editing schema state manually.

For detailed container behavior see [container runtime](../container-runtime.md). For production probe interpretation see [listener release operations](listener-release.md).

The optional self-hosted speech runtime has a separate approval and pinning procedure in [local Kokoro speech](kokoro.md).
