package ro.bitboy.dailysignals;

import java.util.List;

/** Turns an approved edition into a reproducible spoken script. */
public final class ScriptAssembler {
    public record Script(String text, List<String> storyIds) {
        public Script {
            if (text == null || text.isBlank() || text.length() > 20000 || storyIds == null
                    || storyIds.isEmpty() || storyIds.stream().anyMatch(id -> id == null || id.isBlank())
                    || storyIds.size() != new java.util.HashSet<>(storyIds).size())
                throw new IllegalArgumentException("invalid narration script");
            storyIds = List.copyOf(storyIds);
        }
    }
    private final PronunciationDictionary pronunciation = new PronunciationDictionary();
    private static String sentence(String value) {
        var clean = value.strip().replaceAll("\\s+", " ");
        if (clean.isBlank() || clean.length() > 2000 || clean.chars().anyMatch(c -> Character.isISOControl(c)))
            throw new IllegalArgumentException("invalid spoken segment");
        return clean.matches(".*[.!?]$") ? clean : clean + ".";
    }
    public Script assemble(EditionAssembler.Edition edition) {
        if (edition == null || edition.segments().isEmpty()) throw new IllegalArgumentException("edition needs approved stories");
        var lines = new java.util.ArrayList<String>();
        var ids = new java.util.ArrayList<String>();
        lines.add("Daily Signals for " + edition.airDate() + ".");
        for (var candidate : edition.segments()) {
            var story = candidate.story();
            if (story.title().isBlank() || story.summary().isBlank()) throw new IllegalArgumentException("blank story");
            lines.add(sentence(story.title()) + " " + sentence(story.summary()));
            ids.add(story.id());
        }
        return new Script(pronunciation.speakable(String.join("\n", lines) + "\n"), ids);
    }
}
