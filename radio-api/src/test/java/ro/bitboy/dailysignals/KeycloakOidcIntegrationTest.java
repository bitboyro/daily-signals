package ro.bitboy.dailysignals;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KeycloakOidcIntegrationTest {
    @Test void authenticatesARealKeycloakTokenThroughTheProducerEndpoint() throws Exception {
        String issuer = System.getenv("OIDC_TEST_ISSUER");
        String token = System.getenv("OIDC_TEST_TOKEN");
        assumeTrue(issuer != null && token != null, "Keycloak integration environment is not configured");
        var authenticator = ProducerAuthenticator.fromEnvironment(
                Map.of(ProducerAuthenticator.ISSUER_ENV, issuer), ReviewerTokens.none());
        var identity = authenticator.authenticate("Bearer " + token).orElseThrow();
        assertTrue(!identity.subject().isBlank());
        assertEquals("EDITOR", identity.role());

        HttpServer server = DailySignals.createServer("127.0.0.1", 0, null, authenticator,
                FetchScheduleView.defaults(Clock.systemUTC().instant()), ReviewQueue.fixture());
        server.start();
        try {
            String queue = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/review-queue";
            var client = HttpClient.newHttpClient();
            var decision = HttpRequest.newBuilder(URI.create(queue + "/demo-park-path"))
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString("APPROVED")).build();
            assertEquals(200, client.send(decision, HttpResponse.BodyHandlers.discarding()).statusCode());
            String body = client.send(HttpRequest.newBuilder(URI.create(queue)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            assertTrue(body.contains("\"reviewer\":\"" + identity.subject() + "\""));
        } finally {
            server.stop(0);
        }
    }
}
