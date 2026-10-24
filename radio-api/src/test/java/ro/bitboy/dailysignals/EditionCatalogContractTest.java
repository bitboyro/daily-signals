package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditionCatalogContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void fixtureCatalogMatchesSharedListenerContract() throws Exception {
        var contract = mapper.readTree(Files.readString(Path.of("../contracts/edition-list-v1.json")));
        assertEquals(contract, mapper.readTree(EditionCatalog.fixture().toJson()));
    }

    @Test void newestEditionFirstAndTitlesEscaped() throws Exception {
        var catalog = new EditionCatalog(List.of(
                new EditionCatalog.Entry("older", LocalDate.of(2026, 1, 1), "Plain", "/editions/older/audio"),
                new EditionCatalog.Entry("newer", LocalDate.of(2026, 1, 2), "Quote \" and \\ slash", "/editions/newer/audio")));
        var json = mapper.readTree(catalog.toJson());
        assertEquals("newer", json.get(0).get("editionId").asText());
        assertEquals("Quote \" and \\ slash", json.get(0).get("title").asText());
    }
}
