package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PublicationManifestContractTest {
    @Test void javaReadsSharedV2ManifestWithAirDate() throws Exception {
        var path = Path.of("../contracts/publication-manifest-v2.json");
        var manifest = new ObjectMapper().readValue(Files.readString(path), PublicationManifest.class);
        assertEquals(2, manifest.schemaVersion());
        assertEquals("PUBLISHED", manifest.status());
        assertEquals("fixtures/demo-edition.wav", manifest.audioPath());
        assertEquals("2026-01-15", manifest.airDate());
    }
}
