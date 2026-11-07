package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ro.bitboy.dailysignals.persistence.CandidateRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "DAILY_SIGNALS_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class PostgresReviewQueueTest {
    @Test void migratesAndPersistsApiReviewWrites() throws Exception {
        var database = new DatabaseRuntime(System.getenv("DAILY_SIGNALS_TEST_DATABASE_URL"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_USER"),
                System.getenv("DAILY_SIGNALS_TEST_DATABASE_PASSWORD"));
        database.migrate();
        String id = "api-candidate-" + UUID.randomUUID();
        try (var connection = database.open()) {
            var attribution = new StoryNormalizer.Attribution("api-desk", "API desk",
                    URI.create("https://feeds.example.test/api.xml"), "api-snapshot");
            new CandidateRepository().save(connection, new StoryNormalizer.Story(id, "API candidate", "Summary",
                    Instant.parse("2026-11-07T07:00:00Z"), "api candidate", attribution));
        }

        var queue = new PostgresReviewQueue(database);
        assertTrue(queue.contains(id));
        assertTrue(queue.toJson().contains("\"storyId\":\"" + id + "\"")
                && queue.toJson().contains("\"decision\":\"PENDING\""));
        String token = "database-editor-token-0123456789";
        var server = DailySignals.createServer("127.0.0.1", 0, null,
                ReviewerTokens.parse(token + "=EDITOR"),
                FetchScheduleView.defaults(Clock.systemUTC().instant()), queue);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/review-queue";
            var client = HttpClient.newHttpClient();
            var listed = client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, listed.statusCode());
            assertTrue(listed.body().contains("\"storyId\":\"" + id + "\""));
            var accepted = client.send(HttpRequest.newBuilder(URI.create(base + "/" + id))
                            .header("Authorization", "Bearer " + token)
                            .POST(HttpRequest.BodyPublishers.ofString("APPROVED")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, accepted.statusCode());
            assertTrue(accepted.body().contains("\"decision\":\"APPROVED\""));
        } finally {
            server.stop(0);
        }
        assertTrue(queue.toJson().contains("\"decision\":\"APPROVED\""));
        assertTrue(queue.auditToJson().contains("\"storyId\":\"" + id + "\""));
        assertFalse(queue.contains("missing-" + id));
    }
}
