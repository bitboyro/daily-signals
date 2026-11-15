package ro.bitboy.dailysignals.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/** Imports the durable file state used before PostgreSQL became mandatory. */
public final class V0FileStateImporter {
    public static final String STATION_SETTINGS = "station-settings.json";
    public static final String EDITIONS = "editions.json";
    private static final long MAX_DOCUMENT_BYTES = 50_000;
    private static final long MAX_EDITIONS_BYTES = 1_000_000;
    private static final int MAX_EDITIONS = 10_000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final StationSettingsRepository settings = new StationSettingsRepository();
    private final EditionRepository editions = new EditionRepository();

    public record Result(boolean stationSettingsImported, int editionsImported) {}

    public Result importFrom(Connection connection, Path stateDirectory) throws IOException, SQLException {
        if (connection == null || stateDirectory == null || !Files.isDirectory(stateDirectory, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("a database connection and v0.x state directory are required");
        Path document = stateDirectory.resolve(STATION_SETTINGS);
        boolean importedSettings = false;
        if (Files.exists(document, LinkOption.NOFOLLOW_LINKS)) {
            requireRegularFile(document, MAX_DOCUMENT_BYTES, "station settings");
            importedSettings = settings.createIfAbsent(connection, Files.readString(document)).isPresent();
        }
        return new Result(importedSettings, importEditions(connection, stateDirectory.resolve(EDITIONS)));
    }

    private int importEditions(Connection connection, Path document) throws IOException, SQLException {
        if (!Files.exists(document, LinkOption.NOFOLLOW_LINKS)) return 0;
        requireRegularFile(document, MAX_EDITIONS_BYTES, "edition history");
        JsonNode root = JSON.readTree(Files.readString(document));
        if (root == null || !root.isArray() || root.size() > MAX_EDITIONS)
            throw new IllegalArgumentException("v0.x edition history must be an array of at most 10000 editions");
        int imported = 0;
        for (JsonNode edition : root) {
            String id = requiredText(edition, "editionId");
            LocalDate airDate = LocalDate.parse(requiredText(edition, "airDate"));
            String zone = edition.path("zoneId").asText("UTC");
            String statusName = edition.path("status").asText("PUBLISHED");
            int revision = edition.path("revision").asInt(0);
            EditionRepository.Status status;
            try { status = EditionRepository.Status.valueOf(statusName); }
            catch (IllegalArgumentException unknown) { throw new IllegalArgumentException("invalid v0.x edition status", unknown); }
            if (editions.importIfAbsent(connection, id, airDate, zone, status, revision)) imported++;
        }
        return imported;
    }

    private static String requiredText(JsonNode node, String name) {
        String value = node.path(name).asText();
        if (value.isBlank()) throw new IllegalArgumentException("v0.x edition " + name + " is required");
        return value;
    }

    private static void requireRegularFile(Path path, long maximum, String label) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum)
            throw new IllegalArgumentException("v0.x " + label + " must be a regular file of at most " + maximum + " bytes");
    }
}
