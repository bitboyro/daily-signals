package ro.bitboy.dailysignals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Selects the voice for each render from the durable first-run setup. */
final class SetupVoiceProvider implements VoiceProvider {
    private final FirstRunSetup setup;
    private final VoiceProvider fixture;
    private final Supplier<VoiceProvider> kokoro;

    SetupVoiceProvider(FirstRunSetup setup, VoiceProvider fixture, VoiceProvider kokoro) {
        this(setup, fixture, () -> kokoro);
    }

    private SetupVoiceProvider(FirstRunSetup setup, VoiceProvider fixture, Supplier<VoiceProvider> kokoro) {
        this.setup = java.util.Objects.requireNonNull(setup, "setup");
        this.fixture = java.util.Objects.requireNonNull(fixture, "fixture");
        this.kokoro = java.util.Objects.requireNonNull(kokoro, "kokoro");
    }

    static SetupVoiceProvider fromEnvironment(FirstRunSetup setup, Map<String, String> environment) {
        var kokoroEnvironment = new HashMap<>(environment);
        kokoroEnvironment.put("DAILY_SIGNALS_VOICE_PROVIDER", "kokoro");
        return new SetupVoiceProvider(setup, new FixtureVoiceProvider(),
                () -> VoiceProviderFactory.fromEnvironment(kokoroEnvironment));
    }

    @Override
    public RenderedAudio render(ScriptAssembler.Script script) throws IOException {
        return ("kokoro".equals(setup.voice()) ? kokoro.get() : fixture).render(script);
    }
}
