package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class KokoroCapabilityProbeTest {
    private static final String SHA = "8f4f1bc14a2a28cc04f27f9fcd17dc5d3a18955881b168b067e036b6ebc2e6f7";

    @Test void readsPinnedRuntimeCapabilitiesWithoutSynthesis() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"modelRevision\":\"kokoro-82m-v1.0-20250915\",\"voices\":[\"en_af_heart\"]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var config = new KokoroProviderConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "kokoro-82m-v1.0-20250915", SHA, "en_af_heart");
            var capability = new KokoroCapabilityProbe(Duration.ofSeconds(1)).probe(config);
            assertEquals(config.modelRevision(), capability.modelRevision());
            assertEquals(config.voiceId(), capability.voices().getFirst());
        } finally { server.stop(0); }
    }
}
