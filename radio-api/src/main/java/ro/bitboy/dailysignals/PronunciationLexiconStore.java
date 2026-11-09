package ro.bitboy.dailysignals;

import java.util.LinkedHashMap;
import java.util.Map;

/** Editable pronunciation preview; the following checkpoint replaces this memory store with PostgreSQL. */
final class PronunciationLexiconStore implements PronunciationLexiconBackend {
    private Map<String, String> entries = new LinkedHashMap<>(new PronunciationDictionary().entries());
    private int revision;

    public synchronized String toJson() {
        return json(entries, revision);
    }
    public synchronized String saveJson(String text) {
        entries = parse(text);
        revision++;
        return toJson();
    }
    @Override public synchronized String speakable(String text) {
        return dictionary().speakable(text);
    }
    synchronized PronunciationDictionary dictionary() {
        return new PronunciationDictionary(entries);
    }
    static Map<String, String> parse(String text) {
        if (text == null || text.isBlank() || text.length() > 20_000)
            throw new IllegalArgumentException("pronunciation entries required");
        var parsed = new LinkedHashMap<String, String>();
        for (String line : text.lines().toList()) {
            if (line.isBlank()) continue;
            int separator = line.indexOf('=');
            if (separator < 1 || separator == line.length() - 1)
                throw new IllegalArgumentException("use term=spoken form");
            String previous = parsed.put(line.substring(0, separator).strip(), line.substring(separator + 1).strip());
            if (previous != null) throw new IllegalArgumentException("duplicate pronunciation term");
        }
        return new LinkedHashMap<>(new PronunciationDictionary(parsed).entries());
    }
    static String json(Map<String, String> values, int revision) {
        return "{\"schemaVersion\":1,\"text\":" + quote(toText(values)) + ",\"revision\":" + revision + "}";
    }
    private static String toText(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
