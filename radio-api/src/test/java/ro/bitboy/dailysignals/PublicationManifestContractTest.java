package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PublicationManifestContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void javaReadsSharedV1ManifestWithoutAirDate() throws Exception {
        var manifest = readVersion(1);
        assertEquals(1, manifest.schemaVersion());
        assertEquals("PUBLISHED", manifest.status());
        assertEquals("fixtures/demo-edition.wav", manifest.audioPath());
        assertNull(manifest.airDate());
    }

    @Test void javaReadsSharedV2ManifestWithAirDate() throws Exception {
        var manifest = readVersion(2);
        assertEquals(2, manifest.schemaVersion());
        assertEquals("PUBLISHED", manifest.status());
        assertEquals("fixtures/demo-edition.wav", manifest.audioPath());
        assertEquals("2026-01-15", manifest.airDate());
    }

    @Test void adjacentManifestVersionsRetainCommonAudioMetadata() throws Exception {
        var v1 = readVersion(1);
        var v2 = readVersion(2);
        assertAll(
                () -> assertEquals(v1.editionId(), v2.editionId()),
                () -> assertEquals(v1.status(), v2.status()),
                () -> assertEquals(v1.audioPath(), v2.audioPath()),
                () -> assertEquals(v1.contentType(), v2.contentType()),
                () -> assertEquals(v1.sizeBytes(), v2.sizeBytes()),
                () -> assertEquals(v1.checksumSha256(), v2.checksumSha256())
        );
    }

    @Test void upgradesV1ToTheCanonicalV2Fixture() throws Exception {
        var upgraded = readVersion(1).upgradeToV2("2026-01-15");
        var expected = mapper.readTree(Files.readString(manifestPath(2)));
        assertEquals(expected, mapper.readTree(upgraded.toJson()));
        assertThrows(IllegalStateException.class, () -> upgraded.upgradeToV2("2026-01-16"));
    }

    private PublicationManifest readVersion(int version) throws Exception {
        return mapper.readValue(Files.readString(manifestPath(version)), PublicationManifest.class);
    }

    private Path manifestPath(int version) {
        return Path.of("../contracts/publication-manifest-v" + version + ".json");
    }
}
