package ro.bitboy.dailysignals;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory reference outbox with idempotent publish and SHA-256 evidence. */
public final class PublishOutbox {
    @FunctionalInterface public interface ObjectWriter {
        void put(String key, byte[] bytes) throws IOException, InterruptedException;
    }
    private record Pending(String editionId, String key, byte[] audio, String checksum) {}
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final Map<String, PublicationManifest> published = new LinkedHashMap<>();

    public synchronized void stage(String editionId, String key, byte[] audio) {
        if (editionId == null || !editionId.matches("[a-zA-Z0-9-]+") || key == null || !key.matches("[a-zA-Z0-9][a-zA-Z0-9/_.-]*") || key.contains("..") || audio == null || audio.length < 4
                || audio[0] != 'R' || audio[1] != 'I' || audio[2] != 'F' || audio[3] != 'F')
            throw new IllegalArgumentException("valid edition id and WAV required");
        if (pending.containsKey(editionId) || published.containsKey(editionId))
            throw new IllegalArgumentException("edition already staged");
        var copy = audio.clone();
        pending.put(editionId, new Pending(editionId, key, copy, sha256(copy)));
    }
    public synchronized PublicationManifest publish(String editionId, ObjectWriter writer) throws IOException, InterruptedException {
        if (published.containsKey(editionId)) return published.get(editionId);
        var item = pending.get(editionId);
        if (item == null) throw new IllegalArgumentException("edition not staged");
        // Leave the outbox item pending on transfer failure so a later attempt can retry.
        writer.put(item.key(), item.audio().clone());
        var manifest = new PublicationManifest(1, editionId, "PUBLISHED", item.key(), "audio/wav",
                item.audio().length, item.checksum());
        published.put(editionId, manifest);
        pending.remove(editionId);
        return manifest;
    }
    public synchronized int pendingCount() { return pending.size(); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
