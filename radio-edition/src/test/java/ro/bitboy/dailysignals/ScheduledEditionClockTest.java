package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class ScheduledEditionClockTest {
    @Test void schedulesStationLocalMorning() {
        var clock = new ScheduledEditionClock(ZoneId.of("Europe/Bucharest"), LocalTime.of(8, 30));
        var day = LocalDate.of(2026, 1, 15);
        assertEquals(Instant.parse("2026-01-15T06:30:00Z"), clock.scheduledAt(day));
        assertFalse(clock.isDue(day, Instant.parse("2026-01-15T06:29:59Z")));
        assertTrue(clock.isDue(day, Instant.parse("2026-01-15T06:30:00Z")));
    }
}
