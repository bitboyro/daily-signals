# Installation and upgrade

Daily Signals installs PostgreSQL, a durable local object store, the Java API/listener on port 8151 and the Rust streamer on port 8152. The optional Kokoro profile adds local speech synthesis. The installation manifest at `deploy/install-manifest.json` is the machine-readable contract checked from a clean checkout.

## Runtime variables

| Variable | Default | Secret | Purpose |
| --- | --- | --- | --- |
| `DAILY_SIGNALS_BIND_ADDRESS` | `0.0.0.0` | no | API listener address inside the container |
| `DAILY_SIGNALS_WEB_ROOT` | `/app/web` | no | Built listener asset directory |
| `DAILY_SIGNALS_SETUP_PATH` | `/data/setup.json` | no | Durable first-run station configuration |
| `STREAMER_BIND_ADDRESS` | `0.0.0.0` | no | Streamer listener address inside the container |
| `STREAMER_PUBLIC_PREFIXES` | `editions/,fixtures/` | no | Object-key prefixes visible to listeners |
| `DAILY_SIGNALS_REVIEW_TOKENS` | unset | yes | Optional `token=ROLE` producer authorization pairs; supply through a secret store |
| `JWT_ISSUER_URI` | unset | yes | Keycloak realm issuer for producer bearer-token validation; HTTPS except loopback development |

The checked-in `runtime.env` contains only non-secret defaults. Do not add review tokens or provider credentials to it.

## Clean installation

```sh
./install.sh
```

The script checks prerequisites before changing anything, builds and starts the five-service Compose model, verifies API, listener and playback health, and writes `install-health.json`. The quick-start target is less than 30 minutes. When it finishes, open [http://127.0.0.1:8151/](http://127.0.0.1:8151/) and complete the guided source, voice and schedule setup.

Preview the exact commands without starting containers:

```sh
./install.sh --dry-run
```

To use local Kokoro, first provision the pinned model and voice assets described in [local Kokoro speech](kokoro.md), then run `./install.sh --profile kokoro`. The preflight refuses to start that profile when either asset is missing.

For manual diagnosis, run `python3 tools/startup_preflight.py --json`. The script reports missing tools, malformed variables, invalid Compose configuration and occupied ports without a Java stack trace.

## Upgrade and rollback

Before upgrading, run `python3 tools/upgrade-smoke.py`. It proves that the v0.1.0 runtime variables and v1 publication manifests remain supported, while the database migration suite checks populated and legacy data.

Keep the previous images and runtime configuration until post-deploy health evidence passes. Roll back both services together if health or playback fails. Database rollback follows the separate [database migration runbook](database-migration.md); never reverse a populated migration by editing schema state manually.

For detailed container behavior see [container runtime](../container-runtime.md). For production probe interpretation see [listener release operations](listener-release.md).

The optional self-hosted speech runtime has a separate approval and pinning procedure in [local Kokoro speech](kokoro.md).
