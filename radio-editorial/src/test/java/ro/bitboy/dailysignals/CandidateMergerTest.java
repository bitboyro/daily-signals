package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CandidateMergerTest {
    @Test void equivalentTitlesAcrossSourcesKeepHighestPriorityAttribution() {
        var high=CandidateReviewTest.candidate("first","Morning transit update",1);
        var low=CandidateReviewTest.candidate("second","MORNING—transit update!",8);
        assertEquals("first",new CandidateMerger().merge(List.of(low,high)).get(0).story().id());
    }
    @Test void reviewPathExcludesPendingCandidate() {
        var pending=CandidateReviewTest.candidate("p","Pending",1);
        var approved=CandidateReviewTest.candidate("a","Approved",1).withDecision(CandidateReview.Decision.APPROVED);
        assertEquals(List.of(approved),new CandidateMerger().mergeApproved(List.of(pending,approved)));
    }
    @Test void duplicateStoryIdKeepsFirstCandidate() {
        var high=CandidateReviewTest.candidate("shared","A civic update",1);
        var low=CandidateReviewTest.candidate("shared","A civic update",9);
        assertSame(high,new CandidateMerger().merge(List.of(high,low)).get(0));
    }
    @Test void higherPrioritySourceWinsSameStory() {
        var low=CandidateReviewTest.candidate("shared","A civic update",9);
        var high=CandidateReviewTest.candidate("shared","A civic update",1);
        assertSame(high,new CandidateMerger().merge(List.of(low,high)).get(0));
    }
    @Test void keepsDistinctStoriesInInputOrderWhenTimestampsMatch() {
        var a=CandidateReviewTest.candidate("a","Alpha",1);
        var b=CandidateReviewTest.candidate("b","Bravo",1);
        assertEquals(List.of(a,b),new CandidateMerger().merge(List.of(a,b)));
    }
}
