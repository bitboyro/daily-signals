package ro.bitboy.dailysignals;

import java.net.URI;
import java.time.Instant;

/** Producer-facing review decisions over the current candidate set; synthetic candidates until ingestion is wired in. */
public final class ReviewQueue {
    private final CandidateReview review = new CandidateReview();

    public static ReviewQueue fixture() {
        var queue = new ReviewQueue();
        var attribution = new StoryNormalizer.Attribution("demo-civic-desk", "Demo Civic Desk",
                URI.create("https://example.invalid/daily-signals/demo-civic-desk"), "synthetic-2026-01-15-morning");
        String[][] stories = {
                {"demo-library-hours", "Community library extends Saturday hours"},
                {"demo-park-path", "New walking path opens in Riverside Park"},
                {"demo-transit-night", "Night bus pilot adds two stops"}};
        for (String[] story : stories) {
            queue.review.submit(new CandidateReview.Candidate(new StoryNormalizer.Story(story[0], story[1], story[1],
                    Instant.parse("2026-01-15T06:30:00Z"), StoryNormalizer.canonical(story[1]), attribution),
                    1, CandidateReview.Decision.PENDING));
        }
        return queue;
    }

    public synchronized void decide(String storyId, String decision) {
        CandidateReview.Decision parsed;
        try {
            parsed = CandidateReview.Decision.valueOf(decision == null ? "" : decision.strip());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("decision must be APPROVED or REJECTED");
        }
        review.decide(storyId, parsed);
    }

    public synchronized boolean contains(String storyId) {
        return review.all().stream().anyMatch(c -> c.story().id().equals(storyId));
    }

    public synchronized String toJson() {
        var json = new StringBuilder("{\"schemaVersion\":2,\"items\":[");
        boolean first = true;
        for (var candidate : review.all()) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"storyId\":").append(quote(candidate.story().id()))
                    .append(",\"headline\":").append(quote(candidate.story().title()))
                    .append(",\"source\":").append(quote(candidate.story().attribution().sourceName()))
                    .append(",\"decision\":\"").append(candidate.decision()).append("\"}");
        }
        return json.append("]}").toString();
    }

    private static String quote(String value) {
        var quoted = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') quoted.append('\\').append(c);
            else if (c < 0x20) quoted.append(String.format("\\u%04x", (int) c));
            else quoted.append(c);
        }
        return quoted.append('"').toString();
    }
}
