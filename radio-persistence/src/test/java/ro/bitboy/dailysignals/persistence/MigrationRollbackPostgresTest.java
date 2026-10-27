package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class MigrationRollbackPostgresTest {
    @Test void rollbackRestoresThePreMigrationShapeAndRows() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"), System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            var schema = "rollback_" + java.util.UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                statement.execute("SET search_path TO " + schema);
                try (var input = getClass().getResourceAsStream("/db/populated-editions.sql")) {
                    statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                connection.setAutoCommit(false);
                MigrationRunner.migrate(connection);
                connection.rollback();
                connection.setAutoCommit(true);
                try (var rows = statement.executeQuery("SELECT count(*) FROM edition_lifecycle")) {
                    assertTrue(rows.next());
                    assertEquals(3, rows.getInt(1));
                }
                assertThrows(SQLException.class, () -> statement.executeQuery("SELECT source_category FROM edition_lifecycle"));
                statement.execute("SET search_path TO public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
