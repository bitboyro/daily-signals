package ro.bitboy.dailysignals;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Archived editions offered to listeners; a fixed synthetic catalog until persistence is wired in. */
public final class EditionCatalog {
    public record Entry(String editionId, LocalDate airDate, String title, String audioPath) {
        public Entry {
            if (editionId == null || !editionId.matches("[a-z0-9-]+") || airDate == null
                    || title == null || title.isBlank() || audioPath == null || !audioPath.startsWith("/"))
                throw new IllegalArgumentException("invalid catalog entry");
        }
    }

    private final List<Entry> entries;

    public EditionCatalog(List<Entry> entries) {
        this.entries = entries.stream().sorted(Comparator.comparing(Entry::airDate).reversed()).toList();
    }

    public static EditionCatalog fixture() {
        return new EditionCatalog(List.of(new Entry("demo-2026-01-15", LocalDate.of(2026, 1, 15),
                "Daily Signals demo edition", "/editions/demo/audio")));
    }

    public List<Entry> entries() { return entries; }

    public static final int SCHEMA_VERSION = 2;

    public String toJson() {
        var json = new StringBuilder("{\"schemaVersion\":" + SCHEMA_VERSION + ",\"editions\":[");
        int first = json.length();
        for (var entry : entries) {
            if (json.length() > first) json.append(',');
            json.append("{\"editionId\":").append(quote(entry.editionId()))
                    .append(",\"airDate\":").append(quote(entry.airDate().toString()))
                    .append(",\"title\":").append(quote(entry.title()))
                    .append(",\"audioPath\":").append(quote(entry.audioPath())).append('}');
        }
        return json.append("]}").toString();
    }

    private static String quote(String value) {
        var quoted = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                default -> {
                    if (c < 0x20) quoted.append(String.format("\\u%04x", (int) c));
                    else quoted.append(c);
                }
            }
        }
        return quoted.append('"').toString();
    }
}
