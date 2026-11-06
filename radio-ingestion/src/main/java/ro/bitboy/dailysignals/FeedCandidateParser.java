package ro.bitboy.dailysignals;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Converts RSS 2.0 and Atom feed entries into the ingestion model. */
public final class FeedCandidateParser {
    public List<StoryNormalizer.Story> parse(HttpFeedFetcher.FeedDocument document, Instant capturedAt) throws Exception {
        if (document == null || capturedAt == null) throw new IllegalArgumentException("document and capture time are required");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(document.body())).getDocumentElement();
        List<SourceSnapshotParser.RawStory> raw;
        if ("rss".equals(root.getLocalName()) || "rss".equals(root.getTagName())) {
            raw = rss(root);
        } else if ("feed".equals(root.getLocalName())) {
            raw = atom(root);
        } else {
            throw new IllegalArgumentException("expected RSS 2.0 or Atom feed");
        }
        var source = document.source();
        var snapshot = new SourceSnapshotParser.Snapshot(source.id() + "-" + capturedAt.toEpochMilli(), capturedAt,
                source.id(), source.name(), source.url().toString(), raw);
        return new StoryNormalizer().normalize(snapshot, source);
    }

    private static List<SourceSnapshotParser.RawStory> rss(Element root) {
        var nodes = root.getElementsByTagName("item");
        var stories = new ArrayList<SourceSnapshotParser.RawStory>();
        for (int index = 0; index < nodes.getLength(); index++) {
            var item = (Element) nodes.item(index);
            stories.add(new SourceSnapshotParser.RawStory(text(item, "guid"), text(item, "title"),
                    text(item, "description"), ZonedDateTime.parse(text(item, "pubDate"),
                    DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(), "general"));
        }
        return List.copyOf(stories);
    }

    private static List<SourceSnapshotParser.RawStory> atom(Element root) {
        var nodes = root.getElementsByTagNameNS("*", "entry");
        var stories = new ArrayList<SourceSnapshotParser.RawStory>();
        for (int index = 0; index < nodes.getLength(); index++) {
            var entry = (Element) nodes.item(index);
            stories.add(new SourceSnapshotParser.RawStory(textNs(entry, "id"), textNs(entry, "title"),
                    textNs(entry, "summary"), Instant.parse(textNs(entry, "updated")), "general"));
        }
        return List.copyOf(stories);
    }

    private static String text(Element parent, String tag) {
        var node = parent.getElementsByTagName(tag).item(0);
        return required(node == null ? null : node.getTextContent(), tag);
    }

    private static String textNs(Element parent, String tag) {
        var node = parent.getElementsByTagNameNS("*", tag).item(0);
        return required(node == null ? null : node.getTextContent(), tag);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + field);
        return value.strip();
    }
}
