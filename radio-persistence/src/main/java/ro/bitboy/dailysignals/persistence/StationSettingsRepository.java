package ro.bitboy.dailysignals.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** Single-row PostgreSQL repository for the validated station settings document. */
public final class StationSettingsRepository {
    public record Snapshot(String document, int revision) {}

    public Optional<Snapshot> load(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT document::text, revision FROM station_settings WHERE singleton = TRUE");
             var rows = statement.executeQuery()) {
            return rows.next() ? Optional.of(new Snapshot(rows.getString(1), rows.getInt(2))) : Optional.empty();
        }
    }

    public Snapshot save(Connection connection, String document) throws SQLException {
        validate(document);
        try (var statement = connection.prepareStatement("""
                INSERT INTO station_settings(singleton, document, revision)
                VALUES (TRUE, CAST(? AS JSONB), 1)
                ON CONFLICT (singleton) DO UPDATE
                SET document = EXCLUDED.document,
                    revision = station_settings.revision + 1,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING document::text, revision
                """)) {
            statement.setString(1, document);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("station settings save returned no row");
                return new Snapshot(rows.getString(1), rows.getInt(2));
            }
        }
    }

    public Optional<Snapshot> createIfAbsent(Connection connection, String document) throws SQLException {
        validate(document);
        try (var statement = connection.prepareStatement("""
                INSERT INTO station_settings(singleton, document, revision)
                VALUES (TRUE, CAST(? AS JSONB), 1)
                ON CONFLICT (singleton) DO NOTHING
                RETURNING document::text, revision
                """)) {
            statement.setString(1, document);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(new Snapshot(rows.getString(1), rows.getInt(2))) : Optional.empty();
            }
        }
    }

    private static void validate(String document) {
        if (document == null || document.isBlank() || document.length() > 50_000)
            throw new IllegalArgumentException("station settings document must be nonempty and at most 50 KB");
    }
}
