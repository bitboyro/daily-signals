package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VoiceProviderFactoryTest {
    @Test void fixtureVoiceIsTheSafeDefault() {
        assertInstanceOf(FixtureVoiceProvider.class, VoiceProviderFactory.fromEnvironment(Map.of()));
    }

    @Test void completePinnedConfigurationSelectsKokoro() {
        var provider = VoiceProviderFactory.fromEnvironment(Map.of(
                "DAILY_SIGNALS_VOICE_PROVIDER", "kokoro",
                "DAILY_SIGNALS_KOKORO_ENDPOINT", "http://127.0.0.1:8880",
                "DAILY_SIGNALS_KOKORO_MODEL_REVISION", "kokoro-82m-v1.0",
                "DAILY_SIGNALS_KOKORO_MODEL_SHA256", "a".repeat(64),
                "DAILY_SIGNALS_KOKORO_VOICE_ID", "af_heart"));
        assertInstanceOf(KokoroVoiceProvider.class, provider);
    }

    @Test void incompleteOrUnknownConfigurationFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> VoiceProviderFactory.fromEnvironment(Map.of("DAILY_SIGNALS_VOICE_PROVIDER", "cloud")));
        assertThrows(IllegalArgumentException.class,
                () -> VoiceProviderFactory.fromEnvironment(Map.of("DAILY_SIGNALS_VOICE_PROVIDER", "kokoro")));
    }
}
