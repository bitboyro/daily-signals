package ro.bitboy.dailysignals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Loopback-only recorded feed server. Tests never contact a public network. */
final class FeedFixtureServer implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();

    FeedFixtureServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serve("/rss.xml", "application/rss+xml", "/feeds/public-desk.rss.xml");
        serve("/atom.xml", "application/atom+xml", "/feeds/world.atom.xml");
        server.start();
    }

    URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    int requests(String path) {
        return requests.getOrDefault(path, new AtomicInteger()).get();
    }

    HttpFeedFetcher.Transport transport() {
        HttpClient client = HttpClient.newHttpClient();
        return source -> {
            try {
                var response = client.send(HttpRequest.newBuilder(uri(source.getPath())).GET().build(), BodyHandlers.ofByteArray());
                return new HttpFeedFetcher.Response(response.statusCode(), response.headers().map(), response.body());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("fixture request interrupted", interrupted);
            }
        };
    }

    private void serve(String path, String contentType, String resource) {
        server.createContext(path, exchange -> respond(exchange, contentType, resource));
    }

    private void respond(HttpExchange exchange, String contentType, String resource) throws IOException {
        requests.computeIfAbsent(exchange.getRequestURI().getPath(), ignored -> new AtomicInteger()).incrementAndGet();
        byte[] body;
        try (var input = FeedFixtureServer.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("missing fixture " + resource);
            body = input.readAllBytes();
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
    }

    @Override public void close() { server.stop(0); }
}
