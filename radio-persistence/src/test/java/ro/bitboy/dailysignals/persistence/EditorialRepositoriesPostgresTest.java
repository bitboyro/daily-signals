package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ro.bitboy.dailysignals.CandidateReview;
import ro.bitboy.dailysignals.StoryNormalizer;

import java.net.URI;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class EditorialRepositoriesPostgresTest {
    @Test void savesAReviewAndKeepsEditionCandidateOrder() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"))) {
            MigrationRunner.migrate(connection);
            String suffix = UUID.randomUUID().toString();
            var attribution = new StoryNormalizer.Attribution("public-desk", "Public desk",
                    URI.create("https://feeds.example.test/rss.xml"), "snapshot-" + suffix);
            var candidateRepository = new CandidateRepository();
            var ids = List.of("candidate-a-" + suffix, "candidate-b-" + suffix);
            for (String id : ids) {
                candidateRepository.save(connection, new StoryNormalizer.Story(id, "Headline " + id, "Summary",
                        Instant.parse("2026-11-07T06:00:00Z"), "headline " + id, attribution));
            }

            var reviews = new ReviewRepository();
            assertTrue(reviews.decide(connection, ids.getFirst(), CandidateReview.Decision.APPROVED,
                    "producer@example.test", "ready", 0));
            assertFalse(reviews.decide(connection, ids.getFirst(), CandidateReview.Decision.REJECTED,
                    "producer@example.test", "stale", 0));
            assertEquals(CandidateReview.Decision.APPROVED, reviews.get(connection, ids.getFirst()).orElseThrow().decision());

            String editionId = "edition-" + suffix;
            new EditionRepository().create(connection, editionId, LocalDate.of(2026, 11, 7), "Europe/Bucharest");
            var editionCandidates = new EditionCandidateRepository();
            editionCandidates.replace(connection, editionId, List.of(ids.get(1), ids.getFirst()));
            assertEquals(List.of(ids.get(1), ids.getFirst()), editionCandidates.candidateIds(connection, editionId));
        }
    }
}
