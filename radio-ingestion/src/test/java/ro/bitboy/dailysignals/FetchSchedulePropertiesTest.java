package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FetchSchedulePropertiesTest {
    @Test
    void bindsTypedStationLocalScheduleAndOrderedSources() {
        var source = new MapConfigurationPropertySource(Map.of(
                "daily-signals.fetch.zone", "Europe/Bucharest",
                "daily-signals.fetch.window", "06:15",
                "daily-signals.fetch.sources[0]", "public-desk",
                "daily-signals.fetch.sources[1]", "world-wire"));

        var properties = new Binder(source).bind("daily-signals.fetch", FetchScheduleProperties.class)
                .orElseThrow(() -> new AssertionError("schedule properties did not bind"));

        assertEquals(ZoneId.of("Europe/Bucharest"), properties.zone());
        assertEquals(LocalTime.of(6, 15), properties.window());
        assertEquals(java.util.List.of("public-desk", "world-wire"), properties.sources());
    }

    @Test
    void rejectsMissingOrDuplicateSources() {
        assertThrows(IllegalArgumentException.class,
                () -> new FetchScheduleProperties(ZoneId.of("UTC"), LocalTime.NOON, java.util.List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new FetchScheduleProperties(ZoneId.of("UTC"), LocalTime.NOON,
                        java.util.List.of("public-desk", "public-desk")));
    }
}
