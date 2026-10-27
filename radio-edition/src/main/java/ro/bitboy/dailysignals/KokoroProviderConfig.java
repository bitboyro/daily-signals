package ro.bitboy.dailysignals;

import java.net.URI;

/** Immutable configuration for an operator-approved local Kokoro runtime. */
public record KokoroProviderConfig(URI endpoint, String modelRevision, String modelSha256, String voiceId) {
    public KokoroProviderConfig {
        if (endpoint == null || !"http".equals(endpoint.getScheme()) || endpoint.getHost() == null
                || !(endpoint.getHost().equals("127.0.0.1") || endpoint.getHost().equals("localhost")))
            throw new IllegalArgumentException("local HTTP Kokoro endpoint required");
        if (modelRevision == null || !modelRevision.matches("[a-zA-Z0-9._-]{6,80}")
                || "latest".equalsIgnoreCase(modelRevision))
            throw new IllegalArgumentException("immutable model revision required");
        if (modelSha256 == null || !modelSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("model SHA-256 required");
        if (voiceId == null || !voiceId.matches("[a-z]{2}_[a-z0-9_]{2,30}"))
            throw new IllegalArgumentException("valid voice id required");
    }
}
