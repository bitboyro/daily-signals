package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizedSourceParserTest {
    private SourceRegistry registry() {
        var registry = new SourceRegistry();
        registry.register(new SourceRegistry.Source("demo-civic-desk", "Demo Civic Desk", URI.create("https://example.invalid/daily-signals/demo-civic-desk"), 10));
        return registry;
    }

    @Test void producerAndEditorMayParseARegisteredSource() throws Exception {
        for (var role : new String[]{"PRODUCER", "EDITOR"}) {
            try (var input = getClass().getResourceAsStream("/fixtures/source-snapshot.json")) {
                assertEquals("synthetic-2026-01-15-morning", new AuthorizedSourceParser(registry()).parse(role, input).snapshotId());
            }
        }
    }

    @Test void listenerAndMissingRolesAreRejectedBeforeParsing() {
        for (var role : new String[]{"LISTENER", "", "ADMIN"}) {
            assertThrows(SecurityException.class, () -> new AuthorizedSourceParser(registry()).parse(role, InputStream.nullInputStream()));
        }
    }

    @Test void unregisteredSourceIdentityIsRejected() throws Exception {
        var empty = new SourceRegistry();
        try (var input = getClass().getResourceAsStream("/fixtures/source-snapshot.json")) {
            assertThrows(SecurityException.class, () -> new AuthorizedSourceParser(empty).parse("PRODUCER", input));
        }
    }
}
