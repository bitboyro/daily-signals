package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class KokoroProviderConfigTest {
    private static final String SHA = "496dba118d1a58f5f3db2efc88dbdc216e0483fc89fe6e47ee1f2c53f18ad1e4";

    @Test void acceptsPinnedLocalRuntimeConfiguration() {
        var config = new KokoroProviderConfig(URI.create("http://127.0.0.1:8880"),
                "9e1746465742d780a70ac81d01a334c9f8e0bbb3", SHA, "af_heart");
        assertEquals("af_heart", config.voiceId());
    }

    @Test void rejectsRemoteEndpointsAndMutableModels() {
        assertThrows(IllegalArgumentException.class, () -> new KokoroProviderConfig(
                URI.create("https://tts.example.test"), "9e1746465742d780a70ac81d01a334c9f8e0bbb3", SHA, "af_heart"));
        assertThrows(IllegalArgumentException.class, () -> new KokoroProviderConfig(
                URI.create("http://localhost:8880"), "latest", SHA, "af_heart"));
    }
}
