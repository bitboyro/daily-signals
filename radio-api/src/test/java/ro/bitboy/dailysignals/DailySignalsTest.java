package ro.bitboy.dailysignals;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySignalsTest {
    @Test
    void servesHealthAndFixtureAudio() throws Exception {
        HttpServer server = DailySignals.createServer(0);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> health = client.send(
                HttpRequest.newBuilder(URI.create(base + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertEquals("{\"status\":\"ok\"}", health.body());

            HttpResponse<byte[]> audio = client.send(
                HttpRequest.newBuilder(URI.create(base + "/editions/demo/audio")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, audio.statusCode());
            assertEquals("audio/wav", audio.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(audio.body().length > 44);
            assertEquals('R', audio.body()[0]);
            assertEquals('I', audio.body()[1]);
            assertEquals('F', audio.body()[2]);
            assertEquals('F', audio.body()[3]);

            HttpResponse<String> editions = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/editions")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(200, editions.statusCode());
            assertEquals("application/json", editions.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(editions.body().startsWith("{\"schemaVersion\":2,\"editions\":[{\"editionId\":\"demo-2026-01-15\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reviewDecisionsRequireAServerIssuedEditorialToken() throws Exception {
        String producer = "producer-token-0123456789abcdef";
        String editor = "editor-token-0123456789abcdef";
        String listener = "listener-token-0123456789abcdef";
        HttpServer server = DailySignals.createServer(0,
            ReviewerTokens.parse(producer + "=PRODUCER," + editor + "=EDITOR," + listener + "=LISTENER"));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/review-queue";
            HttpClient client = HttpClient.newHttpClient();
            java.util.function.BiFunction<String, String, HttpRequest> decide = (token, body) -> {
                var request = HttpRequest.newBuilder(URI.create(base + "/demo-park-path"))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
                if (token != null) request.header("Authorization", "Bearer " + token);
                return request.build();
            };
            HttpResponse<Void> anonymous = client.send(decide.apply(null, "APPROVED"), HttpResponse.BodyHandlers.discarding());
            assertEquals(401, anonymous.statusCode());
            assertEquals("Bearer", anonymous.headers().firstValue("WWW-Authenticate").orElseThrow());
            assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/demo-park-path"))
                .header("X-Daily-Signals-Role", "PRODUCER").POST(HttpRequest.BodyPublishers.ofString("APPROVED")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(401, client.send(decide.apply("not-a-configured-token-000000", "APPROVED"),
                HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403, client.send(decide.apply(listener, "APPROVED"), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(400, client.send(decide.apply(editor, "PENDING"), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(204, client.send(decide.apply(editor, "APPROVED"), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/missing"))
                .header("Authorization", "Bearer " + producer).POST(HttpRequest.BodyPublishers.ofString("REJECTED")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
            String queue = client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
            assertTrue(queue.contains("{\"storyId\":\"demo-park-path\",\"headline\":\"New walking path opens in Riverside Park\",\"source\":\"Demo Civic Desk\",\"decision\":\"APPROVED\"}"));
            assertTrue(queue.contains("\"storyId\":\"demo-library-hours\"") && queue.contains("\"decision\":\"PENDING\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unconfiguredServerRefusesEveryReviewWrite() throws Exception {
        HttpServer server = DailySignals.createServer(0);
        server.start();
        try {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/review-queue/demo-park-path")).header("Authorization", "Bearer anything-at-all-0123456789")
                .POST(HttpRequest.BodyPublishers.ofString("APPROVED")).build();
            assertEquals(401, HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
        } finally {
            server.stop(0);
        }
    }
}
