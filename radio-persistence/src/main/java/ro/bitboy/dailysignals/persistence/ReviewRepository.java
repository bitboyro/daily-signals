package ro.bitboy.dailysignals.persistence;

import ro.bitboy.dailysignals.CandidateReview;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** Persists producer decisions with optimistic concurrency. */
public final class ReviewRepository {
    public record StoredReview(String candidateId, CandidateReview.Decision decision, String reviewer,
                               String reason, Instant reviewedAt, int revision) {}

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
}
