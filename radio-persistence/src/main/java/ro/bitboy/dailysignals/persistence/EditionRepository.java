package ro.bitboy.dailysignals.persistence;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

public final class EditionRepository {
    public enum Status { DRAFT, READY, PUBLISHED }
    public record StoredEdition(String id, LocalDate airDate, String zoneId, Status status, int revision) {}

    public void create(Connection connection, String id, LocalDate day, String zoneId) throws SQLException {
        if (id == null || id.isBlank() || day == null || zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("edition identity, date and zone are required");
        }
        try (var statement = connection.prepareStatement(
                "INSERT INTO edition_lifecycle (edition_id, air_date, zone_id, status) VALUES (?, ?, ?, 'DRAFT')")) {
            statement.setString(1, id);
            statement.setDate(2, Date.valueOf(day));
            statement.setString(3, zoneId);
            statement.executeUpdate();
        }
    }

    public Optional<StoredEdition> get(Connection connection, String id) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT edition_id, air_date, zone_id, status, revision FROM edition_lifecycle WHERE edition_id = ?")) {
            statement.setString(1, id);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new StoredEdition(result.getString(1), result.getDate(2).toLocalDate(),
                        result.getString(3), Status.valueOf(result.getString(4)), result.getInt(5)));
            }
        }
    }

    public boolean transition(Connection connection, String id, Status from, Status to, int expectedRevision) throws SQLException {
        if (!allowed(from, to)) throw new IllegalArgumentException("invalid edition transition");
        try (var statement = connection.prepareStatement(
                "UPDATE edition_lifecycle SET status = ?, revision = revision + 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE edition_id = ? AND status = ? AND revision = ?")) {
            statement.setString(1, to.name());
            statement.setString(2, id);
            statement.setString(3, from.name());
            statement.setInt(4, expectedRevision);
            return statement.executeUpdate() == 1;
        }
    }

    public static boolean allowed(Status from, Status to) {
        return from == Status.DRAFT && to == Status.READY || from == Status.READY && to == Status.PUBLISHED;
    }
}
