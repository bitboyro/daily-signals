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
    void reviewDecisionsRequireAnEditorialRole() throws Exception {
        HttpServer server = DailySignals.createServer(0);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/review-queue";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder approve = HttpRequest.newBuilder(URI.create(base + "/demo-park-path"))
                .POST(HttpRequest.BodyPublishers.ofString("APPROVED"));
            assertEquals(403, client.send(approve.build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403, client.send(approve.header("X-Daily-Signals-Role", "LISTENER").build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
            HttpRequest.Builder asEditor = HttpRequest.newBuilder(URI.create(base + "/demo-park-path"))
                .header("X-Daily-Signals-Role", "EDITOR");
            assertEquals(400, client.send(asEditor.POST(HttpRequest.BodyPublishers.ofString("PENDING")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(204, client.send(asEditor.POST(HttpRequest.BodyPublishers.ofString("APPROVED")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/missing"))
                .header("X-Daily-Signals-Role", "PRODUCER").POST(HttpRequest.BodyPublishers.ofString("REJECTED")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
            String queue = client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
            assertTrue(queue.contains("{\"storyId\":\"demo-park-path\",\"headline\":\"New walking path opens in Riverside Park\",\"source\":\"Demo Civic Desk\",\"decision\":\"APPROVED\"}"));
            assertTrue(queue.contains("\"storyId\":\"demo-library-hours\"") && queue.contains("\"decision\":\"PENDING\""));
        } finally {
            server.stop(0);
        }
    }
}
