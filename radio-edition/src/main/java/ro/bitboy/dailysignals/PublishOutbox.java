package ro.bitboy.dailysignals;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** In-memory reference outbox with idempotent publish and SHA-256 evidence. */
public final class PublishOutbox {
    @FunctionalInterface public interface ObjectWriter {
        void put(String key, byte[] bytes) throws IOException, InterruptedException;
    }
    private record ObjectMetadata(long sizeBytes, String checksumSha256) {
        static ObjectMetadata from(byte[] bytes) { return new ObjectMetadata(bytes.length, sha256(bytes)); }
    }
    private record Pending(String editionId, String key, byte[] audio, ObjectMetadata metadata) {}
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final Map<String, Pending> replacements = new LinkedHashMap<>();
    private final Map<String, PublicationManifest> published = new LinkedHashMap<>();
    public record ProducerEvent(String editionId, String action, String objectKey, String checksumSha256) {}
    private final List<ProducerEvent> events = new ArrayList<>();

    public synchronized void stage(String editionId, String key, byte[] audio) {
        if (editionId == null || !editionId.matches("[a-zA-Z0-9-]+") || key == null || !key.matches("[a-zA-Z0-9][a-zA-Z0-9/_.-]*") || key.contains("..") || audio == null || audio.length < 4
                || audio[0] != 'R' || audio[1] != 'I' || audio[2] != 'F' || audio[3] != 'F')
            throw new IllegalArgumentException("valid edition id and WAV required");
        if (pending.containsKey(editionId) || published.containsKey(editionId))
            throw new IllegalArgumentException("edition already staged");
        var copy = audio.clone();
        pending.put(editionId, new Pending(editionId, key, copy, ObjectMetadata.from(copy)));
    }
    public synchronized PublicationManifest publish(String editionId, String role, ObjectWriter writer) throws IOException, InterruptedException {
        if (!ProducerPermissions.mayPublish(role)) throw new SecurityException("producer role required");
        return publish(editionId, writer);
    }
    public synchronized PublicationManifest publishReplacement(String editionId, String role, ObjectWriter writer)
            throws IOException, InterruptedException {
        if (!ProducerPermissions.mayPublish(role)) throw new SecurityException("producer role required");
        return publishReplacement(editionId, writer);
    }
    private synchronized PublicationManifest publish(String editionId, ObjectWriter writer) throws IOException, InterruptedException {
        if (published.containsKey(editionId)) return published.get(editionId);
        var item = pending.get(editionId);
        if (item == null) throw new IllegalArgumentException("edition not staged");
        // Leave the outbox item pending on transfer failure so a later attempt can retry.
        writer.put(item.key(), item.audio().clone());
        var manifest = new PublicationManifest(1, editionId, "PUBLISHED", item.key(), "audio/wav",
                item.metadata().sizeBytes(), item.metadata().checksumSha256(), null);
        published.put(editionId, manifest);
        events.add(new ProducerEvent(editionId, "published", item.key(), item.metadata().checksumSha256()));
        pending.remove(editionId);
        return manifest;
    }
    public synchronized int pendingCount() { return pending.size(); }
    /** Prepare a new recording revision without changing the published edition yet. */
    public synchronized void stageReplacement(String editionId, String key, byte[] audio) {
        if (!published.containsKey(editionId)) throw new IllegalArgumentException("edition not published");
        if (replacements.containsKey(editionId)) throw new IllegalArgumentException("replacement already staged");
        if (key == null || !key.matches("[a-zA-Z0-9][a-zA-Z0-9/_.-]*") || key.contains("..")
                || audio == null || audio.length < 4 || audio[0] != 'R' || audio[1] != 'I'
                || audio[2] != 'F' || audio[3] != 'F')
            throw new IllegalArgumentException("valid replacement WAV and key required");
        if (key.equals(published.get(editionId).audioPath()))
            throw new IllegalArgumentException("replacement must have a new object key");
        var copy = audio.clone();
        replacements.put(editionId, new Pending(editionId, key, copy, ObjectMetadata.from(copy)));
    }
    public synchronized int replacementCount() { return replacements.size(); }
    /** Transfer a prepared re-render and make it available to the edition. */
    private synchronized PublicationManifest publishReplacement(String editionId, ObjectWriter writer)
            throws IOException, InterruptedException {
        var item = replacements.get(editionId);
        if (item == null) throw new IllegalArgumentException("replacement not staged");
        writer.put(item.key(), item.audio().clone());
        var manifest = new PublicationManifest(1, editionId, "PUBLISHED", item.key(), "audio/wav",
                item.metadata().sizeBytes(), item.metadata().checksumSha256(), null);
        published.put(editionId, manifest);
        events.add(new ProducerEvent(editionId, "recording-transferred", item.key(), item.metadata().checksumSha256()));
        replacements.remove(editionId);
        return manifest;
    }
    public synchronized List<ProducerEvent> producerEvents() { return List.copyOf(events); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
