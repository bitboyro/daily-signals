package ro.bitboy.dailysignals.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

public final class MigrationRunner {
    private MigrationRunner() {}
    public static void migrate(Connection connection) throws SQLException, IOException {
        try (var input = MigrationRunner.class.getResourceAsStream("/db/migration/V1__edition_lifecycle.sql")) {
            if (input == null) throw new IOException("edition migration resource missing");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (var statement = connection.createStatement()) { statement.execute(sql); }
        }
    }
}
