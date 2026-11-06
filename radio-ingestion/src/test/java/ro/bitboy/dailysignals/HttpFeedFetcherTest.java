package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpFeedFetcherTest {
    private static SourceRegistry registry() {
        var registry = new SourceRegistry();
        registry.register(new SourceRegistry.Source("public-desk", "Public desk",
                URI.create("https://feeds.example.test/news.xml"), 10));
        return registry;
    }

    @Test void fetchesARegisteredSourceThroughAnInjectedOfflineTransport() throws Exception {
        var requested = new AtomicReference<URI>();
        var fetcher = new HttpFeedFetcher(registry(), Set.of("FEEDS.EXAMPLE.TEST"), (uri, headers) -> {
            requested.set(uri);
            return new HttpFeedFetcher.Response(200, Map.of("content-type", List.of("application/rss+xml")),
                    "<rss/>".getBytes(StandardCharsets.UTF_8));
        });

        var document = fetcher.fetch("public-desk");

        assertEquals(URI.create("https://feeds.example.test/news.xml"), requested.get());
        assertEquals("public-desk", document.source().id());
        assertArrayEquals("<rss/>".getBytes(StandardCharsets.UTF_8), document.body());
    }

    @Test void rejectsUnknownSourcesAndHostsBeforeCallingTheNetwork() {
        var fetcher = new HttpFeedFetcher(registry(), Set.of("other.example.test"),
                (uri, headers) -> { throw new AssertionError("transport must not be called"); });

        assertThrows(IllegalArgumentException.class, () -> fetcher.fetch("missing"));
        assertThrows(SecurityException.class, () -> fetcher.fetch("public-desk"));
    }

    @Test void rejectsNonSuccessfulResponses() {
        var fetcher = new HttpFeedFetcher(registry(), Set.of("feeds.example.test"),
                (uri, headers) -> new HttpFeedFetcher.Response(503, Map.of(), new byte[0]));

        assertThrows(java.io.IOException.class, () -> fetcher.fetch("public-desk"));
    }

    @Test void reusesTheCachedDocumentForAConditionalNotModifiedResponse() throws Exception {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var seen = new AtomicReference<Map<String, String>>();
        var fetcher = new HttpFeedFetcher(registry(), Set.of("feeds.example.test"), (uri, headers) -> {
            seen.set(headers);
            if (attempts.getAndIncrement() == 0) {
                return new HttpFeedFetcher.Response(200, Map.of(
                        "ETag", List.of("\"feed-v1\""),
                        "Last-Modified", List.of("Fri, 06 Nov 2026 08:00:00 GMT")),
                        "<rss/>".getBytes(StandardCharsets.UTF_8));
            }
            return new HttpFeedFetcher.Response(304, Map.of(), new byte[0]);
        });

        var first = fetcher.fetch("public-desk");
        var second = fetcher.fetch("public-desk");

        assertArrayEquals(first.body(), second.body());
        assertEquals("\"feed-v1\"", seen.get().get("If-None-Match"));
        assertEquals("Fri, 06 Nov 2026 08:00:00 GMT", seen.get().get("If-Modified-Since"));
    }

    @Test void enforcesBodyLimitAndConfiguredSourceRate() throws Exception {
        var registry = new SourceRegistry();
        registry.register(new SourceRegistry.Source("public-desk", "Public desk",
                URI.create("https://feeds.example.test/news.xml"), 10,
                new SourceRegistry.Terms("Public desk / CC BY 4.0", Duration.ofMinutes(15))));
        var clock = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC);
        var limited = new HttpFeedFetcher(registry, Set.of("feeds.example.test"),
                new HttpFeedFetcher.Policy(Duration.ofSeconds(1), Duration.ofSeconds(2), 4), clock,
                (uri, headers) -> new HttpFeedFetcher.Response(200, Map.of(), new byte[5]));
        assertThrows(java.io.IOException.class, () -> limited.fetch("public-desk"));

        var allowed = new HttpFeedFetcher(registry, Set.of("feeds.example.test"),
                new HttpFeedFetcher.Policy(Duration.ofSeconds(1), Duration.ofSeconds(2), 100), clock,
                (uri, headers) -> new HttpFeedFetcher.Response(200, Map.of(), new byte[1]));
        allowed.fetch("public-desk");
        assertThrows(java.io.IOException.class, () -> allowed.fetch("public-desk"));
        assertEquals("Public desk / CC BY 4.0", registry.get("public-desk").terms().attribution());
    }
}
