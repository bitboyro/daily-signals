package ro.bitboy.dailysignals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Applies whole-token pronunciations, avoiding replacements inside names. */
public final class PronunciationDictionary {
    private final Map<String, String> entries = new LinkedHashMap<>();
    public PronunciationDictionary() {
        this(Map.of("AI", "A I", "S3", "S three"));
    }
    public PronunciationDictionary(Map<String, String> configuredEntries) {
        if (configuredEntries == null || configuredEntries.isEmpty() || configuredEntries.size() > 100)
            throw new IllegalArgumentException("pronunciation entries required");
        configuredEntries.forEach((term, spoken) -> entries.put(valid(term, 64), valid(spoken, 128)));
    }
    public Map<String, String> entries() {
        return Map.copyOf(entries);
    }
    public String speakable(String text) {
        if (text == null) throw new IllegalArgumentException("text required");
        String result = text;
        for (var entry : entries.entrySet()) {
            result = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(entry.getKey()) + "(?![\\p{L}\\p{N}])")
                    .matcher(result).replaceAll(java.util.regex.Matcher.quoteReplacement(entry.getValue()));
        }
        return result;
    }
    private static String valid(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid pronunciation entry");
        return value.strip();
    }
}
