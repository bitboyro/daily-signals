package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PronunciationDictionaryTest {
    @Test void correctsWholeAcronymsWithoutCorruptingNames() {
        assertEquals("A I handles S three, but RAIDER stays RAIDER.",
                new PronunciationDictionary().speakable("AI handles S3, but RAIDER stays RAIDER."));
    }
    @Test void usesValidatedProducerOverrides() {
        var dictionary = new PronunciationDictionary(Map.of("SQL", "sequel"));
        assertEquals("A sequel query, not MYSQL.", dictionary.speakable("A SQL query, not MYSQL."));
        assertThrows(IllegalArgumentException.class,
                () -> new PronunciationDictionary(Map.of("", "empty term")));
    }
}
