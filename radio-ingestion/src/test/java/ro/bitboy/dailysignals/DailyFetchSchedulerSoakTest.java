package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slow-tier qualification for retained resources across repeated daily fetch windows. */
class DailyFetchSchedulerSoakTest {
    private static final int CYCLES = Integer.getInteger("dailySignals.soak.cycles", 400);
    private static final long MAXIMUM_HEAP_GROWTH = Long.getLong(
            "dailySignals.soak.maximumHeapGrowthBytes", 64L * 1024 * 1024);

    @Test
    void keepsOnePendingScheduleAndBoundedHeapAcrossRecordedFeedCycles() throws Exception {
        var clock = new MutableClock(Instant.parse("2026-01-15T04:14:00Z"));
        var tasks = new SingleHandleScheduler(clock);
        var registry = new SourceRegistry();
        registry.register(new SourceRegistry.Source("public-desk", "Public desk",
                URI.create("https://feeds.example.test/rss.xml"), 10));
        registry.register(new SourceRegistry.Source("world-wire", "World wire",
                URI.create("https://feeds.example.test/atom.xml"), 20));

        try (var fixture = new FeedFixtureServer()) {
            var fetcher = new HttpFeedFetcher(registry, Set.of("feeds.example.test"),
                    HttpFeedFetcher.Policy.defaults(), clock, fixture.transport());
            var properties = new FetchScheduleProperties(ZoneId.of("Europe/Bucharest"), LocalTime.of(6, 15),
                    List.of("public-desk", "world-wire"));
            var scheduler = new DailyFetchScheduler(properties, clock, tasks, sourceIds -> {
                for (String sourceId : sourceIds) {
                    try {
                        fetcher.fetch(sourceId);
                    } catch (java.io.IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                }
            });

            scheduler.start();
            runCycles(clock, tasks, 25); // Warm the HTTP client, parser-independent cache and JIT.
            long warmedHeap = usedHeapAfterCollection();
            runCycles(clock, tasks, CYCLES);
            long finalHeap = usedHeapAfterCollection();

            assertEquals((CYCLES + 25) * 2, fixture.requests("/rss.xml") + fixture.requests("/atom.xml"));
            assertEquals(1, tasks.liveHandles);
            assertEquals(1, tasks.maximumLiveHandles);
            assertTrue(finalHeap - warmedHeap <= MAXIMUM_HEAP_GROWTH,
                    () -> "heap grew by " + (finalHeap - warmedHeap) + " bytes; limit is " + MAXIMUM_HEAP_GROWTH);

            scheduler.close();
            assertEquals(0, tasks.liveHandles);
            System.out.printf("schedule soak: cycles=%d requests=%d heapGrowthBytes=%d maxLiveHandles=%d%n",
                    CYCLES, (CYCLES + 25) * 2, finalHeap - warmedHeap, tasks.maximumLiveHandles);
        }
    }

    private static void runCycles(MutableClock clock, SingleHandleScheduler tasks, int cycles) {
        for (int cycle = 0; cycle < cycles; cycle++) {
            clock.now = tasks.nextRun;
            tasks.fire();
        }
    }

    private static long usedHeapAfterCollection() throws InterruptedException {
        System.gc();
        Thread.sleep(25);
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return heap.getUsed();
    }

    private static final class SingleHandleScheduler implements DailyFetchScheduler.Scheduler {
        private final MutableClock clock;
        private Runnable task;
        private Instant nextRun;
        private int liveHandles;
        private int maximumLiveHandles;

        private SingleHandleScheduler(MutableClock clock) {
            this.clock = clock;
        }

        @Override
        public DailyFetchScheduler.Cancellable schedule(Runnable task, Duration delay) {
            if (this.task != null) throw new IllegalStateException("more than one pending schedule");
            this.task = task;
            nextRun = clock.instant().plus(delay);
            liveHandles++;
            maximumLiveHandles = Math.max(maximumLiveHandles, liveHandles);
            return () -> {
                if (this.task != null) {
                    this.task = null;
                    liveHandles--;
                }
            };
        }

        void fire() {
            Runnable current = task;
            task = null;
            liveHandles--;
            current.run();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
