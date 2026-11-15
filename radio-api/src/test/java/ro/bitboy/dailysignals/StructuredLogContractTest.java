package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StructuredLogContractTest {
    @Test void emitsAStableMachineReadableEnvelope() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
        var node = new ObjectMapper().readTree(StructuredLog.event(clock, "INFO", "daily-signals-api", "3.0.0",
                "startup", DailySignals.class.getName(), "api.started",
                "Daily Signals listening on http://127.0.0.1:8151", Map.of("port", 8151)));
        assertEquals(1, node.path("schemaVersion").asInt());
        assertEquals(1794744000000L, node.path("timestamp").asLong());
        assertEquals("INFO", node.path("level").asText());
        assertEquals("daily-signals-api", node.path("service").asText());
        assertEquals("3.0.0", node.path("version").asText());
        assertEquals("startup", node.path("scId").asText());
        assertEquals(DailySignals.class.getName(), node.path("class").asText());
        assertEquals("api.started", node.path("event").asText());
        assertEquals("Daily Signals listening on http://127.0.0.1:8151", node.path("message").asText());
        assertEquals(8151, node.path("fields").path("port").asInt());
        assertFalse(node.has("token"));
    }

    @Test void rejectsUnknownLevelsAndEventNames() {
        assertThrows(IllegalArgumentException.class, () -> StructuredLog.event("TRACE", "api.started", "x", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> StructuredLog.event("INFO", "API STARTED", "x", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> StructuredLog.event(Clock.systemUTC(), "INFO", "", "1",
                "startup", "DailySignals", "api.started", "x", Map.of()));
    }
}
