package ro.bitboy.dailysignals;

import java.util.List;

/** Turns an approved edition into a reproducible spoken script. */
public final class ScriptAssembler {
    public record Script(String text, List<String> storyIds) {
        public Script { storyIds = List.copyOf(storyIds); }
    }
    public Script assemble(EditionAssembler.Edition edition) {
        if (edition == null || edition.segments().isEmpty()) throw new IllegalArgumentException("edition needs approved stories");
        var lines = new java.util.ArrayList<String>();
        var ids = new java.util.ArrayList<String>();
        lines.add("Daily Signals for " + edition.airDate() + ".");
        for (var candidate : edition.segments()) {
            var story = candidate.story();
            if (story.title().isBlank() || story.summary().isBlank()) throw new IllegalArgumentException("blank story");
            lines.add(story.title().strip() + ". " + story.summary().strip());
            ids.add(story.id());
        }
        return new Script(String.join("\n", lines) + "\n", ids);
    }
}
