package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class PublishOutboxTest {
    @Test void stagesAReplacementWithoutChangingThePublishedRevision() throws Exception {
        var outbox = new PublishOutbox();
        outbox.stage("edition-1", "editions/one.wav", new byte[]{'R','I','F','F',1});
        var first = outbox.publish("edition-1", (key, bytes) -> {});
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
        assertThrows(IOException.class, () -> outbox.publish("edition-1", (key, bytes) -> { throw new IOException("temporary failure"); }));
        assertEquals(1, outbox.pendingCount());
        var manifest = outbox.publish("edition-1", (key, bytes) -> {
            assertEquals("editions/edition-1.wav", key);
            assertEquals(1, bytes[4]);
        });
        assertEquals(0, outbox.pendingCount());
        assertEquals("editions/edition-1.wav", outbox.producerEvents().get(0).objectKey());
        assertEquals("published", outbox.producerEvents().get(0).action());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[]{'R','I','F','F',1,2,3})), manifest.checksumSha256());
        assertSame(manifest, outbox.publish("edition-1", (key, bytes) -> fail("must not write twice")));
    }
}
