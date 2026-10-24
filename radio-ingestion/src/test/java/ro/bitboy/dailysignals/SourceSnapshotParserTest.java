package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SourceSnapshotParserTest {
    @Test void parsesPinnedSnapshot() throws Exception {
        try (var input=getClass().getResourceAsStream("/fixtures/source-snapshot.json")) {
            var snapshot=new SourceSnapshotParser().parse(input);
            assertEquals("synthetic-2026-01-15-morning",snapshot.snapshotId());
            assertEquals("demo-civic-desk",snapshot.sourceId());
            assertEquals(2,snapshot.stories().size());
            assertEquals(Instant.parse("2026-01-15T06:30:00Z"),snapshot.stories().get(0).publishedAt());
        }
    }
}
