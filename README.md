# Daily Signals

A public radio experiment built progressively from a small Java service into a multi-language system: a Java 21 Maven reactor owns ingestion, editorial review, editions and publication; a Rust streamer serves published audio; a TypeScript client provides the listener and producer UI.

The repository history is intentionally authored as small, identifiable product changes so the companion optimizer experiment can replay exact revisions. The product does not require live news, voice, or cloud credentials for its reference checks.

## Build and run

- Java: `mvn -B -ntp verify`. Run the API with `java -cp radio-api/target/classes:radio-edition/target/classes:radio-editorial/target/classes:radio-ingestion/target/classes ro.bitboy.dailysignals.DailySignals [PORT]` (default `8151`). Set `DAILY_SIGNALS_WEB_ROOT=web/dist` to serve the listener UI at `/`.
- Rust: `cargo test --manifest-path streamer/Cargo.toml`; `streamer PORT MANIFEST_PATH AUDIO_ROOT` serves one published manifest.
- Web: `npm --prefix web ci`, then `npm --prefix web run build`, `npm --prefix web test`, and `npm --prefix web run e2e` (needs the Java build).
- Containers: `deploy/compose.yaml`; see [container runtime](docs/container-runtime.md).

The API serves `GET /health`, `GET /api/editions` (versioned edition list), `GET /editions/{editionId}/audio`, and the producer review queue at `/api/review-queue`. Review decisions require `Authorization: Bearer <token>` for a token configured in `DAILY_SIGNALS_REVIEW_TOKENS` (`token=ROLE` pairs, at least 24 characters); without that configuration the API refuses every review write. The retired `/editions/demo/audio` link redirects permanently to the demo edition. Shared contracts live in `contracts/`.

## Checks

`python3 tools/check-module-boundaries.py`, `check-fixtures.py`, `check-source-example.py`, `check-audio-fixture.py` and `check-docs.py` validate structure, fixtures and documentation. `check-annotations.py` audits `public-annotations.yaml`, which maps every tracked path to stable feature IDs or an explicit classification, and records when each rule's paths entered and left the history. After building Java, Rust and the web client, `dual-process-smoke.py`, `voice-playback-smoke.py` and `container-smoke.py` check the cross-process playback paths. PostgreSQL reference tests require the configured test database. Release qualification is described in [docs/release-qualification.md](docs/release-qualification.md).
