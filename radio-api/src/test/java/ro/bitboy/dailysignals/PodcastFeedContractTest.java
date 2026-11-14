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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PodcastFeedContractTest {
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
