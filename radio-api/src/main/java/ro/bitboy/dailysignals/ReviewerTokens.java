package ro.bitboy.dailysignals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side reviewer credentials from {@code DAILY_SIGNALS_REVIEW_TOKENS} ("token=ROLE,token=ROLE").
 * With no configuration every review write is refused; the client never chooses its own role.
 */
public final class ReviewerTokens {
    private static final int MINIMUM_TOKEN_LENGTH = 24;
    private final Map<String, String> roles;

    private ReviewerTokens(Map<String, String> roles) { this.roles = Map.copyOf(roles); }

    public static ReviewerTokens none() { return new ReviewerTokens(Map.of()); }

    public static ReviewerTokens parse(String configuration) {
        if (configuration == null || configuration.isBlank()) return none();
        var roles = new LinkedHashMap<String, String>();
        for (String entry : configuration.split(",")) {
            String[] parts = entry.strip().split("=", 2);
            if (parts.length != 2 || parts[0].length() < MINIMUM_TOKEN_LENGTH || !parts[0].matches("[A-Za-z0-9._~-]+")
                    || !parts[1].matches("PRODUCER|EDITOR|LISTENER") || roles.putIfAbsent(parts[0], parts[1]) != null)
                throw new IllegalArgumentException("reviewer tokens must be unique token=ROLE pairs of at least "
                        + MINIMUM_TOKEN_LENGTH + " URL-safe characters");
        }
        return new ReviewerTokens(roles);
    }

    /** Role for an {@code Authorization: Bearer} header value, compared in constant time per configured token. */
    public Optional<String> roleFor(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return Optional.empty();
        byte[] presented = authorization.substring("Bearer ".length()).strip().getBytes(StandardCharsets.UTF_8);
        String match = null;
        for (var entry : roles.entrySet()) {
            if (MessageDigest.isEqual(presented, entry.getKey().getBytes(StandardCharsets.UTF_8))) match = entry.getValue();
        }
        return Optional.ofNullable(match);
    }
}
