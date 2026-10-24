package ro.bitboy.dailysignals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Resolves a station-local publication time to one unambiguous instant. */
public record ScheduledEditionClock(ZoneId zone, LocalTime airTime) {
    public ScheduledEditionClock {
        if (zone == null || airTime == null) throw new IllegalArgumentException("zone and air time required");
    }
    public Instant scheduledAt(LocalDate airDate) {
        if (airDate == null) throw new IllegalArgumentException("air date required");
        // Java's zone rules shift gap times forward and choose the earlier offset on overlaps.
        return LocalDateTime.of(airDate, airTime).atZone(zone).toInstant();
    }
    public boolean isDue(LocalDate airDate, Instant now) {
        if (now == null) throw new IllegalArgumentException("current time required");
        return !now.isBefore(scheduledAt(airDate));
    }
}
