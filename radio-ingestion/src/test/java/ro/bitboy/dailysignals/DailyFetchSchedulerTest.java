package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyFetchSchedulerTest {
    @Test
    void runsConfiguredSourcesAndSchedulesTheFollowingStationDay() {
        var clock = new MutableClock(Instant.parse("2026-01-15T04:14:00Z"));
        var tasks = new RecordingScheduler();
        var windows = new ArrayList<List<String>>();
        var properties = new FetchScheduleProperties(ZoneId.of("Europe/Bucharest"), LocalTime.of(6, 15),
                List.of("public-desk", "world-wire"));
        var scheduler = new DailyFetchScheduler(properties, clock, tasks, windows::add);

        scheduler.start();
        scheduler.start();
        assertEquals(1, tasks.scheduled);
        assertEquals(Duration.ofMinutes(1), tasks.delay);
        assertEquals(Instant.parse("2026-01-15T04:15:00Z"), scheduler.nextRun().orElseThrow());

        clock.now = Instant.parse("2026-01-15T04:15:00Z");
        tasks.fire();

        assertEquals(List.of(List.of("public-desk", "world-wire")), windows);
        assertEquals(2, tasks.scheduled);
        assertEquals(Instant.parse("2026-01-16T04:15:00Z"), scheduler.nextRun().orElseThrow());
    }

    @Test
    void recalculatesAcrossDaylightSavingTransitionsAndReschedulesAfterFailure() {
        var clock = new MutableClock(Instant.parse("2026-03-28T05:00:00Z"));
        var tasks = new RecordingScheduler();
        var properties = new FetchScheduleProperties(ZoneId.of("Europe/Bucharest"), LocalTime.of(8, 30),
                List.of("public-desk"));
        var scheduler = new DailyFetchScheduler(properties, clock, tasks, ignored -> { throw new IllegalStateException("feed down"); });

        scheduler.start();
        assertEquals(Instant.parse("2026-03-28T06:30:00Z"), scheduler.nextRun().orElseThrow());
        clock.now = Instant.parse("2026-03-28T06:30:00Z");
        assertThrows(IllegalStateException.class, tasks::fire);
        assertEquals(Instant.parse("2026-03-29T05:30:00Z"), scheduler.nextRun().orElseThrow());

        scheduler.close();
        assertTrue(scheduler.nextRun().isEmpty());
        assertTrue(tasks.cancelled);
        assertThrows(IllegalStateException.class, scheduler::start);
    }

    private static final class RecordingScheduler implements DailyFetchScheduler.Scheduler {
        private Runnable task;
        private Duration delay;
        private int scheduled;
        private boolean cancelled;

        @Override
        public DailyFetchScheduler.Cancellable schedule(Runnable task, Duration delay) {
            this.task = task;
            this.delay = delay;
            scheduled++;
            cancelled = false;
            return () -> cancelled = true;
        }

        void fire() {
            Runnable current = task;
            task = null;
            current.run();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
