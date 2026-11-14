package ro.bitboy.dailysignals;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** RSS 2.0 representation of published editions for podcast clients. */
public final class PodcastFeed {
    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME;

    private PodcastFeed() {
    }

    public static String from(EditionCatalog catalog) {
        var xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\">")
                .append("<channel><title>Daily Signals</title>")
                .append("<link>https://daily-signals.example/</link>")
                .append("<description>Daily public-interest news, prepared for listening.</description>")
                .append("<language>en</language><itunes:author>Daily Signals</itunes:author>")
                .append("<itunes:summary>Daily public-interest news, prepared for listening.</itunes:summary>")
                .append("<itunes:explicit>false</itunes:explicit>");
        for (var edition : catalog.entries()) {
            String title = escape(edition.title());
            xml.append("<item><title>").append(title).append("</title>")
                    .append("<description>").append(title).append("</description>")
                    .append("<guid isPermaLink=\"false\">urn:daily-signals:edition:")
                    .append(escape(edition.editionId())).append("</guid>")
                    .append("<pubDate>").append(RFC_1123.format(edition.airDate().atStartOfDay(ZoneOffset.UTC)))
                    .append("</pubDate><itunes:episodeType>full</itunes:episodeType></item>");
        }
        return xml.append("</channel></rss>").toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
