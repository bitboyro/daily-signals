package ro.bitboy.dailysignals;

import java.util.Map;
import java.util.Optional;

/** Producer identity boundary; the optional OIDC implementation is isolated from dependency-free local startup. */
public interface ProducerAuthenticator {
    String ISSUER_ENV = "JWT_ISSUER_URI";

    record Identity(String subject, String role, Mechanism mechanism) {
        public enum Mechanism { OIDC, LOCAL_TOKEN }

        public Identity {
            if (subject == null || subject.isBlank() || role == null || role.isBlank() || mechanism == null) {
                throw new IllegalArgumentException("subject, role and mechanism are required");
            }
        }
    }

    Optional<Identity> authenticate(String authorization);

    static ProducerAuthenticator local(ReviewerTokens fallback) {
        return authorization -> fallback.roleFor(authorization)
                .map(role -> new Identity(role, role, Identity.Mechanism.LOCAL_TOKEN));
    }

    static ProducerAuthenticator fromEnvironment(Map<String, String> environment, ReviewerTokens fallback) {
        String issuer = environment.get(ISSUER_ENV);
        if (issuer == null || issuer.isBlank()) return local(fallback);
        return OidcProducerAuthenticator.discover(issuer.strip(), fallback);
    }
}
