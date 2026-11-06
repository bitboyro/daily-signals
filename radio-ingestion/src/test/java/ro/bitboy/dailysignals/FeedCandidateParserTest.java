package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeedCandidateParserTest {
    private final FeedCandidateParser parser = new FeedCandidateParser();

    @Test void parsesAndNormalizesRecordedRssAndAtom() throws Exception {
        var rss = parse("public-desk", "Public desk", "/feeds/public-desk.rss.xml");
        var atom = parse("world-desk", "World desk", "/feeds/world.atom.xml");

        assertEquals("public-001", rss.getFirst().id());
        assertEquals("library opens a new reading room", rss.getFirst().normalizedTitle());
        assertEquals(Instant.parse("2026-11-06T08:00:00Z"), rss.getFirst().publishedAt());
        assertEquals("world-001", atom.getFirst().id());
        assertEquals("world-desk", atom.getFirst().attribution().sourceId());
    }

    @Test void rejectsDoctypesAndUnknownDocuments() {
        var source = source("public-desk", "Public desk");
        assertThrows(Exception.class, () -> parser.parse(document(source,
                "<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rss><channel><item><guid>&x;</guid></item></channel></rss>"), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(document(source, "<html/>") , Instant.EPOCH));
    }

    private List<StoryNormalizer.Story> parse(String id, String name, String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            return parser.parse(new HttpFeedFetcher.FeedDocument(source(id, name), Map.of(), input.readAllBytes()),
                    Instant.parse("2026-11-06T10:00:00Z"));
        }
    }

    private static SourceRegistry.Source source(String id, String name) {
        return new SourceRegistry.Source(id, name, URI.create("https://feeds.example.test/" + id + ".xml"), 10);
    }

    private static HttpFeedFetcher.FeedDocument document(SourceRegistry.Source source, String xml) {
        return new HttpFeedFetcher.FeedDocument(source, Map.of(), xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
