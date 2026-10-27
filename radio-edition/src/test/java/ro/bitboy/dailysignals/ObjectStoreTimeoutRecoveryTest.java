package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ObjectStoreTimeoutRecoveryTest {
    private static final byte[] WAV = {'R', 'I', 'F', 'F', 1};

    @Test void retriesTimeoutsThenPublishesOnce() throws Exception {
        var calls = new AtomicInteger();
        var writer = new RecoveringObjectWriter((key, bytes) -> {
            if (calls.incrementAndGet() < 3) throw new IOException("timeout");
        }, 3, Duration.ofMillis(5), ignored -> {});
        var outbox = new PublishOutbox();
        outbox.stage("morning", "audio/morning.wav", WAV);

        outbox.publish("morning", "PRODUCER", writer);

        assertEquals(3, calls.get());
        assertEquals(0, outbox.pendingCount());
        assertEquals(1, outbox.publishedOperationCount());
    }

    @Test void keepsOutboxPendingAfterRetryExhaustion() {
        var calls = new AtomicInteger();
        var writer = new RecoveringObjectWriter((key, bytes) -> {
            calls.incrementAndGet();
            throw new IOException("timeout");
        }, 2, Duration.ZERO, ignored -> {});
        var outbox = new PublishOutbox();
        outbox.stage("evening", "audio/evening.wav", WAV);

        assertThrows(IOException.class, () -> outbox.publish("evening", "PRODUCER", writer));
        assertEquals(2, calls.get());
        assertEquals(1, outbox.pendingCount());
    }

    @Test void neverRetriesInterruptedTransfers() {
        var calls = new AtomicInteger();
        var writer = new RecoveringObjectWriter((key, bytes) -> {
            calls.incrementAndGet();
            throw new InterruptedException("shutdown");
        }, 3, Duration.ZERO, ignored -> {});

        assertThrows(InterruptedException.class, () -> writer.put("audio/x.wav", WAV));
        assertEquals(1, calls.get());
    }
}
