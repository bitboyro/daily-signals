package ro.bitboy.dailysignals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Applies whole-token pronunciations, avoiding replacements inside names. */
public final class PronunciationDictionary {
    private final Map<String, String> entries = new LinkedHashMap<>();
    public PronunciationDictionary() {
        entries.put("AI", "A I");
        entries.put("S3", "S three");
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
}
