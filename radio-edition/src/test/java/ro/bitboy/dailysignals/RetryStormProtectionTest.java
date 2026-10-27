package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryStormProtectionTest {
    @Test void sharedBudgetCapsAttemptsAcrossWriters() {
        var clock = new MutableClock(Instant.parse("2026-02-01T00:00:00Z"));
        var budget = new RetryBudget(3, Duration.ofMinutes(1), clock);
        var calls = new AtomicInteger();
        PublishOutbox.ObjectWriter failing = (key, bytes) -> {
            calls.incrementAndGet();
            throw new IOException("provider unavailable");
        };
        var first = new RecoveringObjectWriter(failing, 3, Duration.ZERO, ignored -> {}, budget);
        var second = new RecoveringObjectWriter(failing, 3, Duration.ZERO, ignored -> {}, budget);

        assertThrows(IOException.class, () -> first.put("audio/a.wav", new byte[] {1}));
        assertThrows(IOException.class, () -> second.put("audio/b.wav", new byte[] {1}));
        assertEquals(3, calls.get());

        clock.advance(Duration.ofMinutes(1));
        assertThrows(IOException.class, () -> second.put("audio/b.wav", new byte[] {1}));
        assertEquals(6, calls.get());
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
