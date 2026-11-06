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
import java.util.concurrent.ConcurrentHashMap;

/** Fetches registered feeds only when their exact host is on the operator allowlist. */
public final class HttpFeedFetcher {
    @FunctionalInterface
    public interface Transport {
        Response get(URI uri, Map<String, String> requestHeaders) throws IOException;
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
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(FeedDocument document, String etag, String lastModified) {}

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
        Cached previous = cache.get(sourceId);
        Map<String, String> validators = previous == null ? Map.of() : validators(previous);
        Response response = transport.get(source.url(), validators);
        if (response.status() == 304) {
            if (previous == null) throw new IOException("feed returned 304 without a cached document");
            return previous.document();
        }
        if (response.status() != 200) throw new IOException("feed returned HTTP " + response.status());
        var document = new FeedDocument(source, response.headers(), response.body());
        cache.put(sourceId, new Cached(document, header(response.headers(), "etag"),
                header(response.headers(), "last-modified")));
        return document;
    }

    private static Map<String, String> validators(Cached cached) {
        var headers = new java.util.LinkedHashMap<String, String>();
        if (cached.etag() != null) headers.put("If-None-Match", cached.etag());
        if (cached.lastModified() != null) headers.put("If-Modified-Since", cached.lastModified());
        return Map.copyOf(headers);
    }

    private static String header(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream()).findFirst().orElse(null);
    }

    private static String host(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("allowlisted host is required");
        return value.toLowerCase(Locale.ROOT);
    }

    private static Transport jdkTransport() {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return (uri, conditionalHeaders) -> {
            var builder = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/atom+xml, application/rss+xml, application/xml;q=0.9");
            conditionalHeaders.forEach(builder::header);
            HttpRequest request = builder.GET().build();
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
