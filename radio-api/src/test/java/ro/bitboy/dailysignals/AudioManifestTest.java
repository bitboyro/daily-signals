package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AudioManifestTest {
    @Test void linksFixtureAudioToApprovedStory() {
        var a=CandidateReviewTest.candidate("a","Alpha",1).withDecision(CandidateReview.Decision.APPROVED);
        var edition=new EditionAssembler().assemble(LocalDate.of(2026,1,15),ZoneId.of("Europe/Bucharest"),List.of(a));
        var manifest=AudioManifest.fixture(edition);
        assertEquals(List.of("a"),manifest.storyIds());
        assertEquals("/editions/demo/audio",manifest.audioUri().toString());
    }
}
