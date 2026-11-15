package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class StationSettingsRepositoryPostgresTest {
    @Test void storesTheSingletonSettingsDocumentAndAdvancesItsRevision() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"), System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            var schema = "settings_" + java.util.UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                statement.execute("SET search_path TO " + schema);
                MigrationRunner.migrate(connection);
                var repository = new StationSettingsRepository();
                assertTrue(repository.load(connection).isEmpty());
                assertEquals(1, repository.save(connection, "{\"configured\":true}").revision());
                assertEquals(2, repository.save(connection, "{\"configured\":true,\"voice\":\"fixture\"}").revision());
                assertEquals(2, repository.load(connection).orElseThrow().revision());
                statement.execute("SET search_path TO public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
