package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StructuredLogContractTest {
    @Test void emitsAStableMachineReadableEnvelope() throws Exception {
        var node = new ObjectMapper().readTree(StructuredLog.event("INFO", "api.started",
                "Daily Signals listening on http://127.0.0.1:8151", Map.of("port", 8151)));
        assertEquals(1, node.path("schemaVersion").asInt());
        assertEquals("INFO", node.path("level").asText());
        assertEquals("api.started", node.path("event").asText());
        assertEquals(8151, node.path("fields").path("port").asInt());
        assertFalse(node.has("token"));
    }

    @Test void rejectsUnknownLevelsAndEventNames() {
        assertThrows(IllegalArgumentException.class, () -> StructuredLog.event("TRACE", "api.started", "x", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> StructuredLog.event("INFO", "API STARTED", "x", Map.of()));
    }
}
