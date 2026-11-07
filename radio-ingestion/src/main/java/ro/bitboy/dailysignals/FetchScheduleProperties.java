package ro.bitboy.dailysignals;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;

/** Station-local time and ordered source ids for the daily ingestion window. */
@ConfigurationProperties("daily-signals.fetch")
public record FetchScheduleProperties(ZoneId zone, LocalTime window, List<String> sources) {
    public FetchScheduleProperties {
        if (zone == null || window == null || sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("fetch zone, window and at least one source are required");
        }
        sources = List.copyOf(sources);
        if (sources.stream().anyMatch(id -> id == null || !id.matches("[a-z][a-z0-9-]*"))
                || new LinkedHashSet<>(sources).size() != sources.size()) {
            throw new IllegalArgumentException("fetch sources must be unique source ids");
        }
    }
}
