package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class PronunciationRepositoryPostgresTest {
    @Test void migratesDefaultsAndAtomicallyReplacesTheLexicon() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"), System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            var schema = "pronunciation_" + java.util.UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                statement.execute("SET search_path TO " + schema);
                MigrationRunner.migrate(connection);
                var repository = new PronunciationRepository();
                assertEquals("A I", repository.load(connection).entries().get("AI"));
                var saved = repository.replace(connection, Map.of("SQL", "sequel", "S3", "S three"));
                assertEquals(1, saved.revision());
                assertEquals(Map.of("SQL", "sequel", "S3", "S three"), saved.entries());
                statement.execute("SET search_path TO public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
