package ro.bitboy.dailysignals;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Read-only API projection of the active fetch schedule. */
public final class FetchScheduleView {
    private final ZoneId zone;
    private final LocalTime window;
    private final List<String> sources;
    private final Supplier<Optional<Instant>> nextRun;

    public FetchScheduleView(ZoneId zone, LocalTime window, List<String> sources,
                             Supplier<Optional<Instant>> nextRun) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.window = Objects.requireNonNull(window, "window");
        this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (sources.isEmpty()) throw new IllegalArgumentException("at least one source is required");
        this.nextRun = Objects.requireNonNull(nextRun, "nextRun");
    }

    public static FetchScheduleView defaults(Instant now) {
        ZoneId zone = ZoneId.of("UTC");
        LocalTime window = LocalTime.of(6, 0);
        var localNow = now.atZone(zone);
        Instant today = localNow.toLocalDate().atTime(window).atZone(zone).toInstant();
        Instant next = now.isBefore(today) ? today
                : localNow.toLocalDate().plusDays(1).atTime(window).atZone(zone).toInstant();
        return new FetchScheduleView(zone, window, List.of("public-desk"), () -> Optional.of(next));
    }

    public String toJson() {
        Instant next = nextRun.get().orElseThrow(() -> new IllegalStateException("fetch scheduler has not started"));
        return "{\"schemaVersion\":1,\"nextRun\":" + quote(next.toString())
                + ",\"zone\":" + quote(zone.getId())
                + ",\"window\":" + quote(window.toString())
                + ",\"sources\":[" + sources.stream().map(FetchScheduleView::quote)
                .reduce((left, right) -> left + "," + right).orElse("") + "]}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
