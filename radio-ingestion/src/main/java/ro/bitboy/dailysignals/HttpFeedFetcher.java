package ro.bitboy.dailysignals;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

    public record Policy(Duration connectTimeout, Duration requestTimeout, int maximumBytes) {
        public Policy {
            if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()
                    || requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                    || maximumBytes < 1) {
                throw new IllegalArgumentException("positive fetch timeouts and body limit are required");
            }
        }

        public static Policy defaults() {
            return new Policy(Duration.ofSeconds(5), Duration.ofSeconds(10), 1_048_576);
        }
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
    private final Policy policy;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastRequests = new ConcurrentHashMap<>();

    private record Cached(FeedDocument document, String etag, String lastModified) {}

    public HttpFeedFetcher(SourceRegistry registry, Set<String> allowedHosts) {
        this(registry, allowedHosts, Policy.defaults(), Clock.systemUTC());
    }

    public HttpFeedFetcher(SourceRegistry registry, Set<String> allowedHosts, Policy policy, Clock clock) {
        this(registry, allowedHosts, policy, clock, jdkTransport(policy));
    }

    public HttpFeedFetcher(SourceRegistry registry, Set<String> allowedHosts, Transport transport) {
        this(registry, allowedHosts, Policy.defaults(), Clock.systemUTC(), transport);
    }

    HttpFeedFetcher(SourceRegistry registry, Set<String> allowedHosts, Policy policy, Clock clock, Transport transport) {
        if (registry == null || transport == null) throw new IllegalArgumentException("registry and transport are required");
        this.registry = registry;
        this.allowedHosts = allowedHosts.stream().map(HttpFeedFetcher::host).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.transport = transport;
        this.policy = policy;
        this.clock = clock;
    }

    public FeedDocument fetch(String sourceId) throws IOException {
        var source = registry.get(sourceId);
        if (source == null) throw new IllegalArgumentException("unknown source: " + sourceId);
        if (!allowedHosts.contains(host(source.url().getHost()))) {
            throw new SecurityException("source host is not allowlisted: " + source.url().getHost());
        }
        Instant now = clock.instant();
        Instant previousRequest = lastRequests.get(sourceId);
        if (previousRequest != null && now.isBefore(previousRequest.plus(source.terms().minimumFetchInterval()))) {
            throw new IOException("source fetch rate limit has not elapsed");
        }
        lastRequests.put(sourceId, now);
        Cached previous = cache.get(sourceId);
        Map<String, String> validators = previous == null ? Map.of() : validators(previous);
        Response response = transport.get(source.url(), validators);
        if (response.status() == 304) {
            if (previous == null) throw new IOException("feed returned 304 without a cached document");
            return previous.document();
        }
        if (response.status() != 200) throw new IOException("feed returned HTTP " + response.status());
        if (response.body().length > policy.maximumBytes()) throw new IOException("feed exceeds maximum body size");
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

    private static Transport jdkTransport(Policy policy) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(policy.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return (uri, conditionalHeaders) -> {
            var builder = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/atom+xml, application/rss+xml, application/xml;q=0.9");
            conditionalHeaders.forEach(builder::header);
            HttpRequest request = builder.timeout(policy.requestTimeout()).GET().build();
            try {
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
                try (var input = response.body()) {
                    return new Response(response.statusCode(), response.headers().map(),
                            input.readNBytes(policy.maximumBytes() + 1));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("feed request interrupted", interrupted);
            }
        };
    }
}
