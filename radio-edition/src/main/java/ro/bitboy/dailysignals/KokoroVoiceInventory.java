package ro.bitboy.dailysignals;

import java.util.Set;

/** Reviewed voice inventory shipped with the pinned Kokoro model revision. */
public final class KokoroVoiceInventory {
    private static final Set<String> PINNED = Set.of("af_heart", "am_adam", "bf_emma");
    private KokoroVoiceInventory() {}

    public static Set<String> pinnedVoices() { return PINNED; }

    public static void requireSupported(String voiceId) {
        if (!PINNED.contains(voiceId)) throw new IllegalArgumentException("unsupported Kokoro voice: " + voiceId);
    }
}
