package ro.bitboy.dailysignals.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ro.bitboy.dailysignals.LocalS3ObjectAdapter;
import ro.bitboy.dailysignals.V0RecordingImporter;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
@EnabledIfSystemProperty(named = "daily.signals.v0.state", matches = ".+")
class V0ReleaseUpgradePostgresTest {
    @Test void upgradesMaterializedV0ReleaseStateAndPreservesItOnRerun() throws Exception {
        Path state = Path.of(System.getProperty("daily.signals.v0.state")).toAbsolutePath();
        var provenance = new ObjectMapper().readTree(Files.readString(state.resolve("provenance.json")));
        assertEquals("v0.2.0", provenance.path("tag").asText());
        assertTrue(provenance.path("revision").asText().matches("[0-9a-f]{40}"));
        migrateLegacyRecording(state);
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

    private static void migrateLegacyRecording(Path state) throws Exception {
        Map<String, byte[]> objects = new ConcurrentHashMap<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/upgrade-fixtures/editions/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("PUT".equals(exchange.getRequestMethod())) {
                objects.put(path, exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(200, -1);
            } else if ("GET".equals(exchange.getRequestMethod()) && objects.containsKey(path)) {
                byte[] object = objects.get(path);
                exchange.sendResponseHeaders(200, object.length);
                exchange.getResponseBody().write(object);
            } else exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            var store = new LocalS3ObjectAdapter(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "upgrade-fixtures");
            var importer = new V0RecordingImporter();
            assertEquals(new V0RecordingImporter.Result(1, 0), importer.importFrom(state, store));
            byte[] source = Files.readAllBytes(state.resolve("recordings/demo-2026-01-15.wav"));
            byte[] object = objects.get("/upgrade-fixtures/editions/demo-2026-01-15.wav");
            assertArrayEquals(source, object);
            assertEquals(V0RecordingImporter.sha256(source), V0RecordingImporter.sha256(object));
            assertEquals(new V0RecordingImporter.Result(0, 1), importer.importFrom(state, store));
        } finally { server.stop(0); }
    }
}
