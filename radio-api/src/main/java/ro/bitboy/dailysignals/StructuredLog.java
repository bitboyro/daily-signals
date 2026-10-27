package ro.bitboy.dailysignals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable JSON event envelope shared by API startup and operator tooling. */
public final class StructuredLog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private StructuredLog() {}

    public static String event(String level, String event, String message, Map<String, ?> fields) {
        if (!level.matches("DEBUG|INFO|WARN|ERROR") || event == null || !event.matches("[a-z][a-z0-9.]+")
                || message == null || message.isBlank()) throw new IllegalArgumentException("invalid log envelope");
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("schemaVersion", 1);
        envelope.put("level", level);
        envelope.put("event", event);
        envelope.put("message", message);
        envelope.put("fields", Map.copyOf(fields));
        try {
            return JSON.writeValueAsString(envelope);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("log fields must be JSON serializable", error);
        }
    }
}
