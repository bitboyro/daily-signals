package ro.bitboy.dailysignals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class EditionAssembler {
    public record Edition(LocalDate airDate, ZoneId zone, List<CandidateReview.Candidate> segments) {
        public Edition { segments=List.copyOf(segments); }
    }
    public Edition assemble(LocalDate day, ZoneId zone, List<CandidateReview.Candidate> candidates) {
        if(day==null || zone==null) throw new IllegalArgumentException("date and zone required");
        var selected=candidates.stream().filter(c->c.decision()==CandidateReview.Decision.APPROVED)
                .filter(c->c.story().publishedAt().atZone(zone).toLocalDate().equals(day)).toList();
        return new Edition(day,zone,selected);
    }
}
