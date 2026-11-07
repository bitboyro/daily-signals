package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FetchScheduleContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void servesTheConfiguredNextRunAndMatchesTheOpenApiShape() throws Exception {
        var view = new FetchScheduleView(ZoneId.of("Europe/Bucharest"), LocalTime.of(6, 15),
                List.of("public-desk", "world-wire"), () -> Optional.of(Instant.parse("2026-01-16T04:15:00Z")));
        var expected = mapper.readTree("""
                {"schemaVersion":1,"nextRun":"2026-01-16T04:15:00Z","zone":"Europe/Bucharest",
                 "window":"06:15","sources":["public-desk","world-wire"]}
                """);
        assertEquals(expected, mapper.readTree(view.toJson()));

        var contract = mapper.readTree(Files.readString(Path.of("../contracts/openapi/schedule-v1.json")));
        assertEquals("#/components/schemas/FetchSchedule",
                contract.at("/paths/~1api~1schedule/get/responses/200/content/application~1json/schema/$ref").asText());
        for (var field : contract.at("/components/schemas/FetchSchedule/required")) {
            assertEquals(true, expected.hasNonNull(field.asText()), field.asText());
        }

        var server = DailySignals.createServer("127.0.0.1", 0, null, ReviewerTokens.none(), view);
        server.start();
        try {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/schedule")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(expected, mapper.readTree(response.body()));
        } finally {
            server.stop(0);
        }
    }
}
