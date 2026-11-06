package ro.bitboy.dailysignals;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SourceRegistry {
    public record Terms(String attribution, Duration minimumFetchInterval) {
        public Terms {
            if (attribution == null || attribution.isBlank() || minimumFetchInterval == null
                    || minimumFetchInterval.isNegative()) {
                throw new IllegalArgumentException("source attribution and nonnegative fetch interval are required");
            }
        }
    }

    public record Source(String id, String name, URI url, int priority, Terms terms) {
        public Source(String id, String name, URI url, int priority) {
            this(id, name, url, priority, new Terms(name, Duration.ZERO));
        }

        public Source {
            if (id == null || !id.matches("[a-z][a-z0-9-]*")) throw new IllegalArgumentException("invalid source id");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("source name is required");
            if (url == null || !"https".equals(url.getScheme()) || url.getHost() == null) {
                throw new IllegalArgumentException("source URL must be HTTPS");
            }
            if (priority < 0) throw new IllegalArgumentException("priority must be nonnegative");
            if (terms == null) throw new IllegalArgumentException("source terms are required");
        }
    }

    private final Map<String, Source> sources = new LinkedHashMap<>();

    public void register(Source source) {
        if (sources.putIfAbsent(source.id(), source) != null) throw new IllegalArgumentException("duplicate source id");
    }

    public List<Source> all() { return List.copyOf(sources.values()); }
    public Source get(String id) { return sources.get(id); }
}
