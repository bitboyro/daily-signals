package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ro.bitboy.dailysignals.StoryNormalizer;

import java.net.URI;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class CandidateRepositoryPostgresTest {
    @Test void writesNormalizedCandidatesIdempotentlyWithPendingReviews() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            MigrationRunner.migrate(connection);
            String id = "candidate-" + UUID.randomUUID();
            var attribution = new StoryNormalizer.Attribution("public-desk", "Public desk",
                    URI.create("https://feeds.example.test/rss.xml"), "snapshot-1");
            var first = new StoryNormalizer.Story(id, "Original headline", "Summary",
                    Instant.parse("2026-11-07T06:00:00Z"), "original headline", attribution);
            var updated = new StoryNormalizer.Story(id, "Corrected headline", "Updated summary",
                    Instant.parse("2026-11-07T06:05:00Z"), "corrected headline", attribution);
            var repository = new CandidateRepository();
            var writer = new IngestedCandidateWriter(repository);

            assertEquals(1, writer.write(connection, List.of(first)));
            assertEquals(1, writer.write(connection, List.of(updated)));
            var stored = repository.get(connection, id).orElseThrow();
            assertEquals("Corrected headline", stored.headline());
            assertEquals(Instant.parse("2026-11-07T06:05:00Z"), stored.capturedAt());
            try (var statement = connection.prepareStatement(
                    "SELECT decision, count(*) OVER () FROM candidate_review WHERE candidate_id = ?")) {
                statement.setString(1, id);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    assertEquals("PENDING", rows.getString(1));
                    assertEquals(1, rows.getInt(2));
                }
            }
        }
    }
}
