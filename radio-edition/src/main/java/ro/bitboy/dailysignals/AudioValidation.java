package ro.bitboy.dailysignals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal WAV validation shared by producer previews and publication checks. */
public record AudioValidation(boolean playable, int sampleRate, int channels, int bitsPerSample, long durationMillis) {
    public static AudioValidation inspect(byte[] audio) {
        if (audio == null || audio.length < 44
                || !"RIFF".equals(new String(audio, 0, 4, StandardCharsets.US_ASCII))
                || !"WAVE".equals(new String(audio, 8, 4, StandardCharsets.US_ASCII))) {
            return new AudioValidation(false, 0, 0, 0, 0);
        }
        var wav = ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN);
        int channels = Short.toUnsignedInt(wav.getShort(22));
        int sampleRate = wav.getInt(24);
        int bits = Short.toUnsignedInt(wav.getShort(34));
        int dataBytes = wav.getInt(40);
        long bytesPerSecond = (long) sampleRate * channels * bits / 8;
        boolean playable = channels > 0 && channels <= 2 && sampleRate >= 8_000 && sampleRate <= 192_000
                && (bits == 8 || bits == 16 || bits == 24 || bits == 32)
                && dataBytes > 0 && dataBytes <= audio.length - 44 && bytesPerSecond > 0;
        return new AudioValidation(playable, sampleRate, channels, bits,
                playable ? dataBytes * 1_000L / bytesPerSecond : 0);
    }
}
