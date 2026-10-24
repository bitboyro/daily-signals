package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class SourceRegistryTest {
    @Test void registersStableSourceId() {
        var registry = new SourceRegistry();
        var source = new SourceRegistry.Source("civic-desk", "Civic Desk", URI.create("https://example.invalid/feed"), 4);
        registry.register(source);
        assertEquals(source, registry.get("civic-desk"));
        assertEquals(1, registry.all().size());
        assertThrows(IllegalArgumentException.class, () -> registry.register(source));
    }
    @Test void refusesInsecureSource() {
        assertThrows(IllegalArgumentException.class, () -> new SourceRegistry.Source("bad", "Bad", URI.create("http://example.invalid/feed"), 1));
    }
}
