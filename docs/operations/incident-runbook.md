# Incident runbook

This runbook covers listener-facing failures of a published edition. Each section names the symptom, the first check and the safe recovery. Record the edition ID, the manifest checksum and the exact source revision in the incident note before changing anything.

## Listeners cannot play an edition

1. Check both services: `GET /health` on the API (port 8151) and the streamer (port 8152). A failing streamer health check means the process did not start; read its log for manifest validation errors.
2. Request the edition audio directly from the streamer: `GET /editions/{editionId}/audio`.
   - `404` means the manifest points at an object the streamer cannot read. Compare `audioPath` in the manifest with the object store listing.
   - `403` means the object key is outside `STREAMER_PUBLIC_PREFIXES`. Check `deploy/runtime.env` before changing the object; the policy is the usual cause after a deployment change.
   - `416` for a range request means the player asked for bytes beyond the recorded size. Compare `sizeBytes` with the stored object.
3. Confirm the object checksum matches `checksumSha256`. A mismatch is a stale or partial upload: do not edit the manifest by hand. Re-publish through the outbox so the consumer writes a new manifest.

See [stream diagnostics](../stream-diagnostics.md) for status codes and the dual-process check.

## A publication did not reach listeners

The outbox retries a failed object transfer, and the consumer writes the manifest only after it applies the publication event. A missing manifest therefore usually means an undelivered event, not a lost recording.

1. Check the producer events for the edition: a `published` entry with no manifest means the consumer has not applied the event.
2. Redeliver pending events. Redelivery is safe: the consumer ignores event IDs it has already applied.
3. If the consumer rejects an event with `recording replaced before publication`, the events arrived out of order. Deliver the original `edition.published` event first.

## A replaced recording still plays the old audio

The manifest changes only after the replacement object transfers successfully. If listeners still hear the earlier recording:

1. Confirm the replacement transfer completed (`recording-transferred` producer event).
2. Confirm the streamer was restarted or reloaded with the new manifest; the streamer reads its manifest at startup.
3. Check client caches: the ETag is the recording checksum, so a changed recording always has a new ETag.

## After the incident

Keep the incident note with the edition ID, both checksums, the revisions involved and the commands run. Recording copies kept for investigation follow the [recording retention](recording-retention.md) rules.

Listener artifact rollback, post-deployment evidence and playback recovery are documented in [listener release operations](listener-release.md).
