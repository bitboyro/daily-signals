package ro.bitboy.dailysignals;

import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class StoryNormalizer {
    public record Attribution(String sourceId, String sourceName, URI sourceUrl, String snapshotId) {}
    public record Story(String id, String title, String summary, Instant publishedAt,
                        String normalizedTitle, Attribution attribution) {}

    public List<Story> normalize(SourceSnapshotParser.Snapshot snapshot, SourceRegistry.Source registered) {
        if (!snapshot.sourceId().equals(registered.id()) || !snapshot.sourceUrl().equals(registered.url().toString())) {
            throw new IllegalArgumentException("snapshot source does not match configured source");
        }
        var attribution = new Attribution(registered.id(),registered.name(),registered.url(),snapshot.snapshotId());
        return snapshot.stories().stream().map(raw -> new Story(raw.id(),raw.title().strip(),raw.summary().strip(),
                raw.publishedAt(),canonical(raw.title()),attribution)).toList();
    }
    public static String canonical(String title) {
        return Normalizer.normalize(title,Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+"," ").strip().replaceAll(" +"," ");
    }
}
