package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditionAssemblerTest {
    @Test void rejectsMissingTimezone() {
        assertThrows(IllegalArgumentException.class,()->new EditionAssembler().assemble(LocalDate.now(),null,List.of()));
    }
    @Test void assemblesOnlyApprovedLocalDate() {
        var approved=CandidateReviewTest.candidate("a","Approved",1).withDecision(CandidateReview.Decision.APPROVED);
        var pending=CandidateReviewTest.candidate("b","Pending",1);
        var edition=new EditionAssembler().assemble(LocalDate.of(2026,1,15),ZoneId.of("Europe/Bucharest"),List.of(approved,pending));
        assertEquals(1,edition.segments().size());
        assertEquals("a",edition.segments().get(0).story().id());
    }
}
