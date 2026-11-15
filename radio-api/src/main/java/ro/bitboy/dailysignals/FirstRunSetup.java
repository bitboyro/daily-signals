package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Durable first-run station configuration shared by the setup page and scheduler projection. */
final class FirstRunSetup {
    record Source(String id, String name, String url, int priority) {}
    record Configuration(int schemaVersion, boolean configured, List<Source> sources,
                         String voice, String zone, String window) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path path;
    private Configuration configuration;

    FirstRunSetup(Path path) {
        this.path = path;
        this.configuration = load(path);
    }

    static FirstRunSetup inMemory() { return new FirstRunSetup(null); }

    static FirstRunSetup fromEnvironment(Map<String, String> environment) {
        String value = environment.get("DAILY_SIGNALS_SETUP_PATH");
        return value == null || value.isBlank() ? inMemory() : new FirstRunSetup(Path.of(value));
    }

    synchronized boolean configured() { return configuration.configured(); }

    synchronized String voice() { return configuration.voice(); }

    synchronized String toJson() {
        try { return JSON.writeValueAsString(configuration); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    synchronized String saveJson(String body) {
        if (body == null || body.isBlank() || body.length() > 50_000)
            throw new IllegalArgumentException("setup payload must be nonempty and at most 50 KB");
        try {
            Configuration candidate = JSON.readValue(body, Configuration.class);
            validate(candidate);
            configuration = candidate;
            if (path != null) persist(candidate);
            return toJson();
        } catch (IOException invalid) {
            throw new IllegalArgumentException("invalid setup payload", invalid);
        }
    }

    synchronized String scheduleJson(Instant now, FetchScheduleView fallback) {
        if (!configuration.configured()) return fallback.toJson();
        ZoneId zone = ZoneId.of(configuration.zone());
        LocalTime window = LocalTime.parse(configuration.window());
        var localNow = now.atZone(zone);
        Instant today = localNow.toLocalDate().atTime(window).atZone(zone).toInstant();
        Instant next = now.isBefore(today) ? today
                : localNow.toLocalDate().plusDays(1).atTime(window).atZone(zone).toInstant();
        return new FetchScheduleView(zone, window,
                configuration.sources().stream().map(Source::id).toList(), () -> java.util.Optional.of(next)).toJson();
    }

    private void persist(Configuration value) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling("." + path.getFileName() + ".tmp");
        JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Configuration load(Path path) {
        if (path == null || !Files.isRegularFile(path)) return defaults();
        try {
            Configuration value = JSON.readValue(path.toFile(), Configuration.class);
            validate(value);
            return value;
        } catch (IOException | IllegalArgumentException invalid) {
            throw new IllegalStateException("invalid persisted setup at " + path, invalid);
        }
    }

    private static Configuration defaults() {
        return new Configuration(1, false,
                List.of(new Source("public-desk", "Public desk", "https://news.example.invalid/public.xml", 10)),
                "fixture", "UTC", "06:00");
    }

    private static void validate(Configuration value) {
        Objects.requireNonNull(value, "configuration");
        if (value.schemaVersion() != 1 || !value.configured())
            throw new IllegalArgumentException("completed setup schema version 1 required");
        if (value.sources() == null || value.sources().isEmpty() || value.sources().size() > 25)
            throw new IllegalArgumentException("between 1 and 25 sources required");
        var ids = new java.util.HashSet<String>();
        for (Source source : value.sources()) {
            if (source == null || source.id() == null || !source.id().matches("[a-z][a-z0-9-]*")
                    || !ids.add(source.id()) || source.name() == null || source.name().isBlank()
                    || source.priority() < 0) throw new IllegalArgumentException("invalid source");
            URI uri = URI.create(source.url());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("source URL must use HTTPS");
        }
        if (!("fixture".equals(value.voice()) || "kokoro".equals(value.voice())))
            throw new IllegalArgumentException("voice must be fixture or kokoro");
        ZoneId.of(value.zone());
        LocalTime.parse(value.window());
    }
}
