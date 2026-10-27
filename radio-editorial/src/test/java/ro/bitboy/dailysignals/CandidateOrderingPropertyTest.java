package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CandidateOrderingPropertyTest {
    @Test void mergeIsInvariantAcrossEveryPermutationOfFiveCandidates() {
        var candidates = List.of(
                CandidateReviewTest.candidate("a", "Alpha transit", 3),
                CandidateReviewTest.candidate("b", "Bravo weather", 1),
                CandidateReviewTest.candidate("c", "Charlie council", 2),
                CandidateReviewTest.candidate("d", "Delta culture", 5),
                CandidateReviewTest.candidate("e", "Echo science", 4));
        var expected = new CandidateMerger().merge(candidates).stream().map(c -> c.story().id()).toList();
        var permutations = new ArrayList<List<CandidateReview.Candidate>>();
        permute(new ArrayList<>(candidates), 0, permutations);
        assertEquals(120, permutations.size());
        for (var permutation : permutations) {
            assertEquals(expected, new CandidateMerger().merge(permutation).stream().map(c -> c.story().id()).toList());
        }
    }

    private static void permute(List<CandidateReview.Candidate> items, int index,
                                List<List<CandidateReview.Candidate>> result) {
        if (index == items.size()) {
            result.add(List.copyOf(items));
            return;
        }
        for (int current = index; current < items.size(); current++) {
            java.util.Collections.swap(items, index, current);
            permute(items, index + 1, result);
            java.util.Collections.swap(items, index, current);
        }
    }
}
