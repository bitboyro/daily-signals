package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class ScheduledEditionClockTest {
    @Test void resolvesGapOverlapAndIndependentZones() {
        var newYork = new ScheduledEditionClock(ZoneId.of("America/New_York"), LocalTime.of(2, 30));
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), newYork.scheduledAt(LocalDate.of(2026, 3, 8)));
        var overlap = new ScheduledEditionClock(ZoneId.of("America/New_York"), LocalTime.of(1, 30));
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), overlap.scheduledAt(LocalDate.of(2026, 11, 1)));
        var tokyo = new ScheduledEditionClock(ZoneId.of("Asia/Tokyo"), LocalTime.of(8, 30));
        assertEquals(Instant.parse("2026-01-14T23:30:00Z"), tokyo.scheduledAt(LocalDate.of(2026, 1, 15)));
    }
    @Test void schedulesStationLocalMorning() {
        var clock = new ScheduledEditionClock(ZoneId.of("Europe/Bucharest"), LocalTime.of(8, 30));
        var day = LocalDate.of(2026, 1, 15);
        assertEquals(Instant.parse("2026-01-15T06:30:00Z"), clock.scheduledAt(day));
        assertFalse(clock.isDue(day, Instant.parse("2026-01-15T06:29:59Z")));
        assertTrue(clock.isDue(day, Instant.parse("2026-01-15T06:30:00Z")));
    }
    @Test void findsTheNextStationLocalEdition() {
        var clock = new ScheduledEditionClock(ZoneId.of("Europe/Bucharest"), LocalTime.of(8, 30));
        assertEquals(Instant.parse("2026-01-15T06:30:00Z"), clock.nextScheduledAt(Instant.parse("2026-01-15T06:29:59Z")));
        assertEquals(Instant.parse("2026-01-16T06:30:00Z"), clock.nextScheduledAt(Instant.parse("2026-01-15T06:30:00Z")));
    }
}
