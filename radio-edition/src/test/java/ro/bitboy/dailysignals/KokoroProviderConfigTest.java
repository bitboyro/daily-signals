package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class KokoroProviderConfigTest {
    private static final String SHA = "8f4f1bc14a2a28cc04f27f9fcd17dc5d3a18955881b168b067e036b6ebc2e6f7";

    @Test void acceptsPinnedLocalRuntimeConfiguration() {
        var config = new KokoroProviderConfig(URI.create("http://127.0.0.1:8880"),
                "kokoro-82m-v1.0-20250915", SHA, "en_af_heart");
        assertEquals("en_af_heart", config.voiceId());
    }

    @Test void rejectsRemoteEndpointsAndMutableModels() {
        assertThrows(IllegalArgumentException.class, () -> new KokoroProviderConfig(
                URI.create("https://tts.example.test"), "kokoro-82m-v1.0-20250915", SHA, "en_af_heart"));
        assertThrows(IllegalArgumentException.class, () -> new KokoroProviderConfig(
                URI.create("http://localhost:8880"), "latest", SHA, "en_af_heart"));
    }
}
