package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class FetchWindowPolicyTest {
    private final FetchWindowPolicy policy = new FetchWindowPolicy(Duration.ofHours(6), Duration.ofMinutes(2));
    private final Instant now = Instant.parse("2026-01-15T12:00:00Z");
    @Test void includesBoundariesAndRejectsStaleOrFutureSnapshots() {
        assertTrue(policy.accepts(now.minus(Duration.ofHours(6)), now));
        assertTrue(policy.accepts(now.plus(Duration.ofMinutes(2)), now));
        assertFalse(policy.accepts(now.minus(Duration.ofHours(6)).minusNanos(1), now));
        assertFalse(policy.accepts(now.plus(Duration.ofMinutes(2)).plusNanos(1), now));
    }
    @Test void rejectsInvalidPolicy() {
        assertThrows(IllegalArgumentException.class, () -> new FetchWindowPolicy(Duration.ZERO, Duration.ZERO));
    }
}
