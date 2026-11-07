package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProducerAuthenticatorTest {
    private static final String LOCAL = "editor-token-0123456789abcdef";
    private static final String ISSUER = "https://identity.example/realms/bitboy-internal";

    @Test void acceptsValidatedKeycloakRealmRoles() {
        var jwt = Jwt.withTokenValue("signed-token").header("alg", "RS256")
                .issuer(ISSUER).subject("producer-42").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("realm_access", Map.of("roles", List.of("offline_access", "EDITOR"))).build();
        var authenticator = OidcProducerAuthenticator.create(ISSUER, ReviewerTokens.none(), () -> token -> jwt);

        var identity = authenticator.authenticate("Bearer signed-token").orElseThrow();

        assertEquals("producer-42", identity.subject());
        assertEquals("EDITOR", identity.role());
        assertEquals(ProducerAuthenticator.Identity.Mechanism.OIDC, identity.mechanism());
    }

    @Test void selectsTheMostCapableKnownRealmRole() {
        var jwt = Jwt.withTokenValue("signed-token").header("alg", "RS256")
                .issuer(ISSUER).subject("producer-42").issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("realm_access", Map.of("roles", List.of("LISTENER", "EDITOR", "PRODUCER"))).build();
        var identity = OidcProducerAuthenticator.create(ISSUER, ReviewerTokens.none(), () -> token -> jwt)
                .authenticate("Bearer signed-token").orElseThrow();
        assertEquals("PRODUCER", identity.role());
    }

    @Test void rejectsWrongIssuerMissingRoleAndDecoderFailures() {
        var wrongIssuer = jwt("https://identity.example/realms/elsewhere", Map.of("roles", List.of("EDITOR")));
        var noRole = jwt(ISSUER, Map.of("roles", List.of("offline_access")));
        assertTrue(OidcProducerAuthenticator.create(ISSUER, ReviewerTokens.none(), () -> token -> wrongIssuer)
                .authenticate("Bearer token").isEmpty());
        assertTrue(OidcProducerAuthenticator.create(ISSUER, ReviewerTokens.none(), () -> token -> noRole)
                .authenticate("Bearer token").isEmpty());
        assertTrue(OidcProducerAuthenticator.create(ISSUER, ReviewerTokens.none(),
                () -> token -> { throw new org.springframework.security.oauth2.jwt.JwtException("bad signature"); })
                .authenticate("Bearer token").isEmpty());
    }

    @Test void retainsConfiguredLocalTokensAsFallback() {
        var fallback = ReviewerTokens.parse(LOCAL + "=EDITOR");
        var authenticator = OidcProducerAuthenticator.create(ISSUER, fallback,
                () -> token -> { throw new AssertionError("local token must not reach the OIDC decoder"); });

        var identity = authenticator.authenticate("Bearer " + LOCAL).orElseThrow();

        assertEquals("EDITOR", identity.subject());
        assertEquals(ProducerAuthenticator.Identity.Mechanism.LOCAL_TOKEN, identity.mechanism());
    }

    @Test void validatesIssuerConfiguration() {
        assertTrue(ProducerAuthenticator.fromEnvironment(Map.of(), ReviewerTokens.none())
                .authenticate("Bearer token").isEmpty());
        assertDoesNotThrow(() -> ProducerAuthenticator.fromEnvironment(
                Map.of(ProducerAuthenticator.ISSUER_ENV, "http://127.0.0.1:8180/realms/bitboy-internal"), ReviewerTokens.none()));
        assertThrows(IllegalArgumentException.class, () -> ProducerAuthenticator.fromEnvironment(
                Map.of(ProducerAuthenticator.ISSUER_ENV, "http://identity.example/realm"), ReviewerTokens.none()));
        assertThrows(IllegalArgumentException.class, () -> ProducerAuthenticator.fromEnvironment(
                Map.of(ProducerAuthenticator.ISSUER_ENV, "relative/realm"), ReviewerTokens.none()));
    }

    private static Jwt jwt(String issuer, Map<String, List<String>> realmAccess) {
        return Jwt.withTokenValue("token").header("alg", "RS256").issuer(issuer).subject("subject")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("realm_access", realmAccess).build();
    }
}
