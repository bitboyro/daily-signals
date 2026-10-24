package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CandidateReviewTest {
    static CandidateReview.Candidate candidate(String id,String title,int priority) {
        var attribution=new StoryNormalizer.Attribution("desk","Desk",URI.create("https://example.invalid/desk"),"snapshot-1");
        var story=new StoryNormalizer.Story(id,title,"Summary",Instant.parse("2026-01-15T06:00:00Z"),StoryNormalizer.canonical(title),attribution);
        return new CandidateReview.Candidate(story,priority,CandidateReview.Decision.PENDING);
    }
    @Test void reviewRequiresExplicitDecision() {
        var review=new CandidateReview(); review.submit(candidate("a","One",1));
        assertEquals(CandidateReview.Decision.PENDING,review.all().get(0).decision());
        review.decide("a",CandidateReview.Decision.APPROVED);
        assertEquals(CandidateReview.Decision.APPROVED,review.all().get(0).decision());
        assertThrows(IllegalArgumentException.class,()->review.decide("missing",CandidateReview.Decision.REJECTED));
    }
}
