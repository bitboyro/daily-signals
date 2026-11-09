package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Bounded repeat profile for slow, transient and permanently failing object-store writes. */
class ObjectStoreRetrySoakTest {
    private static final byte[] WAV = {'R', 'I', 'F', 'F', 1, 2, 3, 4};

    @Test void transientFailuresRecoverWithinTheAttemptBoundAcrossRepeatedPublications() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/radio/editions/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var request = requests.getAndIncrement();
            var status = request % 3 < 2 ? 503 : 200;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new LocalS3ObjectAdapter(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "radio");
            var writer = new RecoveringObjectWriter(adapter, 3, Duration.ofMillis(1), Thread::sleep);
            var outbox = new PublishOutbox();
            for (int edition = 0; edition < 20; edition++) {
                var id = "retry-" + edition;
                outbox.stage(id, "editions/" + id + ".wav", WAV);
                outbox.publish(id, "PRODUCER", writer);
            }
            assertEquals(60, requests.get());
            assertEquals(0, outbox.pendingCount());
            assertEquals(20, outbox.publishedOperationCount());
        } finally {
            server.stop(0);
        }
    }

    @Test void permanentFailureStopsAtTheBoundAndLeavesTheItemPending() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/radio/editions/failing.wav", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            var adapter = new LocalS3ObjectAdapter(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "radio");
            var writer = new RecoveringObjectWriter(adapter, 3, Duration.ZERO, ignored -> {});
            var outbox = new PublishOutbox();
            outbox.stage("failing", "editions/failing.wav", WAV);
            assertThrows(IOException.class, () -> outbox.publish("failing", "PRODUCER", writer));
            assertEquals(3, requests.get());
            assertEquals(1, outbox.pendingCount());
        } finally {
            server.stop(0);
        }
    }
}
