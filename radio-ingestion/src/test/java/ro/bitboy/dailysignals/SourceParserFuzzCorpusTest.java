package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SourceParserFuzzCorpusTest {
    @Test void malformedCorpusIsRejectedWithoutHangingOrReturningPartialSnapshots() throws Exception {
        var corpus = Path.of("src/test/resources/fuzz/source-parser");
        try (var paths = Files.list(corpus)) {
            var fixtures = paths.filter(path -> path.getFileName().toString().startsWith("invalid-")).sorted().toList();
            assertEquals(4, fixtures.size());
            for (var fixture : fixtures) {
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
                    try (var input = Files.newInputStream(fixture)) {
                        assertThrows(Exception.class, () -> new SourceSnapshotParser().parse(input), fixture.toString());
                    }
                });
            }
        }
    }

    @Test void validUnicodeFixtureRemainsLossless() throws Exception {
        try (var input = Files.newInputStream(Path.of("src/test/resources/fuzz/source-parser/valid-unicode.json"))) {
            var snapshot = new SourceSnapshotParser().parse(input);
            assertEquals("Radio România", snapshot.sourceName());
            assertEquals("Știință și cultură", snapshot.stories().get(0).title());
            assertEquals("cultură", snapshot.stories().get(0).category());
        }
    }
}
