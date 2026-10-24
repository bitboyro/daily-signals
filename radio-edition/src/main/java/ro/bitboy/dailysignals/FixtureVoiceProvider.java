package ro.bitboy.dailysignals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Offline provider: fixed audio bytes plus a digest of the requested script. */
public final class FixtureVoiceProvider implements VoiceProvider {
    @Override public RenderedAudio render(ScriptAssembler.Script script) throws IOException {
        if (script == null || script.text().isBlank()) throw new IllegalArgumentException("script required");
        try (var input = getClass().getResourceAsStream("/fixtures/fixture-voice.wav")) {
            if (input == null) throw new IOException("voice fixture unavailable");
            return new RenderedAudio(input.readAllBytes(), "audio/wav", sha256(script.text()));
        }
    }
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
