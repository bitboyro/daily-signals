package ro.bitboy.dailysignals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        EditionCatalog catalog = EditionCatalog.fixture();
        server.createContext("/health", exchange -> respond(exchange, 200, "application/json", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/api/editions", exchange -> respond(exchange, 200, "application/json",
                catalog.toJson().getBytes(StandardCharsets.UTF_8)));
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
        var reviews = new Object() {
            private ReviewQueue queue;
            synchronized ReviewQueue get() {
                if (queue == null) queue = ReviewQueue.fixture();
                return queue;
            }
        };
        server.createContext("/api/review-queue", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && "/api/review-queue".equals(path)) {
                respond(exchange, 200, "application/json", reviews.get().toJson().getBytes(StandardCharsets.UTF_8));
                return;
            }
            String storyId = path.substring("/api/review-queue".length()).replaceFirst("^/", "");
            var role = reviewers.roleFor(exchange.getRequestHeaders().getFirst("Authorization"));
            if (!"POST".equals(exchange.getRequestMethod()) || storyId.isEmpty() || storyId.contains("/")) {
                reply(exchange, 405);
            } else if (role.isEmpty()) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                reply(exchange, 401);
            } else if (!ProducerPermissions.mayReview(role.get())) {
                reply(exchange, 403);
            } else if (!reviews.get().contains(storyId)) {
                reply(exchange, 404);
            } else {
                try (var body = exchange.getRequestBody()) {
                    var receipt = reviews.get().decide(storyId, new String(body.readNBytes(64), StandardCharsets.UTF_8));
                    respondWrite(exchange, 200, "application/json", receipt.toJson().getBytes(StandardCharsets.UTF_8));
                } catch (IllegalArgumentException invalid) {
                    reply(exchange, 400);
                }
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

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8151 : Integer.parseInt(args[0]);
        String webRoot = System.getenv("DAILY_SIGNALS_WEB_ROOT");
        String bind = System.getenv().getOrDefault("DAILY_SIGNALS_BIND_ADDRESS", "127.0.0.1");
        HttpServer server = createServer(bind, port, webRoot == null || webRoot.isBlank() ? null : Path.of(webRoot),
                ReviewerTokens.parse(System.getenv("DAILY_SIGNALS_REVIEW_TOKENS")));
        server.start();
        System.out.println("Daily Signals listening on http://" + bind + ":" + server.getAddress().getPort());
    }
}
