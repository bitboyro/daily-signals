package ro.bitboy.dailysignals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Runs one configured source window per station-local day and recalculates after every run for DST. */
public final class DailyFetchScheduler implements AutoCloseable {
    @FunctionalInterface
    public interface FetchWindow {
        void fetch(List<String> sourceIds);
    }

    @FunctionalInterface
    public interface Scheduler {
        Cancellable schedule(Runnable task, Duration delay);

        static Scheduler executor(ScheduledExecutorService executor) {
            Objects.requireNonNull(executor, "executor");
            return (task, delay) -> {
                var future = executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
                return () -> future.cancel(false);
            };
        }
    }

    @FunctionalInterface
    public interface Cancellable {
        void cancel();
    }

    private final FetchScheduleProperties properties;
    private final Clock clock;
    private final Scheduler scheduler;
    private final FetchWindow fetchWindow;
    private Cancellable pending;
    private Instant nextRun;
    private boolean closed;

    public DailyFetchScheduler(FetchScheduleProperties properties, Clock clock, Scheduler scheduler, FetchWindow fetchWindow) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.fetchWindow = Objects.requireNonNull(fetchWindow, "fetchWindow");
    }

    public synchronized void start() {
        if (closed) throw new IllegalStateException("scheduler is closed");
        if (pending == null) scheduleAfter(clock.instant());
    }

    public synchronized Optional<Instant> nextRun() {
        return Optional.ofNullable(nextRun);
    }

    public Instant nextRunAfter(Instant now) {
        Objects.requireNonNull(now, "now");
        ZonedDateTime localNow = now.atZone(properties.zone());
        Instant today = localNow.toLocalDate().atTime(properties.window()).atZone(properties.zone()).toInstant();
        return now.isBefore(today) ? today
                : localNow.toLocalDate().plusDays(1).atTime(properties.window()).atZone(properties.zone()).toInstant();
    }

    private synchronized void scheduleAfter(Instant now) {
        nextRun = nextRunAfter(now);
        Duration delay = Duration.between(now, nextRun);
        pending = scheduler.schedule(this::runWindow, delay);
    }

    private void runWindow() {
        synchronized (this) {
            if (closed) return;
            pending = null;
            nextRun = null;
        }
        try {
            fetchWindow.fetch(properties.sources());
        } finally {
            synchronized (this) {
                if (!closed) scheduleAfter(clock.instant());
            }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        nextRun = null;
        if (pending != null) pending.cancel();
        pending = null;
    }
}
