package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class StoryNormalizerTest {
    @Test void preservesAttributionAndNormalizesTitle() throws Exception {
        var source=new SourceRegistry.Source("demo-civic-desk","Demo Civic Desk",
                URI.create("https://example.invalid/daily-signals/demo-civic-desk"),1);
        try (var input=getClass().getResourceAsStream("/fixtures/source-snapshot.json")) {
            var stories=new StoryNormalizer().normalize(new SnapshotParser().parse(input),source);
            assertEquals("community library extends saturday hours",stories.get(0).normalizedTitle());
            assertEquals("synthetic-2026-01-15-morning",stories.get(0).attribution().snapshotId());
            assertEquals(source.url(),stories.get(0).attribution().sourceUrl());
        }
    }
    @Test void canonicalTitleIgnoresPunctuationAndCase() {
        assertEquals("daily signals",StoryNormalizer.canonical("  DAILY—Signals! "));
    }
}
