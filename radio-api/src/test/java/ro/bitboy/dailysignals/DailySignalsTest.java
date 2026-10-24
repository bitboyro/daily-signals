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
}
