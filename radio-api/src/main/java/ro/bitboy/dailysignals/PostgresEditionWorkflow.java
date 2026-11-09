package ro.bitboy.dailysignals;

import ro.bitboy.dailysignals.persistence.EditionRepository;
import ro.bitboy.dailysignals.persistence.ApprovalAuditRepository;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;

/** PostgreSQL-backed approve/publish state machine for the producer workspace. */
final class PostgresEditionWorkflow implements EditionWorkflowBackend {
    private static final String ID = "producer-today";
    private final DatabaseRuntime database;
    private final Clock clock;
    private final EditionRepository editions = new EditionRepository();
    private final ApprovalAuditRepository approvals = new ApprovalAuditRepository();

    PostgresEditionWorkflow(DatabaseRuntime database) { this(database, Clock.systemUTC()); }

    PostgresEditionWorkflow(DatabaseRuntime database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    @Override public String toJson() {
        try (var connection = database.open()) {
            editions.createIfAbsent(connection, ID, LocalDate.now(java.time.Clock.systemUTC()), "UTC");
            var edition = editions.get(connection, ID).orElseThrow();
            return EditionWorkflowStore.json(edition.status().name(), edition.revision());
        } catch (SQLException failure) { throw unavailable(failure); }
    }

    @Override public String auditJson(String editionId) {
        try (var connection = database.open()) {
            if (editions.get(connection, editionId).isEmpty()) throw new IllegalArgumentException("unknown edition");
            var history = approvals.history(connection, editionId).stream()
                    .map(event -> new EditionApprovalAudit.Entry(
                            event.editionRevision(), event.actor(), event.approvedAt()))
                    .toList();
            return EditionApprovalAudit.json(editionId, history);
        } catch (SQLException failure) { throw unavailable(failure); }
    }

    @Override public String transitionJson(String action, String actor) {
        try (var connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                editions.createIfAbsent(connection, ID, LocalDate.now(clock), "UTC");
                var edition = editions.get(connection, ID).orElseThrow();
                String requested = action == null ? "" : action.strip();
                var target = switch (requested) {
                    case "APPROVE" -> EditionRepository.Status.READY;
                    case "PUBLISH" -> EditionRepository.Status.PUBLISHED;
                    default -> throw new IllegalArgumentException("unknown edition action");
                };
                if (!editions.transition(connection, ID, edition.status(), target, edition.revision()))
                    throw new IllegalStateException("edition changed concurrently");
                int revision = edition.revision() + 1;
                if ("APPROVE".equals(requested)) {
                    approvals.append(connection, ID, revision, actor, clock.instant());
                }
                connection.commit();
                return EditionWorkflowStore.json(target.name(), revision);
            } catch (RuntimeException | SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) { throw unavailable(failure); }
    }

    private static IllegalStateException unavailable(SQLException failure) {
        return new IllegalStateException("edition database unavailable", failure);
    }
}
