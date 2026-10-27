package ro.bitboy.dailysignals;

import java.util.Map;

/** Stable JSON event envelope shared by API startup and operator tooling. */
public final class StructuredLog {
    private StructuredLog() {}

    public static String event(String level, String event, String message, Map<String, ?> fields) {
        if (!level.matches("DEBUG|INFO|WARN|ERROR") || event == null || !event.matches("[a-z][a-z0-9.]+")
                || message == null || message.isBlank()) throw new IllegalArgumentException("invalid log envelope");
        var json = new StringBuilder("{\"schemaVersion\":1,\"level\":\"").append(escape(level))
                .append("\",\"event\":\"").append(escape(event)).append("\",\"message\":\"")
                .append(escape(message)).append("\",\"fields\":{");
        boolean first = true;
        for (var field : new java.util.TreeMap<>(fields).entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(escape(field.getKey())).append("\":");
            var value = field.getValue();
            if (value instanceof Number || value instanceof Boolean) json.append(value);
            else if (value instanceof String text) json.append('"').append(escape(text)).append('"');
            else throw new IllegalArgumentException("log fields must be scalar JSON values");
        }
        return json.append("}}").toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
