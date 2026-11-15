package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstRunSetupTest {
    @TempDir Path directory;

    @Test void persistsSourcesVoiceAndScheduleAndRestoresThem() {
        Path file = directory.resolve("setup.json");
        var setup = new FirstRunSetup(file);
        assertFalse(setup.configured());
        String saved = setup.saveJson("""
                {"schemaVersion":1,"configured":true,"sources":[
                  {"id":"world-wire","name":"World wire","url":"https://news.example/world.xml","priority":2}
                ],"voice":"kokoro","zone":"Europe/Bucharest","window":"06:15"}
                """);
        assertTrue(saved.contains("\"voice\":\"kokoro\""));
        var restored = new FirstRunSetup(file);
        assertTrue(restored.configured());
        String schedule = restored.scheduleJson(Instant.parse("2026-11-15T04:00:00Z"),
                FetchScheduleView.defaults(Instant.parse("2026-11-15T04:00:00Z")));
        assertTrue(schedule.contains("\"zone\":\"Europe/Bucharest\""));
        assertTrue(schedule.contains("\"sources\":[\"world-wire\"]"));
    }

    @Test void rejectsUnsafeOrIncompleteSetup() {
        var setup = FirstRunSetup.inMemory();
        assertThrows(IllegalArgumentException.class, () -> setup.saveJson("{}"));
        assertThrows(IllegalArgumentException.class, () -> setup.saveJson("""
                {"schemaVersion":1,"configured":true,"sources":[
                  {"id":"desk","name":"Desk","url":"http://insecure.example/rss","priority":0}
                ],"voice":"fixture","zone":"UTC","window":"06:00"}
                """));
        assertEquals(1, FirstRunSetup.fromEnvironment(java.util.Map.of()).toJson().contains("public-desk") ? 1 : 0);
    }
}
