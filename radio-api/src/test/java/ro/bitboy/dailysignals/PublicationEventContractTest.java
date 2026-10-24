package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PublicationEventContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void sharedEventEnvelopeRoundTrips() throws Exception {
        var json = Files.readString(Path.of("../contracts/publication-event-v1.json"));
        var event = mapper.readValue(json, PublicationEvent.class);
        assertEquals(PublicationEvent.PUBLISHED, event.type());
        assertEquals("fixtures/demo-edition.wav", event.manifest().audioPath());
        assertEquals(mapper.readTree(json), mapper.readTree(event.toJson()));
    }

    @Test void manifestJsonMatchesSharedContract() throws Exception {
        var json = Files.readString(Path.of("../contracts/publication-manifest-v2.json"));
        var manifest = mapper.readValue(json, PublicationManifest.class);
        assertEquals(mapper.readTree(json), mapper.readTree(manifest.toJson()));
    }

    @Test void rejectsEventsForAnotherEditionOrUnknownType() throws Exception {
        var manifest = mapper.readValue(Files.readString(Path.of("../contracts/publication-manifest-v2.json")), PublicationManifest.class);
        assertThrows(IllegalArgumentException.class,
                () -> new PublicationEvent(1, "x:1", PublicationEvent.PUBLISHED, "other", "2026-01-15T07:30:00Z", manifest));
        assertThrows(IllegalArgumentException.class,
                () -> new PublicationEvent(1, "x:1", "edition.deleted", manifest.editionId(), "2026-01-15T07:30:00Z", manifest));
        assertThrows(IllegalArgumentException.class,
                () -> new PublicationEvent(1, "x:1", PublicationEvent.PUBLISHED, manifest.editionId(), "yesterday", manifest));
    }
}
