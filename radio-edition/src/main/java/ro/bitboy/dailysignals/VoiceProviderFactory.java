package ro.bitboy.dailysignals;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Selects the offline fixture by default and Kokoro only from complete, pinned operator configuration. */
public final class VoiceProviderFactory {
    private VoiceProviderFactory() {}

    public static VoiceProvider fromEnvironment(Map<String, String> environment) {
        String provider = environment.getOrDefault("DAILY_SIGNALS_VOICE_PROVIDER", "fixture").strip();
        if ("fixture".equals(provider)) return new FixtureVoiceProvider();
        if (!"kokoro".equals(provider)) throw new IllegalArgumentException("unsupported voice provider");
        var config = new KokoroProviderConfig(
                URI.create(required(environment, "DAILY_SIGNALS_KOKORO_ENDPOINT")),
                required(environment, "DAILY_SIGNALS_KOKORO_MODEL_REVISION"),
                required(environment, "DAILY_SIGNALS_KOKORO_MODEL_SHA256"),
                required(environment, "DAILY_SIGNALS_KOKORO_VOICE_ID"));
        long timeout = Long.parseLong(environment.getOrDefault("DAILY_SIGNALS_KOKORO_TIMEOUT_SECONDS", "30"));
        return new KokoroVoiceProvider(config, Duration.ofSeconds(timeout));
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
