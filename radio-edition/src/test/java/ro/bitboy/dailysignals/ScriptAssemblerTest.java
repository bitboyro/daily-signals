package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScriptAssemblerTest {
    @Test void retainsEditorialOrderAndAttributionIds() {
        var attribution = new StoryNormalizer.Attribution("desk", "Desk", URI.create("https://example.test"), "snapshot");
        var story = new StoryNormalizer.Story("s1", "Title", "Summary", Instant.parse("2026-01-15T08:00:00Z"), "title", attribution);
        var candidate = new CandidateReview.Candidate(story, 1, CandidateReview.Decision.APPROVED);
        var edition = new EditionAssembler.Edition(LocalDate.of(2026,1,15), ZoneId.of("UTC"), List.of(candidate));
        var script = new ScriptAssembler().assemble(edition);
        assertEquals(List.of("s1"), script.storyIds());
        assertEquals("Daily Signals for 2026-01-15.\nTitle. Summary\n", script.text());
    }
}
