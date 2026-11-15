package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SetupVoiceProviderTest {
    @Test void followsThePersistedSetupChoiceWithoutRestarting() throws Exception {
        var setup = FirstRunSetup.inMemory();
        var fixtureCalls = new AtomicInteger();
        var kokoroCalls = new AtomicInteger();
        VoiceProvider fixture = script -> rendered("fixture", fixtureCalls);
        VoiceProvider kokoro = script -> rendered("kokoro", kokoroCalls);
        var provider = new SetupVoiceProvider(setup, fixture, kokoro);
        var script = new ScriptAssembler.Script("Daily Signals test.", java.util.List.of("story-1"));

        provider.render(script);
        setup.saveJson("""
                {"schemaVersion":1,"configured":true,"sources":[
                  {"id":"desk","name":"Desk","url":"https://news.example/rss","priority":0}
                ],"voice":"kokoro","zone":"UTC","window":"06:00"}
                """);
        provider.render(script);

        assertEquals(1, fixtureCalls.get());
        assertEquals(1, kokoroCalls.get());
    }

    @Test void optionalKokoroConfigurationIsOnlyRequiredWhenSelected() throws Exception {
        var setup = FirstRunSetup.inMemory();
        var provider = SetupVoiceProvider.fromEnvironment(setup, java.util.Map.of());
        var script = new ScriptAssembler.Script("Daily Signals test.", java.util.List.of("story-1"));

        assertEquals("audio/wav", provider.render(script).contentType());
        setup.saveJson("""
                {"schemaVersion":1,"configured":true,"sources":[
                  {"id":"desk","name":"Desk","url":"https://news.example/rss","priority":0}
                ],"voice":"kokoro","zone":"UTC","window":"06:00"}
                """);
        assertThrows(IllegalArgumentException.class, () -> provider.render(script));
    }

    private static VoiceProvider.RenderedAudio rendered(String name, AtomicInteger calls) {
        calls.incrementAndGet();
        return new VoiceProvider.RenderedAudio(new byte[]{'R', 'I', 'F', 'F'}, "audio/wav", name);
    }
}
