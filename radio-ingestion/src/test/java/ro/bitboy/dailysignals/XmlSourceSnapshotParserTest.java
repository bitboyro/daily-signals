package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class XmlSourceSnapshotParserTest {
    @Test void parsesPinnedXmlSnapshot() throws Exception {
        try (var input = getClass().getResourceAsStream("/fixtures/source-snapshot.xml")) {
            var snapshot = new XmlSourceSnapshotParser().parse(input);
            assertEquals("synthetic-xml-2026-01-15", snapshot.snapshotId());
            assertEquals("demo-town-desk", snapshot.sourceId());
            assertEquals("xml-1", snapshot.stories().get(0).id());
        }
    }
    @Test void rejectsDoctypeBeforeExpansion() {
        var xml = "<!DOCTYPE snapshot [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><snapshot license='synthetic-test-content'/>";
        assertThrows(Exception.class, () -> new XmlSourceSnapshotParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }
}
