package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class EditionWorkflowStoreTest {
    @Test void approvesThenPublishesWithMonotonicRevisions() {
        var workflow = new EditionWorkflowStore(Clock.fixed(
                Instant.parse("2026-11-09T19:40:00Z"), ZoneOffset.UTC));
        assertTrue(workflow.toJson().contains("\"status\":\"DRAFT\""));
        assertTrue(workflow.transitionJson("APPROVE", "producer-42").contains("\"revision\":1"));
        assertEquals("{\"schemaVersion\":1,\"editionId\":\"producer-today\",\"approvals\":["
                        + "{\"editionRevision\":1,\"actor\":\"producer-42\","
                        + "\"approvedAt\":\"2026-11-09T19:40:00Z\"}]}",
                workflow.auditJson("producer-today"));
        assertTrue(workflow.transitionJson("PUBLISH").contains("\"status\":\"PUBLISHED\""));
        assertThrows(IllegalArgumentException.class, () -> workflow.transitionJson("PUBLISH"));
        assertThrows(IllegalArgumentException.class, () -> workflow.auditJson("another-edition"));
    }
}
