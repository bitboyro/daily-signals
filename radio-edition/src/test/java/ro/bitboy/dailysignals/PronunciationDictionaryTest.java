package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PronunciationDictionaryTest {
    @Test void correctsWholeAcronymsWithoutCorruptingNames() {
        assertEquals("A I handles S three, but RAIDER stays RAIDER.",
                new PronunciationDictionary().speakable("AI handles S3, but RAIDER stays RAIDER."));
    }
}
