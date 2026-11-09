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
            assertTrue(ddl.contains("source_category VARCHAR(80) NOT NULL DEFAULT 'general'"));
        }
    }

    @Test void editorialMigrationDefinesCandidatesReviewsAndEditionOrdering() throws Exception {
        try (var input = MigrationRunner.class.getResourceAsStream("/db/migration/V3__editorial_state.sql")) {
            assertNotNull(input);
            String ddl = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS candidate_story"));
            assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS candidate_review"));
            assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS edition_candidate"));
            assertTrue(ddl.contains("CHECK (decision IN ('PENDING', 'APPROVED', 'REJECTED'))"));
            assertTrue(ddl.contains("UNIQUE (edition_id, position)"));
            assertTrue(ddl.contains("REFERENCES candidate_story(candidate_id) ON DELETE CASCADE"));
        }
    }

    @Test void pronunciationMigrationDefinesLexiconAndRevisionState() throws Exception {
        try (var input = MigrationRunner.class.getResourceAsStream("/db/migration/V5__pronunciation_lexicon.sql")) {
            assertNotNull(input);
            String ddl = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS pronunciation_lexicon"));
            assertTrue(ddl.contains("pronunciation_lexicon_state"));
            assertTrue(ddl.contains("('AI', 'A I')"));
        }
    }

    @Test void approvalAuditMigrationRequiresActorTimeAndEditionRevision() throws Exception {
        try (var input = MigrationRunner.class.getResourceAsStream("/db/migration/V6__approval_audit.sql")) {
            assertNotNull(input);
            String ddl = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS edition_approval_event"));
            assertTrue(ddl.contains("actor VARCHAR(200) NOT NULL"));
            assertTrue(ddl.contains("approved_at TIMESTAMPTZ NOT NULL"));
            assertTrue(ddl.contains("UNIQUE (edition_id, edition_revision)"));
        }
    }
}
