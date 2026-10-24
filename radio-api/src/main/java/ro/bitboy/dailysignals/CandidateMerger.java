package ro.bitboy.dailysignals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public final class CandidateMerger {
    public List<CandidateReview.Candidate> merge(List<CandidateReview.Candidate> input) {
        var ordered=new ArrayList<>(input);
        ordered.sort(Comparator.comparingInt(CandidateReview.Candidate::sourcePriority)
                .thenComparing(Comparator.comparing((CandidateReview.Candidate c)->c.story().publishedAt()).reversed()));
        var seen=new HashSet<String>();
        return ordered.stream().filter(c->seen.add(c.story().id())).toList();
    }
}
