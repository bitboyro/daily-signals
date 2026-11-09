package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static ro.bitboy.dailysignals.persistence.EditionRepository.Status.DRAFT;
import static ro.bitboy.dailysignals.persistence.EditionRepository.Status.READY;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class ApprovalAuditRepositoryPostgresTest {
    @Test void storesTheApprovalActorAndTimeBesideTheLifecycleRevision() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            MigrationRunner.migrate(connection);
            String editionId = "audit-" + UUID.randomUUID();
            var editions = new EditionRepository();
            var approvals = new ApprovalAuditRepository();
            editions.create(connection, editionId, LocalDate.of(2026, 11, 9), "Europe/Bucharest");
            assertTrue(editions.transition(connection, editionId, DRAFT, READY, 0));

            Instant approvedAt = Instant.parse("2026-11-09T19:30:00Z");
            approvals.append(connection, editionId, 1, "producer-42", approvedAt);

            var event = assertDoesNotThrow(() -> approvals.history(connection, editionId)).getFirst();
            assertEquals(editionId, event.editionId());
            assertEquals(1, event.editionRevision());
            assertEquals("producer-42", event.actor());
            assertEquals(approvedAt, event.approvedAt());
            assertThrows(IllegalArgumentException.class,
                    () -> approvals.append(connection, editionId, 1, " ", approvedAt));
        }
    }
}
