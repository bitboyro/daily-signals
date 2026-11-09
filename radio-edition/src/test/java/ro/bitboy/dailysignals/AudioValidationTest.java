package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AudioValidationTest {
    @Test void reportsTheFixtureVoicePlaybackProperties() throws Exception {
        var audio = new FixtureVoiceProvider().render(
                new ScriptAssembler.Script("Preview.", java.util.List.of("preview")));
        var validation = AudioValidation.inspect(audio.bytes());
        assertTrue(validation.playable());
        assertEquals(8_000, validation.sampleRate());
        assertEquals(1, validation.channels());
        assertEquals(16, validation.bitsPerSample());
        assertEquals(1_000, validation.durationMillis());
    }

    @Test void rejectsTruncatedOrNonWavBytes() {
        assertFalse(AudioValidation.inspect(new byte[] {'R', 'I', 'F', 'F'}).playable());
        assertFalse(AudioValidation.inspect("not audio".getBytes()).playable());
    }
}
