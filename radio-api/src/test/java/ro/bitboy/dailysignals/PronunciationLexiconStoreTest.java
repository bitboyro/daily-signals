package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PronunciationLexiconStoreTest {
    @Test void savesValidatedEntriesAndBuildsTheRuntimeDictionary() {
        var store = new PronunciationLexiconStore();
        assertTrue(store.saveJson("SQL=sequel\nS3=S three").contains("\"revision\":1"));
        assertEquals("sequel meets S three", store.dictionary().speakable("SQL meets S3"));
    }
    @Test void rejectsMalformedAndDuplicateEntries() {
        var store = new PronunciationLexiconStore();
        assertThrows(IllegalArgumentException.class, () -> store.saveJson("missing separator"));
        assertThrows(IllegalArgumentException.class, () -> store.saveJson("AI=A I\nAI=artificial intelligence"));
    }
}
