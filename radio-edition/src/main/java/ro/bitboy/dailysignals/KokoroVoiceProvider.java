package ro.bitboy.dailysignals;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/** Optional adapter for an operator-managed Kokoro HTTP runtime on localhost. */
public final class KokoroVoiceProvider implements VoiceProvider {
    private final KokoroProviderConfig config;
    private final HttpClient client;
    private final Duration timeout;

    public KokoroVoiceProvider(KokoroProviderConfig config, Duration timeout) {
        if (config == null || timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Kokoro configuration and timeout required");
        this.config = config;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override public RenderedAudio render(ScriptAssembler.Script script) throws IOException {
        if (script == null || script.text().isBlank()) throw new IllegalArgumentException("script required");
        KokoroVoiceInventory.requireSupported(config.voiceId());
        String json = "{\"model\":\"" + escape(config.modelRevision()) + "\",\"voice\":\""
                + escape(config.voiceId()) + "\",\"input\":\"" + escape(script.text()) + "\"}";
        var request = HttpRequest.newBuilder(config.endpoint().resolve("/v1/audio")).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200)
                throw new IOException("Kokoro synthesis failed: HTTP " + response.statusCode());
            if (response.body().length < 4) throw new IOException("Kokoro returned empty audio");
            return new RenderedAudio(response.body(), "audio/wav", sha256(script.text()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Kokoro synthesis interrupted", interrupted);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
