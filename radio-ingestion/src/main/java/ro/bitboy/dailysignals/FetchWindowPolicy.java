package ro.bitboy.dailysignals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Deterministic admission of pinned snapshots to an edition's fetch window. */
public record FetchWindowPolicy(Duration maximumAge, Duration futureTolerance) {
    public FetchWindowPolicy {
        if (maximumAge == null || maximumAge.isNegative() || maximumAge.isZero()
                || futureTolerance == null || futureTolerance.isNegative())
            throw new IllegalArgumentException("invalid fetch window");
    }
    public boolean accepts(Instant capturedAt, Instant decisionAt) {
        if (capturedAt == null || decisionAt == null) throw new IllegalArgumentException("timestamps required");
        return !capturedAt.isBefore(decisionAt.minus(maximumAge))
                && !capturedAt.isAfter(decisionAt.plus(futureTolerance));
    }
    public List<SourceSnapshotParser.Snapshot> eligible(List<SourceSnapshotParser.Snapshot> snapshots, Instant decisionAt) {
        return snapshots.stream().filter(snapshot -> accepts(snapshot.capturedAt(), decisionAt)).toList();
    }
}
