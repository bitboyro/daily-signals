package ro.bitboy.dailysignals;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Producer-facing review decisions over the current candidate set; synthetic candidates until ingestion is wired in. */
public final class ReviewQueue implements ReviewQueueBackend {
    private final CandidateReview review = new CandidateReview();
    private final List<DecisionAudit> audit = new ArrayList<>();
    private final Map<String, ReviewMetadata> metadata = new LinkedHashMap<>();

    private record ReviewMetadata(String reviewer, Instant reviewedAt, int revision) {}

    public record DecisionReceipt(String storyId, CandidateReview.Decision decision) {
        String toJson() {
            return "{\"storyId\":" + quote(storyId) + ",\"decision\":\"" + decision + "\"}";
        }
    }

    public record DecisionAudit(int sequence, String storyId, CandidateReview.Decision decision) {
        String toJson() {
            return "{\"sequence\":" + sequence + ",\"storyId\":" + quote(storyId)
                    + ",\"decision\":\"" + decision + "\"}";
        }
    }

    public static ReviewQueue fixture() {
        var queue = new ReviewQueue();
        String[][] stories = {
                {"demo-library-hours", "Community library extends Saturday hours", "education", "Demo Education Desk"},
                {"demo-park-path", "New walking path opens in Riverside Park", "civic", "Demo Civic Desk"},
                {"demo-transit-night", "Night bus pilot adds two stops", "civic", "Demo Civic Desk"}};
        for (String[] story : stories) {
            var attribution = new StoryNormalizer.Attribution("demo-" + story[2] + "-desk", story[3],
                    URI.create("https://example.invalid/daily-signals/demo-" + story[2] + "-desk"), "synthetic-2026-01-15-morning");
            queue.review.submit(new CandidateReview.Candidate(new StoryNormalizer.Story(story[0], story[1], story[1],
                    Instant.parse("2026-01-15T06:30:00Z"), StoryNormalizer.canonical(story[1]), attribution),
                    1, CandidateReview.Decision.PENDING));
        }
        return queue;
    }

    public synchronized DecisionReceipt decide(String storyId, String decision) {
        return decide(storyId, decision, "fixture");
    }

    private synchronized DecisionReceipt decide(String storyId, String decision, String reviewer) {
        CandidateReview.Decision parsed;
        try {
            parsed = CandidateReview.Decision.valueOf(decision == null ? "" : decision.strip());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("decision must be APPROVED or REJECTED");
        }
        review.decide(storyId, parsed);
        audit.add(new DecisionAudit(audit.size() + 1, storyId, parsed));
        metadata.put(storyId, new ReviewMetadata(reviewer, Instant.now(), 1));
        return new DecisionReceipt(storyId, parsed);
    }

    @Override
    public synchronized String decideJson(String storyId, String decision, String reviewer) {
        return decide(storyId, decision, reviewer).toJson();
    }

    public synchronized boolean contains(String storyId) {
        return review.all().stream().anyMatch(c -> c.story().id().equals(storyId));
    }

    public synchronized String toJson() {
        var json = new StringBuilder("{\"schemaVersion\":3,\"items\":[");
        boolean first = true;
        for (var candidate : review.all()) {
            if (!first) json.append(',');
            first = false;
            var state = metadata.get(candidate.story().id());
            json.append("{\"storyId\":").append(quote(candidate.story().id()))
                    .append(",\"headline\":").append(quote(candidate.story().title()))
                    .append(",\"source\":").append(quote(candidate.story().attribution().sourceName()))
                    .append(",\"decision\":\"").append(candidate.decision()).append("\",\"review\":{")
                    .append("\"reviewer\":").append(state == null ? "null" : quote(state.reviewer()))
                    .append(",\"reviewedAt\":").append(state == null ? "null" : quote(state.reviewedAt().toString()))
                    .append(",\"revision\":").append(state == null ? 0 : state.revision()).append("}}");
        }
        return json.append("]}").toString();
    }

    public synchronized String auditToJson() {
        return "{\"schemaVersion\":1,\"decisions\":[" + audit.stream()
                .map(DecisionAudit::toJson).reduce((left, right) -> left + "," + right).orElse("") + "]}";
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
