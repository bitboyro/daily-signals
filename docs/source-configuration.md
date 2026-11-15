# Producer guide

The producer workspace turns reviewed source candidates into one approved, published edition. Open the listener, choose **Producer workspace**, and sign in through the configured OIDC provider. A local development installation may use a token supplied by the operator; the server maps credentials to roles, and the browser never chooses its own role.

## Review today's candidates

Open **Today's candidates** to read normalized stories with their source attribution, then use **Review queue** to approve or reject each item. Verify the headline, summary, publication time and source snapshot before approving. Rejected stories do not enter today's edition. The queue records reviewer, review time and revision; do not share credentials to make a decision appear to belong to someone else.

If two sources describe the same event, retain the candidate with the strongest provenance and reject the duplicate. If attribution or licensing is unclear, reject the story and ask the operator to correct the source configuration.

## Edit the script

Open **Edition script** after the candidate review is complete. Keep the narration factual, preserve necessary attribution and make transitions explicit. The editor accepts 1 to 20,000 printable characters and records a new revision when saved. Reload the saved revision before generating audio; never approve an unsaved browser draft.

## Check pronunciation

Open **Pronunciations** and enter one whole-token mapping per line as `term=spoken form`. Mappings are case-insensitive whole tokens: a short term must not rewrite the middle of a person's name. Save the lexicon, return to the script and read the affected sentence again before generating audio.

## Generate and approve audio

Choose **Generate audio preview**. The workspace reports the validated WAV sample rate, channel count and duration. Listen to the entire preview and compare it with the saved script. Do not approve clipped, silent, incomplete or stale audio; correct the script or pronunciation and generate a new preview.

Choose **Approve** only when the reviewed candidates, saved script and current preview agree. Approval records the actor, edition revision and time in the append-only history. A later script change requires a new approval.

## Publish

Choose **Publish** after approval. Keep the workspace open while it follows the persisted edition state:

- `READY` means approval completed but publication has not;
- `PUBLISHED` means the recording and manifest are available to listeners;
- `FAILED` means publication stopped and requires investigation.

After `PUBLISHED`, open today's listener edition and play its beginning and a byte-range seek. Do not repair a failed edition by editing database rows, objects or manifests. Give the operator the edition ID, current revision, approval actor and visible error so the [incident runbook](operations/incident-runbook.md) can be followed.

## Configure sources

The example in `examples/sources.json` is the pinned synthetic source used by the pilot. A source has a stable lowercase ID, a display name, an HTTPS URL and a nonnegative priority. The source URL must match the snapshot provenance before candidates enter review. The fetcher must retain the snapshot ID and capture time so a revision can be replayed offline. Production sources require permission and a separately configured fetch policy; the pilot runs only on licensed synthetic content.

Run `python3 tools/check-source-example.py` before copying the example into a deployment configuration.

Producers can edit sources in the web client; `web/src/source-editor.ts` applies the same identifier, HTTPS and priority rules before the configuration is saved.
