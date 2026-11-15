package ro.bitboy.dailysignals;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Dependency-free actuator-compatible health and Prometheus projection for the API. */
final class ApiMetrics {
    private final Clock clock;
    private final long startedAtMillis;
    private final BooleanSupplier configured;
    private final AtomicLong scrapes = new AtomicLong();

    ApiMetrics(Clock clock, BooleanSupplier configured) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.startedAtMillis = clock.millis();
        this.configured = java.util.Objects.requireNonNull(configured, "configured");
    }

    String healthJson() {
        return "{\"status\":\"UP\",\"components\":{\"api\":{\"status\":\"UP\"},"
                + "\"stationSettings\":{\"status\":\"" + (configured.getAsBoolean() ? "UP" : "UNKNOWN")
                + "\"}}}";
    }

    String prometheus() {
        long scrape = scrapes.incrementAndGet();
        double uptime = Math.max(0, clock.millis() - startedAtMillis) / 1_000.0;
        return """
                # HELP daily_signals_api_up Whether the Daily Signals API is ready.
                # TYPE daily_signals_api_up gauge
                daily_signals_api_up 1
                # HELP daily_signals_api_uptime_seconds Seconds since the API metrics registry started.
                # TYPE daily_signals_api_uptime_seconds gauge
                daily_signals_api_uptime_seconds %s
                # HELP daily_signals_station_configured Whether station setup is complete.
                # TYPE daily_signals_station_configured gauge
                daily_signals_station_configured %d
                # HELP daily_signals_metrics_scrapes_total Prometheus endpoint scrapes.
                # TYPE daily_signals_metrics_scrapes_total counter
                daily_signals_metrics_scrapes_total %d
                """.formatted(Double.toString(uptime), configured.getAsBoolean() ? 1 : 0, scrape);
    }
}
