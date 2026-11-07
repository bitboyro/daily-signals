package ro.bitboy.dailysignals.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Atomic PostgreSQL storage for the producer pronunciation lexicon. */
public final class PronunciationRepository {
    public record Snapshot(Map<String, String> entries, int revision) {
        public Snapshot { entries = Map.copyOf(entries); }
    }

    public Snapshot load(Connection connection) throws SQLException {
        var entries = new LinkedHashMap<String, String>();
        try (var statement = connection.prepareStatement(
                "SELECT term, spoken_form FROM pronunciation_lexicon ORDER BY term");
             var rows = statement.executeQuery()) {
            while (rows.next()) entries.put(rows.getString(1), rows.getString(2));
        }
        try (var statement = connection.prepareStatement(
                "SELECT revision FROM pronunciation_lexicon_state WHERE singleton = TRUE");
             var rows = statement.executeQuery()) {
            if (!rows.next()) throw new SQLException("pronunciation revision row missing");
            return new Snapshot(entries, rows.getInt(1));
        }
    }

    public Snapshot replace(Connection connection, Map<String, String> entries) throws SQLException {
        if (entries == null || entries.isEmpty()) throw new IllegalArgumentException("pronunciation entries required");
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM pronunciation_lexicon");
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO pronunciation_lexicon(term, spoken_form) VALUES (?, ?)")) {
                for (var entry : entries.entrySet()) {
                    statement.setString(1, entry.getKey());
                    statement.setString(2, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE pronunciation_lexicon_state SET revision = revision + 1 WHERE singleton = TRUE");
            }
            connection.commit();
            return load(connection);
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }
}
