package ro.bitboy.dailysignals.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

public final class MigrationRunner {
    private MigrationRunner() {}
    public static void migrate(Connection connection) throws SQLException, IOException {
        for (var resource : new String[]{"V1__edition_lifecycle.sql", "V2__source_category_backfill.sql"}) {
            try (var input = MigrationRunner.class.getResourceAsStream("/db/migration/" + resource)) {
                if (input == null) throw new IOException("edition migration resource missing: " + resource);
                String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                try (var statement = connection.createStatement()) { statement.execute(sql); }
            }
        }
    }
}
