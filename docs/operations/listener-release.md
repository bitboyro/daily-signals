# Listener release operations

## Build and verify

Run `npm --prefix web ci` and `npm --prefix web run release:package`. The command builds the listener and creates `web/artifacts/daily-signals-listener-<version>.zip`. Every archive entry has a fixed timestamp and permission mode, and `listener-package.json` records the package version plus the SHA-256 digest of every served file. Verify an artifact before promotion with `python3 tools/package-listener.py --verify <archive>`.

Promote the exact retained GitHub Actions artifact; do not rebuild it from a tag or copy an unpacked directory. Record the source SHA, workflow run, archive digest and target environment in the deployment note.

## Deploy and prove health

Deploy the API listener image and streamer from the same qualified source revision. Then run:

```sh
python3 tools/post-deploy-health.py \
  --api-base https://radio.example/api-origin \
  --stream-base https://radio.example/stream-origin \
  --report post-deploy-health.json
```

The probe checks both health endpoints, the listener HTML/module/style assets, the newest edition, complete audio, byte-range playback, cache policy and ETag. Attach its JSON evidence to the deployment note.

## Listener recovery

The player never autoplays. A failed stream offers one explicit retry, reloads the media element before playing, and keeps the recording download visible. After the retry is exhausted, preserve the download link and direct listeners to try again later; do not create an automatic retry loop.

## Rollback and handoff

If health evidence fails, stop promotion and redeploy the previous qualified listener archive and matching API/streamer images. Re-run the probe against the rollback. For an active listener incident, follow the [incident runbook](incident-runbook.md) and hand off the edition ID, source SHA, artifact digest, failed check, probe report and rollback result.
