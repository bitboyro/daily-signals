package ro.bitboy.dailysignals;

import java.io.IOException;

/** Boundary for rendering a script; fixture output is an audio contract, not live narration. */
public interface VoiceProvider {
    record RenderedAudio(byte[] bytes, String contentType, String scriptSha256) {
        public RenderedAudio { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    RenderedAudio render(ScriptAssembler.Script script) throws IOException;
}
