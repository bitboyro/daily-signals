package ro.bitboy.dailysignals;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Spring Security JWT decoder adapter, loaded only when an OIDC issuer is configured. */
final class OidcProducerAuthenticator implements ProducerAuthenticator {
    private final ReviewerTokens fallback;
    private final String issuer;
    private final Supplier<JwtDecoder> decoders;

    private OidcProducerAuthenticator(ReviewerTokens fallback, String issuer, Supplier<JwtDecoder> decoders) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.issuer = validateIssuer(issuer);
        this.decoders = Objects.requireNonNull(decoders, "decoders");
    }

    static ProducerAuthenticator discover(String issuer, ReviewerTokens fallback) {
        String validated = validateIssuer(issuer);
        return create(validated, fallback, memoized(() -> JwtDecoders.fromIssuerLocation(validated)));
    }

    static ProducerAuthenticator create(String issuer, ReviewerTokens fallback, Supplier<JwtDecoder> decoders) {
        return new OidcProducerAuthenticator(fallback, issuer, decoders);
    }

    @Override
    public Optional<Identity> authenticate(String authorization) {
        Optional<String> localRole = fallback.roleFor(authorization);
        if (localRole.isPresent()) {
            return Optional.of(new Identity(localRole.get(), localRole.get(), Identity.Mechanism.LOCAL_TOKEN));
        }
        String token = bearer(authorization);
        if (token == null) return Optional.empty();
        try {
            Jwt jwt = decoders.get().decode(token);
            if (!issuer.equals(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())) return Optional.empty();
            return realmRole(jwt).map(role -> new Identity(jwt.getSubject(), role, Identity.Mechanism.OIDC));
        } catch (JwtException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static Optional<String> realmRole(Jwt jwt) {
        Object access = jwt.getClaims().get("realm_access");
        if (!(access instanceof Map<?, ?> map) || !(map.get("roles") instanceof Collection<?> roles)) {
            return Optional.empty();
        }
        for (String accepted : new String[]{"PRODUCER", "EDITOR", "LISTENER"}) {
            if (roles.stream().anyMatch(accepted::equals)) return Optional.of(accepted);
        }
        return Optional.empty();
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring("Bearer ".length()).strip();
        return token.isEmpty() ? null : token;
    }

    private static String validateIssuer(String value) {
        URI issuer;
        try {
            issuer = URI.create(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("JWT_ISSUER_URI must be an absolute HTTPS or loopback HTTP URI", invalid);
        }
        boolean loopback = "http".equals(issuer.getScheme())
                && ("localhost".equals(issuer.getHost()) || "127.0.0.1".equals(issuer.getHost()));
        if (!issuer.isAbsolute() || issuer.getHost() == null || !("https".equals(issuer.getScheme()) || loopback)
                || issuer.getQuery() != null || issuer.getFragment() != null) {
            throw new IllegalArgumentException("JWT_ISSUER_URI must be an absolute HTTPS or loopback HTTP URI");
        }
        return issuer.toString().replaceFirst("/$", "");
    }

    private static <T> Supplier<T> memoized(Supplier<T> source) {
        return new Supplier<>() {
            private volatile T value;
            @Override public T get() {
                T current = value;
                if (current == null) synchronized (this) {
                    if (value == null) value = Objects.requireNonNull(source.get());
                    current = value;
                }
                return current;
            }
        };
    }
}
