package ro.bitboy.dailysignals;

import ro.bitboy.dailysignals.persistence.ReviewRepository;

import java.sql.SQLException;

/** PostgreSQL-backed producer queue; each request owns a short-lived JDBC connection. */
final class PostgresReviewQueue implements ReviewQueueBackend {
    private final DatabaseRuntime database;
    private final ReviewRepository reviews = new ReviewRepository();

    PostgresReviewQueue(DatabaseRuntime database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override
    public boolean contains(String storyId) {
        try (var connection = database.open()) {
            return reviews.get(connection, storyId).isPresent();
        } catch (SQLException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public String decideJson(String storyId, String decision, String reviewer) {
        CandidateReview.Decision parsed;
        try {
            parsed = CandidateReview.Decision.valueOf(decision == null ? "" : decision.strip());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("decision must be APPROVED or REJECTED");
        }
        if (parsed == CandidateReview.Decision.PENDING) {
            throw new IllegalArgumentException("decision must be APPROVED or REJECTED");
        }
        try (var connection = database.open()) {
            var current = reviews.get(connection, storyId).orElseThrow(() -> new IllegalArgumentException("unknown candidate"));
            if (!reviews.decide(connection, storyId, parsed, reviewer, null, current.revision())) {
                throw new IllegalStateException("candidate review changed concurrently");
            }
            return "{\"storyId\":" + quote(storyId) + ",\"decision\":\"" + parsed + "\"}";
        } catch (SQLException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public String toJson() {
        try (var connection = database.open()) {
            var json = new StringBuilder("{\"schemaVersion\":2,\"items\":[");
            for (var item : reviews.queue(connection)) {
                if (json.charAt(json.length() - 1) != '[') json.append(',');
                json.append("{\"storyId\":").append(quote(item.candidateId()))
                        .append(",\"headline\":").append(quote(item.headline()))
                        .append(",\"source\":").append(quote(item.sourceId()))
                        .append(",\"decision\":\"").append(item.decision()).append("\"}");
            }
            return json.append("]}").toString();
        } catch (SQLException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public String auditToJson() {
        try (var connection = database.open()) {
            var json = new StringBuilder("{\"schemaVersion\":1,\"decisions\":[");
            int sequence = 0;
            for (var item : reviews.queue(connection)) {
                if (item.decision() == CandidateReview.Decision.PENDING) continue;
                if (sequence > 0) json.append(',');
                json.append("{\"sequence\":").append(++sequence).append(",\"storyId\":")
                        .append(quote(item.candidateId())).append(",\"decision\":\"")
                        .append(item.decision()).append("\"}");
            }
            return json.append("]}").toString();
        } catch (SQLException failure) {
            throw unavailable(failure);
        }
    }

    private static IllegalStateException unavailable(SQLException failure) {
        return new IllegalStateException("review database unavailable", failure);
    }

    private static String quote(String value) {
        var result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') result.append('\\');
            if (c < 0x20) result.append(String.format("\\u%04x", (int) c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
}
