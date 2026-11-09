package ro.bitboy.dailysignals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

public final class DailySignals {
    private DailySignals() {
    }

    private static final Map<String, String> WEB_TYPES = Map.of(
            "html", "text/html; charset=utf-8", "js", "text/javascript; charset=utf-8", "css", "text/css; charset=utf-8");

    public static HttpServer createServer(int port) throws IOException {
        return createServer(port, null, ReviewerTokens.none());
    }

    public static HttpServer createServer(int port, ReviewerTokens reviewers) throws IOException {
        return createServer(port, null, reviewers);
    }

    public static HttpServer createServer(int port, Path webRoot) throws IOException {
        return createServer(port, webRoot, ReviewerTokens.none());
    }

    /** Serves the API and, when {@code webRoot} is given, the built listener UI from that directory. */
    public static HttpServer createServer(int port, Path webRoot, ReviewerTokens reviewers) throws IOException {
        return createServer("127.0.0.1", port, webRoot, reviewers);
    }

    /** Loopback by default; container images bind every interface through DAILY_SIGNALS_BIND_ADDRESS. */
    public static HttpServer createServer(String bindAddress, int port, Path webRoot, ReviewerTokens reviewers)
            throws IOException {
        return createServer(bindAddress, port, webRoot, reviewers, FetchScheduleView.defaults(Clock.systemUTC().instant()));
    }

    public static HttpServer createServer(String bindAddress, int port, Path webRoot, ReviewerTokens reviewers,
                                          FetchScheduleView fetchSchedule) throws IOException {
        return createServer(bindAddress, port, webRoot, reviewers, fetchSchedule, ReviewQueue.fixture());
    }

    static HttpServer createServer(String bindAddress, int port, Path webRoot, ReviewerTokens reviewers,
                                   FetchScheduleView fetchSchedule, ReviewQueueBackend reviewQueue) throws IOException {
        return createServer(bindAddress, port, webRoot, ProducerAuthenticator.local(reviewers), fetchSchedule, reviewQueue);
    }

    static HttpServer createServer(String bindAddress, int port, Path webRoot, ProducerAuthenticator authenticator,
                                   FetchScheduleView fetchSchedule, ReviewQueueBackend reviewQueue) throws IOException {
        return createServer(bindAddress, port, webRoot, authenticator, fetchSchedule, reviewQueue,
                new PronunciationLexiconStore());
    }

    static HttpServer createServer(String bindAddress, int port, Path webRoot, ProducerAuthenticator authenticator,
                                   FetchScheduleView fetchSchedule, ReviewQueueBackend reviewQueue,
                                   PronunciationLexiconBackend pronunciations) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        EditionCatalog catalog = EditionCatalog.fixture();
        EditionScriptStore script = new EditionScriptStore("Daily Signals. Add today’s approved stories here.");
        server.createContext("/health", exchange -> respond(exchange, 200, "application/json", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/api/editions", exchange -> respond(exchange, 200, "application/json",
                catalog.toJson().getBytes(StandardCharsets.UTF_8)));
        server.createContext("/api/schedule", exchange -> respond(exchange, 200, "application/json",
                fetchSchedule.toJson().getBytes(StandardCharsets.UTF_8)));
        server.createContext("/editions/", exchange -> {
            var legacy = LegacyRoutes.redirectFor(exchange.getRequestURI().getPath());
            if (legacy.isPresent()) {
                exchange.getResponseHeaders().set("Location", legacy.get());
                reply(exchange, 308);
                return;
            }
            var edition = catalog.entries().stream()
                    .filter(entry -> entry.audioPath().equals(exchange.getRequestURI().getPath())).findFirst();
            if (edition.isEmpty()) {
                respond(exchange, 404, "text/plain", "Unknown edition".getBytes(StandardCharsets.UTF_8));
                return;
            }
            try (InputStream fixture = DailySignals.class.getResourceAsStream("/fixtures/demo-edition.wav")) {
                if (fixture == null) {
                    respond(exchange, 500, "text/plain", "Audio fixture unavailable".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                respond(exchange, 200, "audio/wav", fixture.readAllBytes());
            }
        });
        server.createContext("/api/review-queue", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && "/api/review-queue".equals(path)) {
                try {
                    respond(exchange, 200, "application/json", reviewQueue.toJson().getBytes(StandardCharsets.UTF_8));
                } catch (IllegalStateException unavailable) {
                    reply(exchange, 503);
                }
                return;
            }
            if ("GET".equals(exchange.getRequestMethod()) && "/api/review-queue/audit".equals(path)) {
                var identity = authenticator.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
                if (identity.isEmpty()) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                    reply(exchange, 401);
                    return;
                }
                if (!ProducerEndpointPolicy.permits(ProducerEndpointPolicy.Operation.READ_AUDIT, identity.get().role())) {
                    reply(exchange, 403);
                    return;
                }
                try {
                    respond(exchange, 200, "application/json", reviewQueue.auditToJson().getBytes(StandardCharsets.UTF_8));
                } catch (IllegalStateException unavailable) {
                    reply(exchange, 503);
                }
                return;
            }
            String storyId = path.substring("/api/review-queue".length()).replaceFirst("^/", "");
            var identity = authenticator.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
            if (!"POST".equals(exchange.getRequestMethod()) || storyId.isEmpty() || storyId.contains("/")) {
                reply(exchange, 405);
            } else if (identity.isEmpty()) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                reply(exchange, 401);
            } else if (!ProducerEndpointPolicy.permits(ProducerEndpointPolicy.Operation.REVIEW_STORY,
                    identity.get().role())) {
                reply(exchange, 403);
            } else {
                try {
                    if (!reviewQueue.contains(storyId)) {
                        reply(exchange, 404);
                        return;
                    }
                    try (var body = exchange.getRequestBody()) {
                        String receipt = reviewQueue.decideJson(storyId,
                                new String(body.readNBytes(64), StandardCharsets.UTF_8), identity.orElseThrow().subject());
                        respondWrite(exchange, 200, "application/json", receipt.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IllegalArgumentException invalid) {
                    reply(exchange, 400);
                } catch (IllegalStateException unavailable) {
                    reply(exchange, 503);
                }
            }
        });
        server.createContext("/api/producer/script", exchange -> {
            var identity = authenticator.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
            if (identity.isEmpty()) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                reply(exchange, 401);
                return;
            }
            if (!ProducerEndpointPolicy.permits(ProducerEndpointPolicy.Operation.EDIT_SCRIPT, identity.get().role())) {
                reply(exchange, 403);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "application/json", script.toJson().getBytes(StandardCharsets.UTF_8));
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                try (var body = exchange.getRequestBody()) {
                    String saved = script.saveJson(new String(body.readNBytes(20_001), StandardCharsets.UTF_8));
                    respondWrite(exchange, 200, "application/json", saved.getBytes(StandardCharsets.UTF_8));
                } catch (IllegalArgumentException invalid) {
                    reply(exchange, 400);
                }
            } else {
                reply(exchange, 405);
            }
        });
        server.createContext("/api/producer/pronunciations", exchange -> {
            var identity = authenticator.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
            if (identity.isEmpty()) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                reply(exchange, 401);
                return;
            }
            if (!ProducerEndpointPolicy.permits(ProducerEndpointPolicy.Operation.EDIT_PRONUNCIATION,
                    identity.get().role())) {
                reply(exchange, 403);
                return;
            }
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    respond(exchange, 200, "application/json", pronunciations.toJson().getBytes(StandardCharsets.UTF_8));
                } else if ("PUT".equals(exchange.getRequestMethod())) {
                    try (var body = exchange.getRequestBody()) {
                    String saved = pronunciations.saveJson(
                            new String(body.readNBytes(20_001), StandardCharsets.UTF_8));
                    respondWrite(exchange, 200, "application/json", saved.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    reply(exchange, 405);
                }
            } catch (IllegalArgumentException invalid) {
                reply(exchange, 400);
            } catch (IllegalStateException unavailable) {
                reply(exchange, 503);
            }
        });
        server.createContext("/api/producer/audio", exchange -> {
            var identity = authenticator.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
            if (identity.isEmpty()) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                reply(exchange, 401);
                return;
            }
            if (!ProducerEndpointPolicy.permits(ProducerEndpointPolicy.Operation.GENERATE_AUDIO,
                    identity.get().role())) {
                reply(exchange, 403);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                reply(exchange, 405);
                return;
            }
            try {
                var draft = script.synthesisScript();
                var spoken = new ScriptAssembler.Script(pronunciations.speakable(draft.text()), draft.storyIds());
                var audio = new FixtureVoiceProvider().render(spoken);
                var validation = AudioValidation.inspect(audio.bytes());
                if (!validation.playable()) throw new IllegalStateException("voice provider returned invalid audio");
                exchange.getResponseHeaders().set("X-Script-SHA256", audio.scriptSha256());
                exchange.getResponseHeaders().set("X-Audio-Sample-Rate", Integer.toString(validation.sampleRate()));
                exchange.getResponseHeaders().set("X-Audio-Channels", Integer.toString(validation.channels()));
                exchange.getResponseHeaders().set("X-Audio-Duration-Ms", Long.toString(validation.durationMillis()));
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                respondWrite(exchange, 200, audio.contentType(), audio.bytes());
            } catch (IllegalArgumentException invalid) {
                reply(exchange, 400);
            } catch (IllegalStateException | IOException unavailable) {
                reply(exchange, 503);
            }
        });
        if (webRoot != null) {
            Path root = webRoot.toAbsolutePath().normalize();
            server.createContext("/", exchange -> {
                String requested = exchange.getRequestURI().getPath();
                Path file = root.resolve(requested.equals("/") ? "index.html" : requested.substring(1)).normalize();
                String name = file.getFileName() == null ? "" : file.getFileName().toString();
                String type = WEB_TYPES.get(name.substring(name.lastIndexOf('.') + 1));
                if (!file.startsWith(root) || type == null || !Files.isRegularFile(file)) {
                    respond(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                respond(exchange, 200, type, Files.readAllBytes(file));
            });
        }
        return server;
    }

    private static void reply(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void respondWrite(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 8151 : Integer.parseInt(args[0]);
        String webRoot = System.getenv("DAILY_SIGNALS_WEB_ROOT");
        String bind = System.getenv().getOrDefault("DAILY_SIGNALS_BIND_ADDRESS", "127.0.0.1");
        var database = DatabaseRuntime.fromEnvironment(System.getenv());
        ReviewQueueBackend reviews;
        PronunciationLexiconBackend pronunciations;
        if (database.isPresent()) {
            database.get().migrate();
            reviews = new PostgresReviewQueue(database.get());
            pronunciations = new PostgresPronunciationLexicon(database.get());
        } else {
            reviews = ReviewQueue.fixture();
            pronunciations = new PronunciationLexiconStore();
        }
        var environment = System.getenv();
        var fallback = ReviewerTokens.parse(environment.get("DAILY_SIGNALS_REVIEW_TOKENS"));
        HttpServer server = createServer(bind, port, webRoot == null || webRoot.isBlank() ? null : Path.of(webRoot),
                ProducerAuthenticator.fromEnvironment(environment, fallback),
                FetchScheduleView.defaults(Clock.systemUTC().instant()), reviews, pronunciations);
        server.start();
        int actualPort = server.getAddress().getPort();
        String address = "http://" + bind + ":" + actualPort;
        System.out.println(StructuredLog.event("INFO", "api.started", "Daily Signals listening on " + address,
                java.util.Map.of("address", address, "port", actualPort)));
    }
}
