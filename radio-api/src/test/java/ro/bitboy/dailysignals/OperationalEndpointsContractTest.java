package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalEndpointsContractTest {
    @Test void exposesActuatorHealthAndPrometheusMetrics() throws Exception {
        var server = DailySignals.createServer(0);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var client = HttpClient.newHttpClient();
            var health = client.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertEquals("application/json", health.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(health.body().contains("\"status\":\"UP\""));
            assertTrue(health.body().contains("\"stationSettings\":{\"status\":\"UNKNOWN\"}"));

            var first = client.send(HttpRequest.newBuilder(URI.create(base + "/actuator/prometheus")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, first.statusCode());
            assertTrue(first.headers().firstValue("Content-Type").orElseThrow().startsWith("text/plain"));
            assertTrue(first.body().contains("daily_signals_api_up 1"));
            assertTrue(first.body().contains("daily_signals_station_configured 0"));
            assertTrue(first.body().contains("daily_signals_metrics_scrapes_total 1"));
            String second = client.send(HttpRequest.newBuilder(URI.create(base + "/actuator/prometheus")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            assertTrue(second.contains("daily_signals_metrics_scrapes_total 2"));
        } finally {
            server.stop(0);
        }
    }
}
