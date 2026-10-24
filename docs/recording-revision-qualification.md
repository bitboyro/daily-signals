# Recording revision qualification

The producer may stage a revised WAV for a published edition. A revision uses a distinct object key, and the object transfer is idempotent at the edition boundary. Publication events retain the object key and checksum for diagnosis.

Run `mvn -B -ntp verify` for the Java reactor, `cargo test --manifest-path streamer/Cargo.toml` for playback handling, and `python3 tools/check-audio-fixture.py` for the pinned WAV fixture. The local object adapter test exercises path-style PUT/GET without external credentials. PostgreSQL integration runs when the test database variables are available.

The producer should inspect the edition manifest and object retrieval after replacing a recording before treating the replacement as listener-ready.

## Regeneration sequence

1. Render the corrected script and preserve its source revision.
2. Stage the resulting WAV under a new object key; do not overwrite the old audio.
3. Transfer the object. A failed transfer retains the staged replacement, and the previous publication remains playable.
4. Publish the new manifest with that key, byte count, and SHA-256 checksum. Check that the object retrieved at the manifest path has those exact bytes.
5. Retry a failed transfer from the outbox. Producer events record each successful object transfer for audit.

The Java `PublishOutboxTest` covers the stale-pointer case and transfer failure. The Rust streamer consumes the published manifest, so its playback checks remain part of release qualification.
