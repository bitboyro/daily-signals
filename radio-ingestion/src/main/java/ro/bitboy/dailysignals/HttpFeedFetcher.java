package ro.bitboy.dailysignals;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Fetches registered feeds only when their exact host is on the operator allowlist. */
public final class HttpFeedFetcher {
    @FunctionalInterface
    public interface Transport {
        Response get(URI uri) throws IOException;
    }

    public record Response(int status, Map<String, List<String>> headers, byte[] body) {
        public Response {
            headers = Map.copyOf(headers);
            body = body.clone();
        }

        @Override public byte[] body() { return body.clone(); }
    }

    public record FeedDocument(SourceRegistry.Source source, Map<String, List<String>> headers, byte[] body) {
        public FeedDocument {
            headers = Map.copyOf(headers);
            body = body.clone();
        }

        @Override public byte[] body() { return body.clone(); }
    }

    private final SourceRegistry registry;
    private final Set<String> allowedHosts;
    private final Transport transport;

    public HttpFeedFetcher(SourceRegistry registry, Set<String> allowedHosts) {
        this(registry, allowedHosts, jdkTransport());
    }

    public HttpFeedFetcher(SourceRegistry registry, Set<String> allowedHosts, Transport transport) {
        if (registry == null || transport == null) throw new IllegalArgumentException("registry and transport are required");
        this.registry = registry;
        this.allowedHosts = allowedHosts.stream().map(HttpFeedFetcher::host).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.transport = transport;
    }

    public FeedDocument fetch(String sourceId) throws IOException {
        var source = registry.get(sourceId);
        if (source == null) throw new IllegalArgumentException("unknown source: " + sourceId);
        if (!allowedHosts.contains(host(source.url().getHost()))) {
            throw new SecurityException("source host is not allowlisted: " + source.url().getHost());
        }
        Response response = transport.get(source.url());
        if (response.status() != 200) throw new IOException("feed returned HTTP " + response.status());
        return new FeedDocument(source, response.headers(), response.body());
    }

    private static String host(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("allowlisted host is required");
        return value.toLowerCase(Locale.ROOT);
    }

    private static Transport jdkTransport() {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/atom+xml, application/rss+xml, application/xml;q=0.9")
                    .GET().build();
            try {
                var response = client.send(request, BodyHandlers.ofByteArray());
                return new Response(response.statusCode(), response.headers().map(), response.body());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("feed request interrupted", interrupted);
            }
        };
    }
}
