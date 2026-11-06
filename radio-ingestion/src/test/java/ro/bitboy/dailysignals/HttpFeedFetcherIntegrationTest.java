package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpFeedFetcherIntegrationTest {
    @Test void fetchesRecordedRssAndAtomOverLoopbackHttp() throws Exception {
        try (var fixture = new FeedFixtureServer()) {
            var registry = new SourceRegistry();
            registry.register(new SourceRegistry.Source("public-desk", "Public desk",
                    URI.create("https://feeds.example.test/rss.xml"), 10));
            registry.register(new SourceRegistry.Source("world-desk", "World desk",
                    URI.create("https://feeds.example.test/atom.xml"), 20));
            var fetcher = new HttpFeedFetcher(registry, Set.of("feeds.example.test"), fixture.transport());

            var rss = new String(fetcher.fetch("public-desk").body(), StandardCharsets.UTF_8);
            var atom = new String(fetcher.fetch("world-desk").body(), StandardCharsets.UTF_8);

            assertTrue(rss.contains("<rss version=\"2.0\">"));
            assertTrue(atom.contains("<feed xmlns=\"http://www.w3.org/2005/Atom\">"));
            assertEquals(1, fixture.requests("/rss.xml"));
            assertEquals(1, fixture.requests("/atom.xml"));
            assertEquals("public-001", new FeedCandidateParser().parse(fetcher.fetch("public-desk"),
                    Instant.parse("2026-11-06T10:00:00Z")).getFirst().id());
        }
    }
}
