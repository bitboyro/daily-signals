package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static ro.bitboy.dailysignals.persistence.EditionRepository.Status.*;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class EditionRepositoryPostgresTest {
    @Test void migratesAndRejectsStaleOrSkippedPublication() throws Exception {
        String url = System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL");
        try (var connection = DriverManager.getConnection(url,
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            MigrationRunner.migrate(connection);
            var repository = new EditionRepository();
            String id = "test-" + UUID.randomUUID();
            repository.create(connection, id, LocalDate.of(2026, 1, 15), "Europe/Bucharest");
            assertEquals(DRAFT, repository.get(connection, id).orElseThrow().status());
            assertThrows(IllegalArgumentException.class, () -> repository.transition(connection, id, DRAFT, PUBLISHED, 0));
            assertTrue(repository.transition(connection, id, DRAFT, READY, 0));
            assertFalse(repository.transition(connection, id, READY, PUBLISHED, 0));
            assertTrue(repository.transition(connection, id, READY, PUBLISHED, 1));
            assertEquals(2, repository.get(connection, id).orElseThrow().revision());
        }
    }
}
