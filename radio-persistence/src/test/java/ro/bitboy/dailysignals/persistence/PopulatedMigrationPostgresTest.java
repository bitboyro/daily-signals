package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class PopulatedMigrationPostgresTest {
    @Test void migratesEveryLifecycleStateAndBackfillsCategory() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"), System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            var schema = "populated_" + java.util.UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                statement.execute("SET search_path TO " + schema);
                try (var input = getClass().getResourceAsStream("/db/populated-editions.sql")) {
                    assertNotNull(input);
                    statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                MigrationRunner.migrate(connection);
                try (var rows = statement.executeQuery("SELECT count(*), count(source_category), min(source_category) FROM edition_lifecycle")) {
                    assertTrue(rows.next());
                    assertEquals(3, rows.getInt(1));
                    assertEquals(3, rows.getInt(2));
                    assertEquals("general", rows.getString(3));
                }
                statement.execute("SET search_path TO public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
