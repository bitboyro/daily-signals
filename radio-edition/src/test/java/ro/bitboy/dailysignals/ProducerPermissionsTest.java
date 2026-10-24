package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProducerPermissionsTest {
    @Test void noRolePublicationEntryPointsAreNotPublic() {
        assertThrows(NoSuchMethodException.class, () -> PublishOutbox.class.getMethod(
                "publish", String.class, PublishOutbox.ObjectWriter.class));
        assertThrows(NoSuchMethodException.class, () -> PublishOutbox.class.getMethod(
                "publishReplacement", String.class, PublishOutbox.ObjectWriter.class));
    }

    @Test void onlyProducerCanPublishNewRecording() throws Exception {
        var outbox = new PublishOutbox();
        outbox.stage("edition-1", "editions/one.wav", new byte[]{'R','I','F','F',1});
        assertThrows(SecurityException.class, () -> outbox.publish("edition-1", "EDITOR", (key, bytes) -> fail("editor must not write")));
        assertEquals(1, outbox.pendingCount());
        assertEquals("PUBLISHED", outbox.publish("edition-1", "PRODUCER", (key, bytes) -> {}).status());
    }
    @Test void onlyProducerCanReplacePublishedRecording() throws Exception {
        var outbox = new PublishOutbox();
        outbox.stage("edition-1", "editions/one.wav", new byte[]{'R','I','F','F',1});
        outbox.publish("edition-1", "PRODUCER", (key, bytes) -> {});
        outbox.stageReplacement("edition-1", "editions/two.wav", new byte[]{'R','I','F','F',2});
        assertThrows(SecurityException.class, () -> outbox.publishReplacement("edition-1", "EDITOR", (key, bytes) -> fail("editor must not write")));
        assertEquals(1, outbox.replacementCount());
        assertEquals("editions/two.wav", outbox.publishReplacement("edition-1", "PRODUCER", (key, bytes) -> {}).audioPath());
    }
}
