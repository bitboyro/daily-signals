package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class PublishOutboxTest {
    @Test void publishesScheduledEditionOnlyWhenDue() throws Exception {
        var outbox = new PublishOutbox();
        outbox.stage("edition-2", "editions/two.wav", new byte[]{'R','I','F','F',2});
        var schedule = new ScheduledEditionClock(ZoneId.of("Europe/Bucharest"), LocalTime.of(8, 30));
        assertThrows(IllegalStateException.class, () -> outbox.publishScheduled("edition-2", LocalDate.of(2026, 1, 15), schedule,
                Instant.parse("2026-01-15T06:29:59Z"), "PRODUCER", (key, bytes) -> fail("not due")));
        assertEquals("edition-2", outbox.publishScheduled("edition-2", LocalDate.of(2026, 1, 15), schedule,
                Instant.parse("2026-01-15T06:30:00Z"), "PRODUCER", (key, bytes) -> {}).editionId());
    }
    @Test void failedReplacementLeavesPreviousObjectPublishedUntilRetry() throws Exception {
        var outbox = new PublishOutbox();
        outbox.stage("edition-1", "editions/first.wav", new byte[]{'R','I','F','F',1});
        var first = outbox.publish("edition-1", "PRODUCER", (key, bytes) -> {});
        var revised = new byte[]{'R','I','F','F',4,5};
        outbox.stageReplacement("edition-1", "editions/revised.wav", revised);
        revised[4] = 0;
        assertThrows(IOException.class, () -> outbox.publishReplacement("edition-1", "PRODUCER", (key, bytes) -> { throw new IOException("upload unavailable"); }));
        assertEquals(1, outbox.replacementCount());
        assertSame(first, outbox.publish("edition-1", "PRODUCER", (key, bytes) -> fail("must not upload previous object")));
        var replacement = outbox.publishReplacement("edition-1", "PRODUCER", (key, bytes) -> assertEquals(4, bytes[4]));
        assertEquals(0, outbox.replacementCount());
        assertEquals("editions/revised.wav", replacement.audioPath());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[]{'R','I','F','F',4,5})), replacement.checksumSha256());
    }
    @Test void replacementPublicationPointsListenersAtNewRecording() throws Exception {
        var outbox = new PublishOutbox();
        outbox.stage("edition-1", "editions/first.wav", new byte[]{'R','I','F','F',1});
        outbox.publish("edition-1", "PRODUCER", (key, bytes) -> {});
        var revised = new byte[]{'R','I','F','F',2,3};
        outbox.stageReplacement("edition-1", "editions/revised.wav", revised);
        var manifest = outbox.publishReplacement("edition-1", "PRODUCER", (key, bytes) -> assertArrayEquals(revised, bytes));
        assertEquals("editions/revised.wav", manifest.audioPath());
        assertEquals(revised.length, manifest.sizeBytes());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(revised)), manifest.checksumSha256());
    }
    @Test void stagesAReplacementWithoutChangingThePublishedRevision() throws Exception {
        var outbox = new PublishOutbox();
        outbox.stage("edition-1", "editions/one.wav", new byte[]{'R','I','F','F',1});
        var first = outbox.publish("edition-1", "PRODUCER", (key, bytes) -> {});
        var next = new byte[]{'R','I','F','F',2};
        outbox.stageReplacement("edition-1", "editions/two.wav", next);
        next[4] = 0;
        assertEquals(1, outbox.replacementCount());
        assertEquals("editions/one.wav", first.audioPath());
        assertThrows(IllegalArgumentException.class, () -> outbox.stageReplacement("edition-1", "editions/three.wav", next));
    }
    @Test void retriesFailedTransferAndPublishesExactChecksumOnce() throws Exception {
        var outbox = new PublishOutbox();
        byte[] wav = new byte[]{'R','I','F','F',1,2,3};
        outbox.stage("edition-1", "editions/edition-1.wav", wav);
        wav[4] = 0;
        assertThrows(IOException.class, () -> outbox.publish("edition-1", "PRODUCER", (key, bytes) -> { throw new IOException("temporary failure"); }));
        assertEquals(1, outbox.pendingCount());
        var manifest = outbox.publish("edition-1", "PRODUCER", (key, bytes) -> {
            assertEquals("editions/edition-1.wav", key);
            assertEquals(1, bytes[4]);
        });
        assertEquals(0, outbox.pendingCount());
        assertEquals("editions/edition-1.wav", outbox.producerEvents().get(0).objectKey());
        assertEquals("published", outbox.producerEvents().get(0).action());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[]{'R','I','F','F',1,2,3})), manifest.checksumSha256());
        assertSame(manifest, outbox.publish("edition-1", "PRODUCER", (key, bytes) -> fail("must not write twice")));
    }
}
