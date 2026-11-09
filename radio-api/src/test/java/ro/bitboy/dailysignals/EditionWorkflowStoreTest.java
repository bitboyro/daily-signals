package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditionWorkflowStoreTest {
    @Test void approvesThenPublishesWithMonotonicRevisions() {
        var workflow = new EditionWorkflowStore();
        assertTrue(workflow.toJson().contains("\"status\":\"DRAFT\""));
        assertTrue(workflow.transitionJson("APPROVE").contains("\"revision\":1"));
        assertTrue(workflow.transitionJson("PUBLISH").contains("\"status\":\"PUBLISHED\""));
        assertThrows(IllegalArgumentException.class, () -> workflow.transitionJson("PUBLISH"));
    }
}
