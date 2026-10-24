package ro.bitboy.dailysignals.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ro.bitboy.dailysignals.persistence.EditionRepository.Status.*;

class EditionRepositoryRulesTest {
    @Test void publicationRequiresReadyState() {
        assertTrue(EditionRepository.allowed(DRAFT, READY));
        assertTrue(EditionRepository.allowed(READY, PUBLISHED));
        assertFalse(EditionRepository.allowed(DRAFT, PUBLISHED));
        assertFalse(EditionRepository.allowed(PUBLISHED, DRAFT));
    }
}
