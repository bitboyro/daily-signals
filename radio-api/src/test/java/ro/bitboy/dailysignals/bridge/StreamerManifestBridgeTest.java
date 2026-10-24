package ro.bitboy.dailysignals.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ro.bitboy.dailysignals.PublicationManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StreamerManifestBridgeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void bridgesV2ManifestToSharedV1Contract() throws Exception {
        var v2 = mapper.readValue(Files.readString(Path.of("../contracts/publication-manifest-v2.json")), PublicationManifest.class);
        var expected = mapper.readTree(Files.readString(Path.of("../contracts/publication-manifest-v1.json")));
        assertEquals(expected, mapper.readTree(StreamerManifestBridge.toV1Json(v2)));
        assertNull(StreamerManifestBridge.toV1(v2).airDate());
    }
}
