# Provider and object-store incidents

These examples describe bounded recovery. Record the source revision, edition ID, attempt count and timestamps before changing a retry limit. Never remove the retry budget during an incident: an unavailable dependency must not turn one failure into a request storm.

## Source provider outage

Example: at 06:00 UTC a source fetch fails. `ProviderRecoveryPolicy` permits four total attempts with delays of 2, 4 and 5 seconds. After failure four, fetching stops and the edition stays incomplete for operator review.

1. Confirm that the error is a provider response or timeout, rather than malformed source content.
2. Read the failed-attempt count from the structured log and verify the next delay against the configured policy.
3. If the attempt budget is exhausted, keep the last accepted source snapshot unchanged. Do not silently publish stale input as current news.
4. When the provider recovers, start a new bounded fetch cycle and record which snapshot was accepted.

## Object-store timeout

Example: a recording upload times out twice and succeeds on attempt three. `RecoveringObjectWriter` retries only I/O failures. An interrupted transfer stops immediately, and a shared `RetryBudget` caps attempts across concurrent editions.

1. Confirm that the publication outbox still reports the edition as pending. A timed-out transfer must not create a publication manifest.
2. Compare aggregate attempts with the shared fixed-window budget. If it is exhausted, wait for the next window instead of manually starting parallel retries.
3. After a successful transfer, verify there is exactly one publication event and that the object checksum matches the manifest.
4. If every bounded attempt fails, retain the pending item and investigate the object-store endpoint. Do not mark the edition published by editing state or manifests.

Run the focused contracts locally with:

```sh
mvn -B -ntp -pl radio-ingestion test -Dtest=ProviderRecoveryPolicyTest
mvn -B -ntp -pl radio-edition -am -Dtest='ObjectStoreTimeoutRecoveryTest,RetryStormProtectionTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

For listener symptoms after a successful transfer, continue with the [incident runbook](incident-runbook.md).
