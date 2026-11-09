package ro.bitboy.dailysignals;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Applies outbox publication events to the listener manifest; redelivered event IDs are ignored. */
public final class PublicationConsumer {
    public enum EditionStatus { PUBLISHED, RECORDING_REPLACED }

    @FunctionalInterface public interface ManifestSink {
        void write(PublicationManifest manifest) throws IOException;
    }

    private final ManifestSink sink;
    private final Set<String> applied = new HashSet<>();
    private final Map<String, EditionStatus> statuses = new LinkedHashMap<>();

    public PublicationConsumer(ManifestSink sink) { this.sink = sink; }

    /** Store the listener manifest alongside its recording, replacing the WAV suffix with JSON. */
    public static PublicationConsumer forObjectStore(LocalS3ObjectAdapter store) {
        if (store == null) throw new IllegalArgumentException("object store required");
        return new PublicationConsumer(manifest -> storeManifest(store, manifest));
    }

    private static void storeManifest(LocalS3ObjectAdapter store, PublicationManifest manifest) throws IOException {
        try {
            store.putJson(manifestObjectKey(manifest.audioPath()), manifest.toJson());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("manifest transfer interrupted", interrupted);
        }
    }

    public static String manifestObjectKey(String audioPath) {
        if (audioPath == null || !audioPath.endsWith(".wav"))
            throw new IllegalArgumentException("WAV object path required");
        return audioPath.substring(0, audioPath.length() - 4) + ".json";
    }

    /** Returns false for an event that was already applied. A sink failure leaves the event unapplied for retry. */
    public synchronized boolean apply(PublicationEvent event) throws IOException {
        if (applied.contains(event.eventId())) return false;
        var current = statuses.get(event.editionId());
        if (PublicationEvent.PUBLISHED.equals(event.type()) && current != null)
            throw new IllegalStateException("edition already published");
        if (PublicationEvent.RECORDING_REPLACED.equals(event.type()) && current == null)
            throw new IllegalStateException("recording replaced before publication");
        sink.write(event.manifest());
        statuses.put(event.editionId(), PublicationEvent.PUBLISHED.equals(event.type())
                ? EditionStatus.PUBLISHED : EditionStatus.RECORDING_REPLACED);
        applied.add(event.eventId());
        return true;
    }

    public synchronized Optional<EditionStatus> status(String editionId) {
        return Optional.ofNullable(statuses.get(editionId));
    }
}
