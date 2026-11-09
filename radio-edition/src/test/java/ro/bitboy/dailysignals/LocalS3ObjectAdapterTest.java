package ro.bitboy.dailysignals;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class LocalS3ObjectAdapterTest {
    @Test void writesAndReadsByBucketAndKeyAgainstLocalProtocolFixture() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var stored = new java.util.concurrent.atomic.AtomicReference<byte[]>();
        server.createContext("/radio-fixtures/editions/test.wav", exchange -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                stored.set(exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(200, -1);
            } else if ("GET".equals(exchange.getRequestMethod()) && stored.get() != null) {
                exchange.sendResponseHeaders(200, stored.get().length);
                exchange.getResponseBody().write(stored.get());
            } else exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new LocalS3ObjectAdapter(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "radio-fixtures");
            byte[] wav = new byte[]{'R','I','F','F'};
            adapter.put("editions/test.wav", wav, "audio/wav");
            assertTrue(Arrays.equals(wav, adapter.get("editions/test.wav")));
            assertThrows(IllegalArgumentException.class, () -> adapter.objectUri("../escape"));
        } finally { server.stop(0); }
    }

    @Test void publishOutboxDeliversRecordingThroughTheObjectStoreAdapter() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var stored = new java.util.concurrent.atomic.AtomicReference<byte[]>();
        var contentType = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/radio-fixtures/editions/edition-1.wav", exchange -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                stored.set(exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(200, -1);
            } else if ("GET".equals(exchange.getRequestMethod()) && stored.get() != null) {
                exchange.sendResponseHeaders(200, stored.get().length);
                exchange.getResponseBody().write(stored.get());
            } else exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new LocalS3ObjectAdapter(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "radio-fixtures");
            var outbox = new PublishOutbox();
            byte[] wav = new byte[]{'R','I','F','F',1,2,3,4};
            outbox.stage("edition-1", "editions/edition-1.wav", wav);

            var manifest = outbox.publish("edition-1", "PRODUCER", adapter);

            assertEquals("editions/edition-1.wav", manifest.audioPath());
            assertEquals("audio/wav", contentType.get());
            assertArrayEquals(wav, adapter.get(manifest.audioPath()));
            assertEquals(0, outbox.pendingCount());
        } finally { server.stop(0); }
    }
}
