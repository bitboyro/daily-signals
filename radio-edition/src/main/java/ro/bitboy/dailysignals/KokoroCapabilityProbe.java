package ro.bitboy.dailysignals;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Lightweight local-runtime probe; it never downloads a model or synthesizes audio. */
public final class KokoroCapabilityProbe {
    private static final Pattern MODEL = Pattern.compile("\\\"modelRevision\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern VOICES = Pattern.compile("\\\"voices\\\"\\s*:\\s*\\[([^]]*)]");
    private final HttpClient client;
    private final Duration timeout;

    public KokoroCapabilityProbe(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("positive probe timeout required");
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public Capability probe(KokoroProviderConfig config) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(config.endpoint().resolve("/health")).timeout(timeout).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Kokoro health failed: " + response.statusCode());
        var model = MODEL.matcher(response.body());
        var voices = VOICES.matcher(response.body());
        if (!model.find() || !voices.find()) throw new IOException("invalid Kokoro capability response");
        List<String> inventory = Arrays.stream(voices.group(1).split(","))
                .map(item -> item.trim().replace("\"", "")).filter(item -> !item.isBlank()).toList();
        return new Capability(model.group(1), inventory);
    }

    public record Capability(String modelRevision, List<String> voices) {
        public Capability { voices = List.copyOf(voices); }
    }
}
