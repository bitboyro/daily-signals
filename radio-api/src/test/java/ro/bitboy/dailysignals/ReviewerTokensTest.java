package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewerTokensTest {
    private static final String PRODUCER = "producer-token-0123456789abcdef";
    private static final String EDITOR = "editor-token-0123456789abcdef";

    @Test void mapsBearerTokensToConfiguredRoles() {
        var tokens = ReviewerTokens.parse(PRODUCER + "=PRODUCER, " + EDITOR + "=EDITOR");
        assertEquals("PRODUCER", tokens.roleFor("Bearer " + PRODUCER).orElseThrow());
        assertEquals("EDITOR", tokens.roleFor("Bearer " + EDITOR).orElseThrow());
        assertTrue(tokens.roleFor("Bearer " + PRODUCER + "x").isEmpty());
        assertTrue(tokens.roleFor(PRODUCER).isEmpty());
        assertTrue(tokens.roleFor(null).isEmpty());
    }

    @Test void unconfiguredServerAcceptsNoReviewer() {
        assertTrue(ReviewerTokens.parse(null).roleFor("Bearer " + PRODUCER).isEmpty());
        assertTrue(ReviewerTokens.parse(" ").roleFor("Bearer ").isEmpty());
    }

    @Test void rejectsWeakDuplicateOrUnknownEntries() {
        assertThrows(IllegalArgumentException.class, () -> ReviewerTokens.parse("short=PRODUCER"));
        assertThrows(IllegalArgumentException.class, () -> ReviewerTokens.parse(PRODUCER + "=ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> ReviewerTokens.parse(PRODUCER + "=PRODUCER," + PRODUCER + "=EDITOR"));
        assertThrows(IllegalArgumentException.class, () -> ReviewerTokens.parse(PRODUCER));
    }
}
