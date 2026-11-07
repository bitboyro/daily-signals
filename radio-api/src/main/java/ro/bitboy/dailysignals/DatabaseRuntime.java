package ro.bitboy.dailysignals;

import ro.bitboy.dailysignals.persistence.MigrationRunner;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

/** Validated database settings and the startup migration boundary for the running API. */
public record DatabaseRuntime(String url, String user, String password) {
    private static final String URL = "DAILY_SIGNALS_DATABASE_URL";
    private static final String USER = "DAILY_SIGNALS_DATABASE_USER";
    private static final String PASSWORD_ENV = "DAILY_SIGNALS_DATABASE_PASSWORD";

    public DatabaseRuntime {
        if (url == null || url.isBlank() || user == null || user.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("database URL, user and password are required");
        }
    }

    public static Optional<DatabaseRuntime> fromEnvironment(Map<String, String> environment) {
        boolean configured = environment.containsKey(URL) || environment.containsKey(USER) || environment.containsKey(PASSWORD_ENV);
        if (!configured) return Optional.empty();
        return Optional.of(new DatabaseRuntime(environment.get(URL), environment.get(USER), environment.get(PASSWORD_ENV)));
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void migrate() throws SQLException, IOException {
        try (var connection = open()) {
            MigrationRunner.migrate(connection);
        }
    }
}
