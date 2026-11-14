package ro.bitboy.dailysignals;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PodcastFeedContractTest {
    @Test
    void generatedEditionsKeepStableGuidsDatesAndEnclosures() throws Exception {
        var entries = new ArrayList<EditionCatalog.Entry>();
        for (int index = 0; index < 40; index++) {
            entries.add(new EditionCatalog.Entry("generated-" + index, LocalDate.of(2026, 1, 1).plusDays(index),
                    "Edition " + index + " & community", "/editions/generated-" + index + "/audio",
                    index % 2 == 0 ? "audio/wav" : "audio/mpeg", 1_000L + index));
        }

        Document feed = parse(PodcastFeed.from(new EditionCatalog(entries)));
        var observed = new HashMap<String, org.w3c.dom.Element>();
        var items = feed.getElementsByTagName("item");
        for (int index = 0; index < items.getLength(); index++) {
            var item = (org.w3c.dom.Element) items.item(index);
            observed.put(item.getElementsByTagName("guid").item(0).getTextContent(), item);
        }
        assertEquals(entries.size(), observed.size());

        var expectedGuids = new HashSet<String>();
        for (var entry : entries) {
            String guid = "urn:daily-signals:edition:" + entry.editionId();
            expectedGuids.add(guid);
            var item = observed.get(guid);
            assertEquals(DateTimeFormatter.RFC_1123_DATE_TIME.format(
                    entry.airDate().atStartOfDay(ZoneOffset.UTC)),
                    item.getElementsByTagName("pubDate").item(0).getTextContent());
            var enclosure = ((org.w3c.dom.Element) item.getElementsByTagName("enclosure").item(0));
            assertEquals("https://stream.daily-signals.example" + entry.audioPath(), enclosure.getAttribute("url"));
            assertEquals(Long.toString(entry.sizeBytes()), enclosure.getAttribute("length"));
            assertEquals(entry.contentType(), enclosure.getAttribute("type"));
        }

        var retitled = entries.stream().map(entry -> new EditionCatalog.Entry(entry.editionId(), entry.airDate(),
                "Corrected " + entry.title(), entry.audioPath(), entry.contentType(), entry.sizeBytes())).toList();
        Document revised = parse(PodcastFeed.from(new EditionCatalog(retitled)));
        var revisedGuids = new HashSet<String>();
        var revisedItems = revised.getElementsByTagName("guid");
        for (int index = 0; index < revisedItems.getLength(); index++) {
            revisedGuids.add(revisedItems.item(index).getTextContent());
        }
        assertEquals(expectedGuids, revisedGuids, "editorial corrections must not change episode identity");
    }

    @Test
    void settingIsOptInAndRejectsAmbiguousValues() {
        assertEquals(new PodcastFeedSettings(false), PodcastFeedSettings.fromEnvironment(Map.of()));
        assertEquals(new PodcastFeedSettings(true), PodcastFeedSettings.fromEnvironment(
                Map.of(PodcastFeedSettings.ENVIRONMENT_KEY, "true")));
        assertEquals(new PodcastFeedSettings(false), PodcastFeedSettings.fromEnvironment(
                Map.of(PodcastFeedSettings.ENVIRONMENT_KEY, "FALSE")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PodcastFeedSettings.fromEnvironment(Map.of(PodcastFeedSettings.ENVIRONMENT_KEY, "yes")));
    }

    @Test
    void disabledFeedIsNotPublished() throws Exception {
        HttpServer server = DailySignals.createServer("127.0.0.1", 0, null,
                ProducerAuthenticator.local(ReviewerTokens.none()),
                FetchScheduleView.defaults(Clock.systemUTC().instant()), ReviewQueue.fixture(),
                new PronunciationLexiconStore(), new EditionWorkflowStore(), new FixtureVoiceProvider(),
                new PodcastFeedSettings(false));
        server.start();
        try {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/feed.xml")).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertEquals(404, response.statusCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publishesRssAndItunesMetadataAtThePublicEndpoint() throws Exception {
        HttpServer server = DailySignals.createServer(0);
        server.start();
        try {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/feed.xml")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("application/rss+xml; charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow());
            Document feed = parse(response.body());
            assertEquals("rss", feed.getDocumentElement().getTagName());
            assertEquals("2.0", feed.getDocumentElement().getAttribute("version"));
            assertEquals("Daily Signals", feed.getElementsByTagName("title").item(0).getTextContent());
            assertEquals("en", feed.getElementsByTagName("language").item(0).getTextContent());
            assertEquals("Daily Signals", feed.getElementsByTagNameNS(
                    "http://www.itunes.com/dtds/podcast-1.0.dtd", "author").item(0).getTextContent());
            assertEquals(1, feed.getElementsByTagName("item").getLength());
            assertTrue(feed.getElementsByTagName("guid").item(0).getTextContent()
                    .startsWith("urn:daily-signals:edition:"));
            assertTrue(feed.getElementsByTagName("pubDate").item(0).getTextContent().endsWith("GMT"));
            var enclosure = feed.getElementsByTagName("enclosure").item(0).getAttributes();
            assertEquals("https://stream.daily-signals.example/editions/demo-2026-01-15/audio",
                    enclosure.getNamedItem("url").getNodeValue());
            assertEquals("16044", enclosure.getNamedItem("length").getNodeValue());
            assertEquals("audio/wav", enclosure.getNamedItem("type").getNodeValue());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void headReportsTheFeedSizeWithoutSendingTheDocument() throws Exception {
        HttpServer server = DailySignals.createServer(0);
        server.start();
        try {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/feed.xml"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertEquals(0, response.body().length);
            assertTrue(response.headers().firstValueAsLong("Content-Length").orElseThrow() > 100);
        } finally {
            server.stop(0);
        }
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
