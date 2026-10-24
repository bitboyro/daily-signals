package ro.bitboy.dailysignals;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Parses the pilot's fixed, licensed XML snapshot format without network access. */
public final class XmlSourceSnapshotParser {
    public SourceSnapshotParser.Snapshot parse(InputStream input) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var root = factory.newDocumentBuilder().parse(input).getDocumentElement();
        if (!"snapshot".equals(root.getTagName()) || !"synthetic-test-content".equals(root.getAttribute("license"))) {
            throw new IllegalArgumentException("only licensed synthetic XML snapshots are accepted");
        }
        var source = (Element) root.getElementsByTagName("source").item(0);
        if (source == null) throw new IllegalArgumentException("missing source");
        List<SourceSnapshotParser.RawStory> stories = new ArrayList<>();
        var nodes = root.getElementsByTagName("story");
        for (int i = 0; i < nodes.getLength(); i++) {
            var story = (Element) nodes.item(i);
            stories.add(new SourceSnapshotParser.RawStory(required(story, "id"), text(story, "title"),
                    text(story, "summary"), Instant.parse(required(story, "publishedAt")), story.hasAttribute("category") ? story.getAttribute("category") : "general"));
        }
        return new SourceSnapshotParser.Snapshot(required(root, "snapshotId"),
                Instant.parse(required(root, "capturedAt")), required(source, "id"),
                required(source, "name"), required(source, "url"), List.copyOf(stories));
    }
    private static String text(Element parent, String tag) {
        var node = parent.getElementsByTagName(tag).item(0);
        if (node == null || node.getTextContent().isBlank()) throw new IllegalArgumentException("missing " + tag);
        return node.getTextContent().strip();
    }
    private static String required(Element element, String attribute) {
        var value = element.getAttribute(attribute);
        if (value.isBlank()) throw new IllegalArgumentException("missing " + attribute);
        return value;
    }
}
