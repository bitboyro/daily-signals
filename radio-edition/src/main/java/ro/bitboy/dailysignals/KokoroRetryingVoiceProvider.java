package ro.bitboy.dailysignals;

import java.io.IOException;
import java.time.Duration;

/** Bounded retry wrapper for transient local Kokoro failures. */
public final class KokoroRetryingVoiceProvider implements VoiceProvider {
    @FunctionalInterface public interface Sleeper { void sleep(Duration delay) throws InterruptedException; }
    private final VoiceProvider delegate;
    private final int maximumAttempts;
    private final Duration delay;
    private final Sleeper sleeper;

    public KokoroRetryingVoiceProvider(VoiceProvider delegate, int maximumAttempts, Duration delay, Sleeper sleeper) {
        if (delegate == null || maximumAttempts < 1 || delay == null || delay.isNegative() || sleeper == null)
            throw new IllegalArgumentException("valid Kokoro retry policy required");
        this.delegate = delegate;
        this.maximumAttempts = maximumAttempts;
        this.delay = delay;
        this.sleeper = sleeper;
    }

    @Override public RenderedAudio render(ScriptAssembler.Script script) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            try {
                return delegate.render(script);
            } catch (IOException transientFailure) {
                last = transientFailure;
                if (attempt < maximumAttempts) {
                    try { sleeper.sleep(delay); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Kokoro retry interrupted", interrupted);
                    }
                }
            }
        }
        throw last;
    }
}
