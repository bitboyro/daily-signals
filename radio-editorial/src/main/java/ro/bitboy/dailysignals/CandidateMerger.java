package ro.bitboy.dailysignals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public final class CandidateMerger {
    public List<CandidateReview.Candidate> mergeApproved(List<CandidateReview.Candidate> input) {
        return merge(input.stream().filter(c -> c.decision()==CandidateReview.Decision.APPROVED).toList());
    }
    public List<CandidateReview.Candidate> merge(List<CandidateReview.Candidate> input) {
        var ordered=new ArrayList<>(input);
        ordered.sort(Comparator.comparingInt(CandidateReview.Candidate::sourcePriority)
                .thenComparing(Comparator.comparing((CandidateReview.Candidate c)->c.story().publishedAt()).reversed()));
        var byHeadline=new LinkedHashMap<String,CandidateReview.Candidate>();
        for (var candidate : ordered) {
            // Input is ranked before deduplication; preserve the first, best attributed candidate.
            byHeadline.putIfAbsent(duplicateKey(candidate),candidate);
        }
        return List.copyOf(byHeadline.values());
    }
    private static String duplicateKey(CandidateReview.Candidate candidate) {
        return candidate.story().normalizedTitle();
    }
}
