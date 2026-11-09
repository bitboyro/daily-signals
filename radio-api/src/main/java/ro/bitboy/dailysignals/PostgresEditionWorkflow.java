package ro.bitboy.dailysignals;

import ro.bitboy.dailysignals.persistence.EditionRepository;

import java.sql.SQLException;
import java.time.LocalDate;

/** PostgreSQL-backed approve/publish state machine for the producer workspace. */
final class PostgresEditionWorkflow implements EditionWorkflowBackend {
    private static final String ID = "producer-today";
    private final DatabaseRuntime database;
    private final EditionRepository editions = new EditionRepository();

    PostgresEditionWorkflow(DatabaseRuntime database) { this.database = database; }

    @Override public String toJson() {
        try (var connection = database.open()) {
            editions.createIfAbsent(connection, ID, LocalDate.now(java.time.Clock.systemUTC()), "UTC");
            var edition = editions.get(connection, ID).orElseThrow();
            return EditionWorkflowStore.json(edition.status().name(), edition.revision());
        } catch (SQLException failure) { throw unavailable(failure); }
    }

    @Override public String transitionJson(String action) {
        try (var connection = database.open()) {
            editions.createIfAbsent(connection, ID, LocalDate.now(java.time.Clock.systemUTC()), "UTC");
            var edition = editions.get(connection, ID).orElseThrow();
            var target = switch (action == null ? "" : action.strip()) {
                case "APPROVE" -> EditionRepository.Status.READY;
                case "PUBLISH" -> EditionRepository.Status.PUBLISHED;
                default -> throw new IllegalArgumentException("unknown edition action");
            };
            if (!editions.transition(connection, ID, edition.status(), target, edition.revision()))
                throw new IllegalStateException("edition changed concurrently");
            return toJson();
        } catch (SQLException failure) { throw unavailable(failure); }
    }

    private static IllegalStateException unavailable(SQLException failure) {
        return new IllegalStateException("edition database unavailable", failure);
    }
}
