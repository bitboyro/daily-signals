# Optional local Kokoro speech

Fixture speech remains the deterministic default for development, tests and ordinary CI. Kokoro is an opt-in local provider; enabling it requires an operator to review the [third-party notices](../../deploy/kokoro/THIRD_PARTY_NOTICES.md), supply pinned artifacts and explicitly approve a manual workflow dispatch.

## Pinned runtime and model

The `kokoro` Compose profile pins Kokoro FastAPI v0.8.0 by OCI digest. `deploy/kokoro/model.lock.json` pins the Kokoro-82M repository revision, filename, SHA-256 and Apache-2.0 license. `DOWNLOAD_MODEL=false` prevents a runtime network fetch.

Obtain the model through the operator's approved artifact process, verify its checksum, and place it in a directory mounted through `KOKORO_MODEL_DIR`. Supply the reviewed voice packs through `KOKORO_VOICES_DIR`. Neither directory is committed.

## Static qualification

Ordinary CI runs only:

```sh
python3 tools/kokoro-integration-smoke.py
mvn -B -ntp -pl radio-edition -am -Dtest='FixtureVoiceProviderTest,Kokoro*Test,GeneratedAudioProvenanceTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

This validates pins, mounts, configuration, timeouts, voice selection and deterministic fixtures without pulling an image, loading weights or synthesizing speech.

## Operator-approved live check

1. Verify the actual packaged image and dependency licenses.
2. Set repository variables `KOKORO_MODEL_DIR` and `KOKORO_VOICES_DIR` for a self-hosted runner labeled `kokoro`.
3. Manually dispatch **Optional Kokoro integration** and check `operator_approved`.
4. Retain the generated WAV and its provider/model/voice provenance as release evidence.

Locally, the equivalent is `docker compose -f deploy/compose.yaml --profile kokoro up -d kokoro` followed by `python3 tools/kokoro-integration-smoke.py --live`. Stop the profile after qualification. Never enable it in ordinary pull-request or push jobs.
