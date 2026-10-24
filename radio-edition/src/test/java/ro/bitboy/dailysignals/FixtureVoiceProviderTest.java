package ro.bitboy.dailysignals;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FixtureVoiceProviderTest {
    @Test void rendersStableWavContractWithoutNetwork() throws Exception {
        var provider = new FixtureVoiceProvider();
        var script = new ScriptAssembler.Script("A fixed line.\n", List.of("s1"));
        var first = provider.render(script);
        var second = provider.render(script);
        assertEquals("audio/wav", first.contentType());
        assertEquals("RIFF", new String(first.bytes(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertArrayEquals(first.bytes(), second.bytes());
        assertEquals(first.scriptSha256(), second.scriptSha256());
        assertNotEquals(first.scriptSha256(), provider.render(new ScriptAssembler.Script("Another line.\n", List.of("s1"))).scriptSha256());
        first.bytes()[0] = 0;
        assertEquals('R', first.bytes()[0]);
    }
}
