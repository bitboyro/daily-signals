package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KokoroVoiceSelectionTest {
    private static final String SHA = "496dba118d1a58f5f3db2efc88dbdc216e0483fc89fe6e47ee1f2c53f18ad1e4";

    @Test void rejectsUnsupportedVoiceBeforeDispatch() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/audio", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(422, -1);
            exchange.close();
        });
        server.start();
        try {
            var config = new KokoroProviderConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "kokoro-82m-v1.0-20250915", SHA, "en_zz_unknown");
            var provider = new KokoroVoiceProvider(config, Duration.ofSeconds(1));
            assertThrows(IllegalArgumentException.class, () -> provider.render(
                    new ScriptAssembler.Script("Bulletin.\n", List.of("s1"))));
            assertEquals(0, calls.get());
        } finally { server.stop(0); }
    }
}
