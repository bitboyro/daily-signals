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
        var contract = mapper.readTree(Files.readString(Path.of("../contracts/edition-list-v2.json")));
        assertEquals(contract, mapper.readTree(EditionCatalog.fixture().toJson()));
    }

    @Test void openApiContractRequiresTheFieldsTheCatalogPublishes() throws Exception {
        var spec = mapper.readTree(Files.readString(Path.of("../contracts/openapi/editions-v1.json")));
        assertEquals("#/components/schemas/EditionList", spec.at("/paths/~1api~1editions/get/responses/200/content/application~1json/schema/$ref").asText());
        var required = spec.at("/components/schemas/EditionSummary/required");
        var edition = mapper.readTree(EditionCatalog.fixture().toJson()).get("editions").get(0);
        for (var field : required) assertTrue(edition.hasNonNull(field.asText()), field.asText());
    }

    @Test void newestEditionFirstAndTitlesEscaped() throws Exception {
        var catalog = new EditionCatalog(List.of(
                new EditionCatalog.Entry("older", LocalDate.of(2026, 1, 1), "Plain", "/editions/older/audio"),
                new EditionCatalog.Entry("newer", LocalDate.of(2026, 1, 2), "Quote \" and \\ slash", "/editions/newer/audio")));
        var json = mapper.readTree(catalog.toJson());
        assertEquals(EditionCatalog.SCHEMA_VERSION, json.get("schemaVersion").asInt());
        assertEquals("newer", json.get("editions").get(0).get("editionId").asText());
        assertEquals("Quote \" and \\ slash", json.get("editions").get(0).get("title").asText());
    }
}
