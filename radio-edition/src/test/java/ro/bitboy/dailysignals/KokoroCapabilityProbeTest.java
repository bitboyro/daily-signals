package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class KokoroCapabilityProbeTest {
    private static final String SHA = "496dba118d1a58f5f3db2efc88dbdc216e0483fc89fe6e47ee1f2c53f18ad1e4";

    @Test void readsPinnedRuntimeCapabilitiesWithoutSynthesis() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"modelRevision\":\"9e1746465742d780a70ac81d01a334c9f8e0bbb3\",\"voices\":[\"af_heart\"]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var config = new KokoroProviderConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "9e1746465742d780a70ac81d01a334c9f8e0bbb3", SHA, "af_heart");
            var capability = new KokoroCapabilityProbe(Duration.ofSeconds(1)).probe(config);
            assertEquals(config.modelRevision(), capability.modelRevision());
            assertEquals(config.voiceId(), capability.voices().getFirst());
        } finally { server.stop(0); }
    }
}
