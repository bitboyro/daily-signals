package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRoutesContractTest {
    @Test void everyPublishedLegacyRouteRedirectsToAPlayableEdition() throws Exception {
        var contract = new ObjectMapper().readTree(Files.readString(Path.of("../contracts/legacy-routes.json")));
        var declared = new HashMap<String, String>();
        HttpServer server = DailySignals.createServer(0);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient manual = HttpClient.newHttpClient();
            HttpClient following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            for (var route : contract.get("routes")) {
                declared.put(route.get("from").asText(), route.get("to").asText());
                var request = HttpRequest.newBuilder(URI.create(base + route.get("from").asText())).GET().build();
                var redirect = manual.send(request, HttpResponse.BodyHandlers.discarding());
                assertEquals(route.get("status").asInt(), redirect.statusCode());
                assertEquals(route.get("to").asText(), redirect.headers().firstValue("Location").orElseThrow());
                var audio = following.send(request, HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, audio.statusCode());
                assertEquals("audio/wav", audio.headers().firstValue("Content-Type").orElseThrow());
            }
        } finally {
            server.stop(0);
        }
        assertEquals(declared, LegacyRoutes.all());
    }
}
