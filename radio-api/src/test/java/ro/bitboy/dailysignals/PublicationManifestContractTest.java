package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PublicationManifestContractTest {
    @Test void javaReadsSharedV1Manifest() throws Exception {
        var path = Path.of("../contracts/publication-manifest-v1.json");
        var manifest = new ObjectMapper().readValue(Files.readString(path), PublicationManifest.class);
        assertEquals("PUBLISHED", manifest.status());
        assertEquals("fixtures/demo-edition.wav", manifest.audioPath());
    }
}
