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
    private final Map<String, String> lastRequestHeaders = new ConcurrentHashMap<>();

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

    String lastRequestHeader(String name) { return lastRequestHeaders.get(name.toLowerCase()); }

    HttpFeedFetcher.Transport transport() {
        HttpClient client = HttpClient.newHttpClient();
        return (source, headers) -> {
            try {
                var builder = HttpRequest.newBuilder(uri(source.getPath()));
                headers.forEach(builder::header);
                var response = client.send(builder.GET().build(), BodyHandlers.ofByteArray());
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
        exchange.getRequestHeaders().forEach((name, values) -> lastRequestHeaders.put(name.toLowerCase(), values.getFirst()));
        exchange.getResponseHeaders().set("ETag", "\"recorded-v1\"");
        exchange.getResponseHeaders().set("Last-Modified", "Fri, 06 Nov 2026 08:00:00 GMT");
        if ("\"recorded-v1\"".equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }
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
