# Recording revision qualification

The producer may stage a revised WAV for a published edition. A revision uses a distinct object key, and the object transfer is idempotent at the edition boundary. Publication events retain the object key and checksum for diagnosis.

Run `mvn -B -ntp verify` for the Java reactor, `cargo test --manifest-path streamer/Cargo.toml` for playback handling, and `python3 tools/check-audio-fixture.py` for the pinned WAV fixture. The local object adapter test exercises path-style PUT/GET without external credentials. PostgreSQL integration runs when the test database variables are available.

The producer should inspect the edition manifest and object retrieval after replacing a recording before treating the replacement as listener-ready.
