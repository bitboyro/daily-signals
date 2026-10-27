package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeneratedAudioProvenanceTest {
    private static final String SHA = "496dba118d1a58f5f3db2efc88dbdc216e0483fc89fe6e47ee1f2c53f18ad1e4";

    @Test void serializesPinnedProviderModelAndVoice() {
        var provenance = new GeneratedAudioProvenance(1, "kokoro-local",
                "9e1746465742d780a70ac81d01a334c9f8e0bbb3", SHA, "af_heart", SHA, SHA);
        assertTrue(provenance.toJson().contains("\"voiceId\":\"af_heart\""));
    }

    @Test void rejectsMutableModelLabels() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratedAudioProvenance(
                1, "kokoro-local", "latest", SHA, "af_heart", SHA, SHA));
    }
}
