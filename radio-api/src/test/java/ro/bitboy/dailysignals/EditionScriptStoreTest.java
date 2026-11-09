package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditionScriptStoreTest {
    @Test void savesBoundedScriptsAndAdvancesTheRevision() {
        var store = new EditionScriptStore("Initial draft");
        assertEquals("{\"schemaVersion\":1,\"text\":\"Initial draft\",\"revision\":0}", store.toJson());
        assertEquals("{\"schemaVersion\":1,\"text\":\"Line one.\\nLine \\\"two\\\".\",\"revision\":1}",
                store.saveJson("  Line one.\nLine \"two\".  "));
        assertThrows(IllegalArgumentException.class, () -> store.saveJson(" "));
        assertThrows(IllegalArgumentException.class, () -> store.saveJson("x".repeat(20_001)));
        assertThrows(IllegalArgumentException.class, () -> store.saveJson("bad\u0000script"));
    }
}
