package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class EditionAssemblerTest {
    static CandidateReview.Candidate candidate(String id, String title, int priority) {
        var attribution = new StoryNormalizer.Attribution("desk", "Desk", java.net.URI.create("https://example.invalid/desk"), "snapshot-1");
        var story = new StoryNormalizer.Story(id, title, "Summary", java.time.Instant.parse("2026-01-15T06:00:00Z"), StoryNormalizer.canonical(title), attribution);
        return new CandidateReview.Candidate(story, priority, CandidateReview.Decision.PENDING);
    }
    @Test void rejectsMissingTimezone() {
        assertThrows(IllegalArgumentException.class,()->new EditionAssembler().assemble(LocalDate.now(),null,List.of()));
    }
    @Test void assemblesOnlyApprovedLocalDate() {
        var approved=candidate("a","Approved",1).withDecision(CandidateReview.Decision.APPROVED);
        var pending=candidate("b","Pending",1);
        var edition=new EditionAssembler().assemble(LocalDate.of(2026,1,15),ZoneId.of("Europe/Bucharest"),List.of(approved,pending));
        assertEquals(1,edition.segments().size());
        assertEquals("a",edition.segments().get(0).story().id());
    }
    @Test void profilesAFixedEditionAssemblyWorkload() {
        var candidates = IntStream.range(0, 100)
                .mapToObj(index -> index % 4 == 0
                        ? candidate("story-" + index, "Approved " + index, 1)
                            .withDecision(CandidateReview.Decision.APPROVED)
                        : candidate("story-" + index, "Pending " + index, 1))
                .toList();
        var profile = new EditionAssembler().profile(LocalDate.of(2026, 1, 15),
                ZoneId.of("Europe/Bucharest"), candidates);
        assertEquals(new EditionAssembler.AssemblyProfile(100, 25), profile);
    }
}
