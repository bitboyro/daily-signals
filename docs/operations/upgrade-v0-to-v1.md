# Upgrade from v0.x to v1

This procedure moves a v0.x station from mounted JSON and WAV files to the v1 PostgreSQL and S3-compatible storage model. Keep the v0.x release, its state directory and its database snapshot until every validation below passes.

## Inputs and safety boundary

The source directory must contain only the legacy files that exist for the station:

```text
state/
  station-settings.json
  editions.json
  recordings/
    <edition-id>.wav
```

The importers reject symlinks, oversized documents, empty recordings, unsafe names and non-WAV recording names. They never replace an existing database row. A recording is uploaded to `editions/<edition-id>.wav`; if that key already contains different bytes, the upgrade stops instead of overwriting it.

Before changing the station:

1. Stop producer writes and record the v0.x image tag and Git revision.
2. Copy the complete state directory to immutable backup storage.
3. Take a database-native snapshot and record its identifier.
4. Preserve the previous runtime configuration and container images.
5. Verify the source and backup trees have identical file counts and SHA-256 checksums.

Do not point a rehearsal at the production database or production object bucket.

## Rehearse the tagged release

From a clean v1 checkout with full Git history, materialize representative state from the actual `v0.2.0` tag:

```sh
python3 tools/upgrade-smoke.py --prepare-state target/v0-upgrade
```

The generated `target/v0-upgrade/state/provenance.json` must name `v0.2.0` and a 40-character revision. Run the same upgrade contract used by CI against an isolated PostgreSQL database:

```sh
export DAILY_SIGNALS_TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:5432/daily_signals_test'
export DAILY_SIGNALS_TEST_DATABASE_USER='daily_signals'
export DAILY_SIGNALS_TEST_DATABASE_PASSWORD='replace-me'
mvn -B -ntp -pl radio-persistence -am test \
  -Dtest='V0ReleaseUpgradePostgresTest,PopulatedMigrationPostgresTest,LegacyMigrationPostgresTest' \
  -Ddaily.signals.v0.state="$PWD/target/v0-upgrade/state" \
  -Dsurefire.failIfNoSpecifiedTests=false
```

This proves the tagged settings and edition history import, recording upload and checksum comparison, populated-schema migration and idempotent rerun. A skipped `V0ReleaseUpgradePostgresTest` is not a successful rehearsal; check the database variables and state property.

## Production migration order

Use the same reviewed components exercised by the rehearsal: `V0FileStateImporter` for station settings and editions, then `V0RecordingImporter` with the configured `LocalS3ObjectAdapter` for recordings.

1. Keep producer writes stopped and apply the v1 database migrations.
2. Import `station-settings.json` and `editions.json` in one controlled database session.
3. Record the importer result counts. Existing rows must be reported as already present, not modified.
4. Import `recordings/*.wav` into the v1 object bucket.
5. Require the importer to read every uploaded object back and match its SHA-256 checksum.
6. Run both importers a second time. The database importer must add zero rows and the recording importer must report every object as already present.
7. Start the v1 API and streamer, but keep producer writes stopped until validation passes.

## Validate and cut over

Compare the v0.x source with v1 before resuming writes:

- station source count, voice and schedule match;
- edition count, identifiers, dates, status and revision match;
- every legacy WAV has one `editions/<edition-id>.wav` object;
- source and object SHA-256 checksums match;
- API health, listener catalog, playback byte ranges and streamer health pass;
- no importer collision or checksum error appears in structured logs.

Run `python3 tools/post-deploy-health.py --revision <v1-sha> --report upgrade-health.json` and retain the report with the snapshot identifier and importer counts. Resume producer writes only after the report passes.

## Rollback

Before writes resume, rollback is a traffic and storage decision: stop v1, restore the saved v0.x configuration and images, and point v0.x at the untouched mounted state. The import is additive, so copied objects may remain quarantined until the incident is understood.

After v1 writes resume, do not start v0.x against the newer live state. Stop writes, preserve the failed v1 state for investigation, restore the recorded database snapshot and mounted-state backup together, restore the previous images and configuration, then run the previous release's health and playback checks. Follow the [database migration runbook](database-migration.md) for schema rollback boundaries.

Never delete the backup, old images or imported objects merely because the first health probe passes. Release them only after the station's normal observation window completes.
