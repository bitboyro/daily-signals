package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AudioManifestTest {
    @Test void linksFixtureAudioToApprovedStory() {
        var a=new CandidateReview.Candidate(new StoryNormalizer.Story("a", "Alpha", "summary", java.time.Instant.parse("2026-01-15T12:00:00Z"), "alpha", new StoryNormalizer.Attribution("source", "Source", java.net.URI.create("https://example.test"), "snapshot")), 1, CandidateReview.Decision.PENDING).withDecision(CandidateReview.Decision.APPROVED);
        var edition=new EditionAssembler().assemble(LocalDate.of(2026,1,15),ZoneId.of("Europe/Bucharest"),List.of(a));
        var manifest=AudioManifest.fixture(edition);
        assertEquals(List.of("a"),manifest.storyIds());
        assertEquals("/editions/demo/audio",manifest.audioUri().toString());
    }
}
