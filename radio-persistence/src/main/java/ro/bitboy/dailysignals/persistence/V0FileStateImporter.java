package ro.bitboy.dailysignals.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/** Imports the durable file state used before PostgreSQL became mandatory. */
public final class V0FileStateImporter {
    public static final String STATION_SETTINGS = "station-settings.json";
    private static final long MAX_DOCUMENT_BYTES = 50_000;
    private final StationSettingsRepository settings = new StationSettingsRepository();

    public record Result(boolean stationSettingsImported) {}

    public Result importFrom(Connection connection, Path stateDirectory) throws IOException, SQLException {
        if (connection == null || stateDirectory == null || !Files.isDirectory(stateDirectory, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("a database connection and v0.x state directory are required");
        Path document = stateDirectory.resolve(STATION_SETTINGS);
        if (!Files.exists(document, LinkOption.NOFOLLOW_LINKS)) return new Result(false);
        if (!Files.isRegularFile(document, LinkOption.NOFOLLOW_LINKS) || Files.size(document) > MAX_DOCUMENT_BYTES)
            throw new IllegalArgumentException("v0.x station settings must be a regular file of at most 50 KB");
        return new Result(settings.createIfAbsent(connection, Files.readString(document)).isPresent());
    }
}
