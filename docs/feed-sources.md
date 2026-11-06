# Feed sources and retrieval policy

Daily Signals can ingest RSS 2.0 and Atom feeds over HTTPS. A source is active only when it is registered and its exact host is present in the operator allowlist. Subdomains are not implied, redirects are not followed, and tests use recorded loopback fixtures rather than public endpoints.

## Add a source

Configure each source with:

- a stable lowercase ID;
- a display name and HTTPS feed URL;
- editorial priority;
- the attribution text required by the publisher or licence;
- a minimum interval between requests.

Add the URL host separately to the allowlist. For example, allowing `feeds.example.org` does not allow `cdn.feeds.example.org` or `example.org`. Review the publisher's terms before enabling a source; the allowlist is a security boundary, not evidence that reuse is permitted.

## Retrieval limits

The default policy uses a five-second connect timeout, ten-second request timeout, and a 1 MiB response limit. Production configuration may choose stricter positive values. A response one byte over the configured limit is rejected. Non-200 responses fail the fetch, except a validated `304 Not Modified` response, which reuses the last successful document.

For successful responses, the fetcher retains `ETag` and `Last-Modified` and sends them as `If-None-Match` and `If-Modified-Since` on the next eligible request. The per-source minimum interval is enforced before any network call.

## Supported content

RSS entries require `guid`, `title`, `description`, and `pubDate`. Atom entries require `id`, `title`, `summary`, and `updated`. Both formats become the same normalized candidate model with source attribution. XML document types and external entities are disabled.

## Verification

Run the offline unit tests with:

```sh
mvn -B -ntp -pl radio-ingestion -am test
```

The `fetch-integration` CI job starts an in-process server bound to loopback, serves the recorded RSS and Atom documents under `radio-ingestion/src/test/resources/feeds`, and verifies HTTP retrieval and conditional requests. It does not need internet access or credentials.
