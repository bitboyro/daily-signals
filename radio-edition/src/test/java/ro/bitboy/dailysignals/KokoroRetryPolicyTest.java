package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KokoroRetryPolicyTest {
    private final ScriptAssembler.Script script = new ScriptAssembler.Script("Bulletin.\n", List.of("s1"));

    @Test void retriesTransientTimeoutThenReturnsAudio() throws Exception {
        var calls = new AtomicInteger();
        VoiceProvider delegate = ignored -> {
            if (calls.incrementAndGet() < 2) throw new IOException("timeout");
            return new VoiceProvider.RenderedAudio(new byte[] {'R', 'I', 'F', 'F'}, "audio/wav", "sha");
        };
        var provider = new KokoroRetryingVoiceProvider(delegate, 2, Duration.ZERO, ignored -> {});
        assertEquals(4, provider.render(script).bytes().length);
        assertEquals(2, calls.get());
    }

    @Test void exhaustedTimeoutFailsClosed() {
        VoiceProvider delegate = ignored -> { throw new IOException("timeout"); };
        var provider = new KokoroRetryingVoiceProvider(delegate, 2, Duration.ZERO, ignored -> {});
        assertThrows(IOException.class, () -> provider.render(script));
    }
}
