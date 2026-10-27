package ro.bitboy.dailysignals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class EditionAssembler {
    public record Edition(LocalDate airDate, ZoneId zone, List<CandidateReview.Candidate> segments) {
        public Edition { segments=List.copyOf(segments); }
    }
    /** Deterministic workload evidence; elapsed time is deliberately measured outside the product model. */
    public record AssemblyProfile(int candidatesScanned, int segmentsSelected) {
        public AssemblyProfile {
            if (candidatesScanned < 0 || segmentsSelected < 0 || segmentsSelected > candidatesScanned)
                throw new IllegalArgumentException("invalid assembly profile");
        }
    }
    public Edition assemble(LocalDate day, ZoneId zone, List<CandidateReview.Candidate> candidates) {
        if(day==null || zone==null) throw new IllegalArgumentException("date and zone required");
        var selected=candidates.stream().filter(c->c.decision()==CandidateReview.Decision.APPROVED)
                .filter(c->c.story().publishedAt().atZone(zone).toLocalDate().equals(day)).toList();
        return new Edition(day,zone,selected);
    }
    public AssemblyProfile profile(LocalDate day, ZoneId zone, List<CandidateReview.Candidate> candidates) {
        if (candidates == null) throw new IllegalArgumentException("candidates required");
        return new AssemblyProfile(candidates.size(), assemble(day, zone, candidates).segments().size());
    }
}
