package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class PublicationConsumerTest {
    private final PublishOutbox outbox = new PublishOutbox(Clock.fixed(Instant.parse("2026-01-15T07:30:00Z"), ZoneOffset.UTC));

    @Test void publicationAndReplacementReachTheManifestThroughEvents() throws Exception {
        var written = new ArrayList<PublicationManifest>();
        var consumer = new PublicationConsumer(written::add);
        outbox.stage("edition-1", "editions/first.wav", new byte[]{'R','I','F','F',1});
        outbox.publish("edition-1", "PRODUCER", (key, bytes) -> {});
        outbox.stageReplacement("edition-1", "editions/second.wav", new byte[]{'R','I','F','F',2});
        outbox.publishReplacement("edition-1", "PRODUCER", (key, bytes) -> {});
        var events = outbox.drainEvents();
        assertEquals(2, events.size());
        assertEquals("edition-1:1", events.get(0).eventId());
        assertEquals("2026-01-15T07:30:00Z", events.get(0).occurredAt());
        for (var event : events) assertTrue(consumer.apply(event));
        assertEquals("editions/second.wav", written.get(written.size() - 1).audioPath());
        assertEquals(PublicationConsumer.EditionStatus.RECORDING_REPLACED, consumer.status("edition-1").orElseThrow());
        assertTrue(outbox.drainEvents().isEmpty());
    }

    @Test void replacementBeforePublicationIsRejected() throws Exception {
        outbox.stage("edition-1", "editions/first.wav", new byte[]{'R','I','F','F',1});
        outbox.publish("edition-1", "PRODUCER", (key, bytes) -> {});
        outbox.stageReplacement("edition-1", "editions/second.wav", new byte[]{'R','I','F','F',2});
        outbox.publishReplacement("edition-1", "PRODUCER", (key, bytes) -> {});
        var replaced = outbox.drainEvents().get(1);
        var consumer = new PublicationConsumer(manifest -> fail("must not write"));
        assertThrows(IllegalStateException.class, () -> consumer.apply(replaced));
        assertTrue(consumer.status("edition-1").isEmpty());
    }

    @Test void sinkFailureLeavesTheEventRetryable() throws Exception {
        outbox.stage("edition-1", "editions/first.wav", new byte[]{'R','I','F','F',1});
        outbox.publish("edition-1", "PRODUCER", (key, bytes) -> {});
        var event = outbox.drainEvents().get(0);
        var attempts = new int[1];
        var consumer = new PublicationConsumer(manifest -> {
            if (attempts[0]++ == 0) throw new IOException("manifest store unavailable");
        });
        assertThrows(IOException.class, () -> consumer.apply(event));
        assertTrue(consumer.status("edition-1").isEmpty());
        assertTrue(consumer.apply(event));
        assertEquals(PublicationConsumer.EditionStatus.PUBLISHED, consumer.status("edition-1").orElseThrow());
    }
}
