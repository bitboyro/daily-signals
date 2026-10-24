package ro.bitboy.dailysignals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CandidateReview {
    public enum Decision { PENDING, APPROVED, REJECTED }
    public record Candidate(StoryNormalizer.Story story, int sourcePriority, Decision decision) {
        public Candidate {
            if (story==null || decision==null || sourcePriority<0) throw new IllegalArgumentException("invalid candidate");
        }
        public Candidate withDecision(Decision updated) { return new Candidate(story,sourcePriority,updated); }
    }
    private final Map<String,Candidate> candidates=new LinkedHashMap<>();
    public void submit(Candidate candidate) {
        if(candidates.putIfAbsent(candidate.story().id(),candidate)!=null) throw new IllegalArgumentException("duplicate candidate id");
    }
    public void decide(String storyId, Decision decision) {
        if(decision==Decision.PENDING) throw new IllegalArgumentException("decision must be final");
        var original=candidates.get(storyId);
        if(original==null) throw new IllegalArgumentException("unknown candidate");
        candidates.put(storyId,original.withDecision(decision));
    }
    public List<Candidate> all() { return List.copyOf(candidates.values()); }
}
