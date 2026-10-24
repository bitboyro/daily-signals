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
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", exchange -> respond(exchange, 200, "application/json", "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/editions/demo/audio", exchange -> {
            try (InputStream fixture = DailySignals.class.getResourceAsStream("/fixtures/demo-edition.wav")) {
                if (fixture == null) {
                    respond(exchange, 500, "text/plain", "Audio fixture unavailable".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                respond(exchange, 200, "audio/wav", fixture.readAllBytes());
            }
        });
        return server;
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
        HttpServer server = createServer(port);
        server.start();
        System.out.println("Daily Signals listening on http://127.0.0.1:" + server.getAddress().getPort());
    }
}
