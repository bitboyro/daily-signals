package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class KokoroVoiceProviderTest {
    private static final String SHA = "496dba118d1a58f5f3db2efc88dbdc216e0483fc89fe6e47ee1f2c53f18ad1e4";

    @Test void postsSpeechRequestAndReturnsWav() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/audio/speech", exchange -> {
            if (!"/v1/audio/speech".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            assertEquals("POST", exchange.getRequestMethod());
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(request.contains("af_heart"));
            byte[] wav;
            try (var fixture = getClass().getResourceAsStream("/fixtures/kokoro-response.wav")) {
                wav = fixture.readAllBytes();
            }
            exchange.getResponseHeaders().add("Content-Type", "audio/wav");
            exchange.sendResponseHeaders(200, wav.length);
            exchange.getResponseBody().write(wav);
            exchange.close();
        });
        server.start();
        try {
            var config = new KokoroProviderConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "9e1746465742d780a70ac81d01a334c9f8e0bbb3", SHA, "af_heart");
            var audio = new KokoroVoiceProvider(config, Duration.ofSeconds(1))
                    .render(new ScriptAssembler.Script("Local bulletin.\n", List.of("s1")));
            assertEquals("RIFF", new String(audio.bytes(), 0, 4, StandardCharsets.US_ASCII));
        } finally { server.stop(0); }
    }
}
