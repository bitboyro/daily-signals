package ro.bitboy.dailysignals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class DailySignals {
    private DailySignals() {
    }

    public static HttpServer createServer(int port) throws IOException {
        return createServer(port, ReviewerTokens.none());
    }

    public static HttpServer createServer(int port, ReviewerTokens reviewers) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", exchange -> respond(exchange, 200, "application/json", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8)));
        EditionCatalog catalog = EditionCatalog.fixture();
        server.createContext("/api/editions", exchange -> respond(exchange, 200, "application/json",
                catalog.toJson().getBytes(StandardCharsets.UTF_8)));
        server.createContext("/editions/demo/audio", exchange -> {
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
                    reviews.get().decide(storyId, new String(body.readNBytes(64), StandardCharsets.UTF_8));
                    reply(exchange, 204);
                } catch (IllegalArgumentException invalid) {
                    reply(exchange, 400);
                }
            }
        });
        return server;
    }

    private static void reply(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
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
        HttpServer server = createServer(port, ReviewerTokens.parse(System.getenv("DAILY_SIGNALS_REVIEW_TOKENS")));
        server.start();
        System.out.println("Daily Signals listening on http://127.0.0.1:" + server.getAddress().getPort());
    }
}
