package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CandidateMergerTest {
    @Test void duplicateStoryIdKeepsFirstCandidate() {
        var high=CandidateReviewTest.candidate("shared","A civic update",1);
        var low=CandidateReviewTest.candidate("shared","A civic update",9);
        assertSame(high,new CandidateMerger().merge(List.of(high,low)).get(0));
    }
    @Test void keepsDistinctStoriesInInputOrderWhenTimestampsMatch() {
        var a=CandidateReviewTest.candidate("a","Alpha",1);
        var b=CandidateReviewTest.candidate("b","Bravo",1);
        assertEquals(List.of(a,b),new CandidateMerger().merge(List.of(a,b)));
    }
}
