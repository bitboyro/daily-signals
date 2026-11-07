package ro.bitboy.dailysignals.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;

/** Owns the stable producer order of candidates within an edition. */
public final class EditionCandidateRepository {
    public void replace(Connection connection, String editionId, List<String> candidateIds) throws SQLException {
        if (connection == null || editionId == null || editionId.isBlank() || candidateIds == null
                || candidateIds.stream().anyMatch(id -> id == null || id.isBlank())
                || new HashSet<>(candidateIds).size() != candidateIds.size()) {
            throw new IllegalArgumentException("edition and unique candidate ids are required");
        }
        try (var delete = connection.prepareStatement("DELETE FROM edition_candidate WHERE edition_id = ?")) {
            delete.setString(1, editionId);
            delete.executeUpdate();
        }
        try (var insert = connection.prepareStatement(
                "INSERT INTO edition_candidate (edition_id, candidate_id, position) VALUES (?, ?, ?)")) {
            for (int position = 0; position < candidateIds.size(); position++) {
                insert.setString(1, editionId);
                insert.setString(2, candidateIds.get(position));
                insert.setInt(3, position);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public List<String> candidateIds(Connection connection, String editionId) throws SQLException {
        if (connection == null || editionId == null || editionId.isBlank()) {
            throw new IllegalArgumentException("connection and edition id are required");
        }
        try (var statement = connection.prepareStatement(
                "SELECT candidate_id FROM edition_candidate WHERE edition_id = ? ORDER BY position")) {
            statement.setString(1, editionId);
            try (var rows = statement.executeQuery()) {
                var result = new java.util.ArrayList<String>();
                while (rows.next()) result.add(rows.getString(1));
                return List.copyOf(result);
            }
        }
    }
}
