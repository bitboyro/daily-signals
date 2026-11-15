package ro.bitboy.dailysignals;

import ro.bitboy.dailysignals.persistence.StationSettingsRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/** PostgreSQL adapter for the first-run and admin station settings document. */
final class PostgresStationSettings implements FirstRunSetup.Store {
    private final DatabaseRuntime database;
    private final StationSettingsRepository settings = new StationSettingsRepository();

    PostgresStationSettings(DatabaseRuntime database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override public Optional<String> load() throws IOException {
        try (var connection = database.open()) {
            return settings.load(connection).map(StationSettingsRepository.Snapshot::document);
        } catch (SQLException failure) {
            throw new IOException("station settings database unavailable", failure);
        }
    }

    @Override public void save(String document) throws IOException {
        try (var connection = database.open()) {
            settings.save(connection, document);
        } catch (SQLException failure) {
            throw new IOException("station settings database unavailable", failure);
        }
    }
}
