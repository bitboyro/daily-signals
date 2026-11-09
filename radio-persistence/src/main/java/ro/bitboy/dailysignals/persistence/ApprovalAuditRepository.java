package ro.bitboy.dailysignals.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Append-only approval evidence for an edition lifecycle. */
public final class ApprovalAuditRepository {
    public record ApprovalEvent(long eventId, String editionId, int editionRevision, String actor, Instant approvedAt) {}

    public void append(Connection connection, String editionId, int editionRevision, String actor, Instant approvedAt)
            throws SQLException {
        if (editionId == null || editionId.isBlank() || editionRevision < 1 || actor == null || actor.isBlank()
                || approvedAt == null) {
            throw new IllegalArgumentException("edition, positive revision, actor and approval time are required");
        }
        try (var statement = connection.prepareStatement(
                "INSERT INTO edition_approval_event (edition_id, edition_revision, actor, approved_at) "
                        + "VALUES (?, ?, ?, ?)")) {
            statement.setString(1, editionId);
            statement.setInt(2, editionRevision);
            statement.setString(3, actor.strip());
            statement.setTimestamp(4, Timestamp.from(approvedAt));
            statement.executeUpdate();
        }
    }

    public List<ApprovalEvent> history(Connection connection, String editionId) throws SQLException {
        if (editionId == null || editionId.isBlank()) throw new IllegalArgumentException("edition is required");
        var history = new ArrayList<ApprovalEvent>();
        try (var statement = connection.prepareStatement(
                "SELECT event_id, edition_id, edition_revision, actor, approved_at "
                        + "FROM edition_approval_event WHERE edition_id = ? ORDER BY approved_at, event_id")) {
            statement.setString(1, editionId);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    history.add(new ApprovalEvent(result.getLong(1), result.getString(2), result.getInt(3),
                            result.getString(4), result.getTimestamp(5).toInstant()));
                }
            }
        }
        return List.copyOf(history);
    }
}
