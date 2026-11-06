package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.charset.StandardCharsets;
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
        var fetcher = new HttpFeedFetcher(registry(), Set.of("FEEDS.EXAMPLE.TEST"), uri -> {
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
                uri -> { throw new AssertionError("transport must not be called"); });

        assertThrows(IllegalArgumentException.class, () -> fetcher.fetch("missing"));
        assertThrows(SecurityException.class, () -> fetcher.fetch("public-desk"));
    }

    @Test void rejectsNonSuccessfulResponses() {
        var fetcher = new HttpFeedFetcher(registry(), Set.of("feeds.example.test"),
                uri -> new HttpFeedFetcher.Response(503, Map.of(), new byte[0]));

        assertThrows(java.io.IOException.class, () -> fetcher.fetch("public-desk"));
    }
}
