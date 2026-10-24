package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MigrationResourceTest {
    @Test void migrationDefinesStateAndOptimisticRevision() throws Exception {
        try (var input = MigrationRunner.class.getResourceAsStream("/db/migration/V1__edition_lifecycle.sql")) {
            assertNotNull(input);
            String ddl = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(ddl.contains("PUBLISHED"));
            assertTrue(ddl.contains("revision INTEGER NOT NULL"));
        }
    }
}
