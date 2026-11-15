package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test void importsV0FileSettingsOnceWithoutOverwritingPostgres(@TempDir Path legacyState) throws Exception {
        Files.writeString(legacyState.resolve(V0FileStateImporter.STATION_SETTINGS), """
                {"schemaVersion":1,"configured":true,"sources":[{"id":"public-desk",
                "name":"Public desk","url":"https://news.example.invalid/public.xml","priority":10}],
                "voice":"fixture","zone":"Europe/Bucharest","window":"06:30"}
                """);
        Files.writeString(legacyState.resolve(V0FileStateImporter.EDITIONS), """
                [
                  {"editionId":"morning-2026-01-14","airDate":"2026-01-14"},
                  {"editionId":"morning-2026-01-15","airDate":"2026-01-15","zoneId":"Europe/Bucharest",
                   "status":"READY","revision":2}
                ]
                """);
        try (var connection = DriverManager.getConnection(System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"), System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            var schema = "v0_state_" + java.util.UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                statement.execute("SET search_path TO " + schema);
                MigrationRunner.migrate(connection);
                var importer = new V0FileStateImporter();
                var first = importer.importFrom(connection, legacyState);
                assertTrue(first.stationSettingsImported());
                assertEquals(2, first.editionsImported());
                var rerun = importer.importFrom(connection, legacyState);
                assertFalse(rerun.stationSettingsImported());
                assertEquals(0, rerun.editionsImported());
                var imported = new StationSettingsRepository().load(connection).orElseThrow();
                assertEquals(1, imported.revision());
                assertTrue(imported.document().contains("Europe/Bucharest"));
                var morning = new EditionRepository().get(connection, "morning-2026-01-15").orElseThrow();
                assertEquals(EditionRepository.Status.READY, morning.status());
                assertEquals(2, morning.revision());
                assertEquals(2, statement.executeUpdate("UPDATE edition_lifecycle SET updated_at = updated_at "
                        + "WHERE edition_id LIKE 'morning-%'"));
                statement.execute("SET search_path TO public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
