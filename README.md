# Daily Signals

A public radio experiment built progressively from a small Java service into a multi-language system. The first checkpoint provides a Java 21 Maven build, a health endpoint, and deterministic fixture playback.

Build with `mvn -B -ntp verify`. Run locally with `java -cp radio-api/target/classes ro.bitboy.dailysignals.DailySignals`; the optional argument is a port (default `8151`). Try `GET /health` and `GET /editions/demo/audio`.

The repository history is intentionally authored as small, identifiable product changes so the companion optimizer experiment can replay exact revisions. The product does not require live news, voice, or cloud credentials for its reference checks.

E1 qualification runs the Java reactor, the Rust streamer tests, the shared publication-manifest contract, and PostgreSQL reference tests in GitHub Actions. Locally, `mvn -B -ntp -o verify`, `cargo test --offline --manifest-path streamer/Cargo.toml`, and `python3 tools/dual-process-smoke.py` cover the available checks; PostgreSQL tests require the configured test database.
