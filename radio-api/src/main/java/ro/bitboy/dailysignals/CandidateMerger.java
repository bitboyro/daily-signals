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
            // Keep the most recent matching entry after extending matching to headlines.
            byHeadline.put(candidate.story().normalizedTitle(),candidate);
        }
        return List.copyOf(byHeadline.values());
    }
}
