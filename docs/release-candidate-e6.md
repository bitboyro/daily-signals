# E6 producer and listener release candidate

E6 is the first candidate that qualifies the producer review workspace and the public listener as one deployable system. Its release boundary includes Java ingestion, editorial, edition, persistence and API modules; the Rust byte-range streamer; the TypeScript listener/producer client; the PostgreSQL reference schema; and the shared publication contracts.

## Acceptance evidence

A candidate is reviewable only when the hosted `release-qualification` job is green for its exact source SHA. The retained evidence must include Java and web JUnit reports, the Rust test log, the deterministic listener ZIP, and—in tag mode—the source-bound release manifest. The normal PR matrix is useful evidence but does not substitute for this full policy run.

The listener artifact must pass `python3 tools/package-listener.py --verify <archive>`. The deployed candidate must pass `tools/post-deploy-health.py`, including HTML and module delivery, catalog discovery, full audio, byte-range audio, ETag and immutable-cache checks.

## Known boundaries

E6 uses deterministic news and audio fixtures. External news sources, object storage and TTS accounts remain replaceable adapters and are not claimed as production-qualified. Review tokens are injected from the environment and are never retained in artifacts. A failed candidate is not promoted; operators roll back the matching listener, API and streamer set together.
