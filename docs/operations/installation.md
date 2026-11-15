# Operator guide

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

## Configure the station

Open `http://127.0.0.1:8151/setup` after installation. Configure at least one licensed HTTPS source, choose the fixture or provisioned Kokoro voice, set the station time zone and daily publication window, then save. The setup endpoint persists revisions in PostgreSQL; do not edit its durable files or database rows by hand. Source validation and replay provenance are described in [source configuration](../source-configuration.md).

For an authenticated production deployment, supply review tokens or an OIDC issuer through the secret store, never `deploy/runtime.env`. Confirm a producer can sign in and a listener cannot reach producer endpoints before opening traffic.

## Back up and restore

Create a new checksummed backup directory:

```sh
python3 tools/backup.py --output backups/$(date -u +%Y%m%dT%H%M%SZ)
```

The manifest covers the PostgreSQL dump, object-store archive and every object checksum. Copy the completed directory to storage outside the installation host. Verify a backup without changing the live services:

```sh
python3 tools/restore.py backups/<timestamp> --verify-only
```

Restore only during a declared write-drain window:

```sh
python3 tools/restore.py backups/<timestamp>
python3 tools/post-deploy-health.py --revision restore-<timestamp> --report restore-health.json
```

Retain the backup and health report together. The nightly destructive round trip qualifies the mechanism, but it does not replace station-specific retention or restore rehearsals.

## Upgrade and rollback

For a station with mounted v0.x JSON and recordings, follow the complete [v0.x-to-v1 upgrade procedure](upgrade-v0-to-v1.md). It covers immutable backup, tagged-release rehearsal, idempotent database import, recording checksums, cutover validation and rollback.

Before any other upgrade, run:

```sh
python3 tools/upgrade-smoke.py
```

It proves that the v0.1.0 runtime variables and v1 publication manifests remain supported, while the database migration suite checks populated and legacy data.

Keep the previous images and runtime configuration until post-deploy health evidence passes. Roll back both services together if health or playback fails. Database rollback follows the separate [database migration runbook](database-migration.md); never reverse a populated migration by editing schema state manually.

## Monitor and respond

Poll API `/actuator/health` and streamer `/health`. Scrape API `/actuator/prometheus` and streamer `/metrics`; alert on service availability, streamer client errors and unexpected request or byte-rate changes. Structured logs from both services include `timestamp`, `level`, `service`, `version`, `scId`, `class` and `message`, so one correlation ID can be followed across playback.

After every deployment, retain the machine-readable result from:

```sh
python3 tools/post-deploy-health.py --revision <deployed-sha> --report post-deploy-health.json
```

For listener failures, missing objects or stuck publication, follow the [incident runbook](incident-runbook.md). For storage/provider recovery and bounded retries, use [provider and object-store incidents](provider-storage-incidents.md). Nightly and weekly evidence interpretation is covered by [nightly qualification](nightly-qualification.md).

For detailed container behavior see [container runtime](../container-runtime.md). For production probe interpretation see [listener release operations](listener-release.md).

The optional self-hosted speech runtime has a separate approval and pinning procedure in [local Kokoro speech](kokoro.md).
