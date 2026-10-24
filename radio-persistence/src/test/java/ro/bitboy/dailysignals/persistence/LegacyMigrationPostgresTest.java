package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class LegacyMigrationPostgresTest {
    @Test void migratesLegacyFixtureWithoutLosingDraft() throws Exception {
        try (var connection = DriverManager.getConnection(
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            String fixture;
            try (var input = getClass().getResourceAsStream("/db/legacy-edition.sql")) {
                assertNotNull(input); fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            // The legacy fixture is a reference shape; execute it in an isolated temporary schema.
            String schema = "legacy_" + java.util.UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                statement.execute("SET search_path TO " + schema);
                statement.execute(fixture);
                MigrationRunner.migrate(connection);
                var edition = new EditionRepository().get(connection, "legacy-edition").orElseThrow();
                assertEquals(EditionRepository.Status.DRAFT, edition.status());
                assertEquals(0, edition.revision());
                statement.execute("SET search_path TO public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
