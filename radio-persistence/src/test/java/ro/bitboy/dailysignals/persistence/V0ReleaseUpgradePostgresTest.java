package ro.bitboy.dailysignals.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
@EnabledIfSystemProperty(named = "daily.signals.v0.state", matches = ".+")
class V0ReleaseUpgradePostgresTest {
    @Test void upgradesMaterializedV0ReleaseStateAndPreservesItOnRerun() throws Exception {
        Path state = Path.of(System.getProperty("daily.signals.v0.state")).toAbsolutePath();
        var provenance = new ObjectMapper().readTree(Files.readString(state.resolve("provenance.json")));
        assertEquals("v0.2.0", provenance.path("tag").asText());
        assertTrue(provenance.path("revision").asText().matches("[0-9a-f]{40}"));
        try (var connection = DriverManager.getConnection(System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"), System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            var schema = "release_upgrade_" + java.util.UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                statement.execute("SET search_path TO " + schema);
                MigrationRunner.migrate(connection);
                var importer = new V0FileStateImporter();
                var first = importer.importFrom(connection, state);
                assertTrue(first.stationSettingsImported());
                assertEquals(1, first.editionsImported());
                assertEquals(EditionRepository.Status.PUBLISHED,
                        new EditionRepository().get(connection, "demo-2026-01-15").orElseThrow().status());
                var rerun = importer.importFrom(connection, state);
                assertFalse(rerun.stationSettingsImported());
                assertEquals(0, rerun.editionsImported());
                assertEquals(1, new StationSettingsRepository().load(connection).orElseThrow().revision());
                statement.execute("SET search_path TO public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
