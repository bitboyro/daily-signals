package ro.bitboy.dailysignals.persistence;

import ro.bitboy.dailysignals.CandidateReview;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persists producer decisions with optimistic concurrency. */
public final class ReviewRepository {
    public record StoredReview(String candidateId, CandidateReview.Decision decision, String reviewer,
                               String reason, Instant reviewedAt, int revision) {}
    public record QueueItem(String candidateId, String headline, String sourceId, CandidateReview.Decision decision,
                            String reviewer, Instant reviewedAt, int revision) {}

    public boolean decide(Connection connection, String candidateId, CandidateReview.Decision decision,
                          String reviewer, String reason, int expectedRevision) throws SQLException {
        if (connection == null || candidateId == null || candidateId.isBlank() || decision == null
                || decision == CandidateReview.Decision.PENDING || reviewer == null || reviewer.isBlank()
                || expectedRevision < 0) {
            throw new IllegalArgumentException("candidate, final decision, reviewer and revision are required");
        }
        try (var statement = connection.prepareStatement("""
                UPDATE candidate_review
                SET decision = ?, reviewer = ?, reason = ?, reviewed_at = CURRENT_TIMESTAMP, revision = revision + 1
                WHERE candidate_id = ? AND decision = 'PENDING' AND revision = ?
                """)) {
            statement.setString(1, decision.name());
            statement.setString(2, reviewer);
            statement.setString(3, reason);
            statement.setString(4, candidateId);
            statement.setInt(5, expectedRevision);
            return statement.executeUpdate() == 1;
        }
    }

    public Optional<StoredReview> get(Connection connection, String candidateId) throws SQLException {
        if (connection == null || candidateId == null || candidateId.isBlank()) {
            throw new IllegalArgumentException("connection and candidate id are required");
        }
        try (var statement = connection.prepareStatement("""
                SELECT candidate_id, decision, reviewer, reason, reviewed_at, revision
                FROM candidate_review WHERE candidate_id = ?
                """)) {
            statement.setString(1, candidateId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                var reviewedAt = rows.getTimestamp(5);
                return Optional.of(new StoredReview(rows.getString(1), CandidateReview.Decision.valueOf(rows.getString(2)),
                        rows.getString(3), rows.getString(4), reviewedAt == null ? null : reviewedAt.toInstant(),
                        rows.getInt(6)));
            }
        }
    }

    public List<QueueItem> queue(Connection connection) throws SQLException {
        if (connection == null) throw new IllegalArgumentException("connection is required");
        try (var statement = connection.prepareStatement("""
                SELECT c.candidate_id, c.headline, c.source_id, r.decision, r.reviewer, r.reviewed_at, r.revision
                FROM candidate_story c JOIN candidate_review r ON r.candidate_id = c.candidate_id
                ORDER BY c.captured_at DESC, c.candidate_id
                """); var rows = statement.executeQuery()) {
            var items = new ArrayList<QueueItem>();
            while (rows.next()) {
                var reviewedAt = rows.getTimestamp(6);
                items.add(new QueueItem(rows.getString(1), rows.getString(2), rows.getString(3),
                        CandidateReview.Decision.valueOf(rows.getString(4)), rows.getString(5),
                        reviewedAt == null ? null : reviewedAt.toInstant(), rows.getInt(7)));
            }
            return List.copyOf(items);
        }
    }
}
