package ro.bitboy.dailysignals;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerEndpointAuthorizationTest {
    private static final String PRODUCER = "producer-token-0123456789abcdef";
    private static final String EDITOR = "editor-token-0123456789abcdef";
    private static final String LISTENER = "listener-token-0123456789abcdef";

    @Test void enforcesTheRoleMatrixAtBothProducerOperations() throws Exception {
        HttpServer server = DailySignals.createServer(0, ReviewerTokens.parse(
                PRODUCER + "=PRODUCER," + EDITOR + "=EDITOR," + LISTENER + "=LISTENER"));
        server.start();
        try {
            String queue = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/review-queue";
            HttpClient client = HttpClient.newHttpClient();
            for (String token : new String[]{PRODUCER, EDITOR}) {
                assertEquals(200, client.send(withToken(HttpRequest.newBuilder(URI.create(queue + "/audit")).GET(), token),
                        HttpResponse.BodyHandlers.discarding()).statusCode());
                assertEquals(200, client.send(withToken(HttpRequest.newBuilder(URI.create(queue.replace("/review-queue", "/producer/script")))
                                .PUT(HttpRequest.BodyPublishers.ofString("Today’s edition script.")), token),
                        HttpResponse.BodyHandlers.discarding()).statusCode());
                assertEquals(200, client.send(withToken(HttpRequest.newBuilder(URI.create(queue.replace("/review-queue", "/producer/pronunciations")))
                                .PUT(HttpRequest.BodyPublishers.ofString("SQL=sequel")), token),
                        HttpResponse.BodyHandlers.discarding()).statusCode());
                HttpResponse<byte[]> audio = client.send(withToken(HttpRequest.newBuilder(
                                URI.create(queue.replace("/review-queue", "/producer/audio")))
                                .POST(HttpRequest.BodyPublishers.noBody()), token), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, audio.statusCode());
                assertEquals("audio/wav", audio.headers().firstValue("Content-Type").orElseThrow());
                assertEquals("8000", audio.headers().firstValue("X-Audio-Sample-Rate").orElseThrow());
                assertEquals("1000", audio.headers().firstValue("X-Audio-Duration-Ms").orElseThrow());
                assertEquals("RIFF", new String(audio.body(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
                assertEquals(200, client.send(withToken(HttpRequest.newBuilder(URI.create(queue + "/demo-park-path"))
                                .POST(HttpRequest.BodyPublishers.ofString("APPROVED")), token),
                        HttpResponse.BodyHandlers.discarding()).statusCode());
            }
            assertEquals(403, client.send(withToken(HttpRequest.newBuilder(URI.create(queue + "/audit")).GET(), LISTENER),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403, client.send(withToken(HttpRequest.newBuilder(URI.create(queue + "/demo-park-path"))
                            .POST(HttpRequest.BodyPublishers.ofString("APPROVED")), LISTENER),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403, client.send(withToken(HttpRequest.newBuilder(URI.create(queue.replace("/review-queue", "/producer/script"))).GET(), LISTENER),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403, client.send(withToken(HttpRequest.newBuilder(URI.create(queue.replace("/review-queue", "/producer/pronunciations"))).GET(), LISTENER),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403, client.send(withToken(HttpRequest.newBuilder(URI.create(queue.replace("/review-queue", "/producer/audio")))
                            .POST(HttpRequest.BodyPublishers.noBody()), LISTENER),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403, client.send(withToken(HttpRequest.newBuilder(URI.create(queue.replace("/review-queue", "/producer/edition"))).GET(), LISTENER),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            HttpResponse<Void> anonymous = client.send(HttpRequest.newBuilder(URI.create(queue + "/audit")).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertEquals(401, anonymous.statusCode());
            assertEquals("Bearer", anonymous.headers().firstValue("WWW-Authenticate").orElseThrow());
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(queue)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode(), "the non-sensitive queue remains readable");
        } finally {
            server.stop(0);
        }
    }

    @Test void producerApprovesAndPublishesTheEditionInOrder() throws Exception {
        HttpServer server = DailySignals.createServer(0, ReviewerTokens.parse(PRODUCER + "=PRODUCER"));
        server.start();
        try {
            var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/producer/edition");
            var client = HttpClient.newHttpClient();
            var approve = client.send(withToken(HttpRequest.newBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.ofString("APPROVE")), PRODUCER), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, approve.statusCode());
            assertTrue(approve.body().contains("\"status\":\"READY\""));
            var audit = client.send(withToken(HttpRequest.newBuilder(
                            URI.create(uri.toString().replace("/edition", "/editions/producer-today/audit"))).GET(),
                    PRODUCER), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, audit.statusCode());
            assertTrue(audit.body().contains("\"actor\":\"PRODUCER\""));
            assertTrue(audit.body().contains("\"editionRevision\":1"));
            var publish = client.send(withToken(HttpRequest.newBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.ofString("PUBLISH")), PRODUCER), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, publish.statusCode());
            assertTrue(publish.body().contains("\"status\":\"PUBLISHED\""));
        } finally { server.stop(0); }
    }

    private static HttpRequest withToken(HttpRequest.Builder request, String token) {
        return request.header("Authorization", "Bearer " + token).build();
    }
}
