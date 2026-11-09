# Daily Signals

A public radio experiment built progressively from a small Java service into a multi-language system: a Java 21 Maven reactor owns ingestion, editorial review, editions and publication; a Rust streamer serves published audio; a TypeScript client provides the listener and producer UI.

The repository history is intentionally authored as small, identifiable product changes so the companion optimizer experiment can replay exact revisions. The product does not require live news, voice, or cloud credentials for its reference checks.

## Build and run

- Java: `mvn -B -ntp verify`. Run the API with `java -cp radio-api/target/classes:radio-edition/target/classes:radio-editorial/target/classes:radio-ingestion/target/classes ro.bitboy.dailysignals.DailySignals [PORT]` (default `8151`). Set `DAILY_SIGNALS_WEB_ROOT=web/dist` to serve the listener UI at `/`.
- Rust: `cargo test --manifest-path streamer/Cargo.toml`; `streamer PORT MANIFEST_PATH AUDIO_ROOT` serves one published manifest.
- Web: `npm --prefix web ci`, then `npm --prefix web run build`, `npm --prefix web test`, and `npm --prefix web run e2e` (needs the Java build).
- Containers: `deploy/compose.yaml`; see [container runtime](docs/container-runtime.md) and [listener release operations](docs/operations/listener-release.md).

The API serves `GET /health`, `GET /api/editions` (versioned edition list), `GET /editions/{editionId}/audio`, and the producer review queue at `/api/review-queue`. Review decisions require a validated Keycloak bearer token when `JWT_ISSUER_URI` is set. Tokens configured in `DAILY_SIGNALS_REVIEW_TOKENS` (`token=ROLE` pairs, at least 24 characters) remain a local-development fallback; without either configuration the API refuses every review write. The retired `/editions/demo/audio` link redirects permanently to the demo edition. Shared contracts live in `contracts/`.

## Producer flow

Open **Produce**, sign in with the station identity provider (or enter a local development token), and work through the tabs:

1. Review today's candidates and approve the stories that belong in the edition.
2. Edit and save the edition script. Add whole-token pronunciation fixes as `term=spoken form` entries.
3. Generate the audio preview. The workspace shows the validated sample rate, channel count and duration; listen before approving.
4. Approve the edition, then publish it. The client follows the persisted edition state until it reaches `PUBLISHED` or `FAILED`.

The fixture voice is the offline default. Operators may select the local Kokoro adapter with `DAILY_SIGNALS_VOICE_PROVIDER=kokoro` plus the endpoint, immutable model revision, model SHA-256 and supported voice ID described in the [Kokoro operations guide](docs/operations/kokoro.md). Incomplete configuration fails closed instead of silently changing providers.

## Checks

`python3 tools/check-module-boundaries.py`, `check-fixtures.py`, `check-source-example.py`, `check-audio-fixture.py` and `check-docs.py` validate structure, fixtures and documentation. `check-annotations.py` audits `public-annotations.yaml`, which maps every tracked path to stable feature IDs or an explicit classification, and records when each rule's paths entered and left the history. After building Java, Rust and the web client, `dual-process-smoke.py`, `voice-playback-smoke.py` and `container-smoke.py` check the cross-process playback paths. PostgreSQL reference tests require the configured test database. Release qualification is described in [docs/release-qualification.md](docs/release-qualification.md), [nightly qualification](docs/operations/nightly-qualification.md), and deployment setup in [docs/operations/installation.md](docs/operations/installation.md).

Pull requests retain a qualification-context artifact. The `run-all-checks` label is a visible human request for full qualification; see [the pull request policy](docs/pull-request-qualification.md).

### Decision Lab

Every pull request is planned by Decision Lab. During the initial shadow period the plan is advisory: all existing verification jobs still run, and the resulting evidence is used to audit what would have been selected. The `run-all-checks` label is an explicit human override that requests every check regardless of the calculated plan.

The repository's `.decision-lab.yml`, pull-request plan comment, workflow summaries and plan/audit artifacts are public. They contain selected check names and explanations, not credentials. The Decision Lab executable remains in its private release repository and is downloaded by a pinned version and SHA-256 using a read-only Actions secret; its token value is never written to comments, logs or artifacts.

Dependency, secret and container declarations follow the [supply-chain policy](docs/dependency-security-policy.md).
RSS and Atom source registration, attribution and retrieval limits are described in the [feed source policy](docs/feed-sources.md).
