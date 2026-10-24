package ro.bitboy.dailysignals;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Envelope for publication changes crossing from the outbox to manifest consumers. */
public record PublicationEvent(int schemaVersion, String eventId, String type, String editionId,
                               String occurredAt, PublicationManifest manifest) {
    public static final String PUBLISHED = "edition.published";
    public static final String RECORDING_REPLACED = "edition.recording-replaced";

    public PublicationEvent {
        if (schemaVersion != 1 || eventId == null || !eventId.matches("[a-zA-Z0-9:._-]+"))
            throw new IllegalArgumentException("unsupported event envelope");
        if (!PUBLISHED.equals(type) && !RECORDING_REPLACED.equals(type))
            throw new IllegalArgumentException("unknown publication event type");
        if (manifest == null || !manifest.editionId().equals(editionId))
            throw new IllegalArgumentException("event manifest belongs to another edition");
        try {
            Instant.parse(occurredAt);
        } catch (DateTimeParseException | NullPointerException invalid) {
            throw new IllegalArgumentException("occurredAt must be an ISO-8601 instant");
        }
    }

    public String toJson() {
        return "{\"schemaVersion\":1,\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\",\"editionId\":\""
                + editionId + "\",\"occurredAt\":\"" + occurredAt + "\",\"manifest\":" + manifest.toJson() + "}";
    }
}
