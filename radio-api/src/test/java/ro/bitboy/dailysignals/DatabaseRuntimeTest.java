package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseRuntimeTest {
    @Test void remainsOptionalForFixtureAndContainerRuns() {
        assertTrue(DatabaseRuntime.fromEnvironment(Map.of()).isEmpty());
    }

    @Test void requiresACompleteDatabaseConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseRuntime.fromEnvironment(Map.of(
                "DAILY_SIGNALS_DATABASE_URL", "jdbc:postgresql://db/daily_signals")));
        var runtime = DatabaseRuntime.fromEnvironment(Map.of(
                "DAILY_SIGNALS_DATABASE_URL", "jdbc:postgresql://db/daily_signals",
                "DAILY_SIGNALS_DATABASE_USER", "radio",
                "DAILY_SIGNALS_DATABASE_PASSWORD", "secret")).orElseThrow();
        assertEquals("radio", runtime.user());
    }
}
