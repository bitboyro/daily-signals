package ro.bitboy.dailysignals;

import java.io.IOException;
import java.io.InputStream;

/** Enforces operator role and registered-source identity around untrusted parser input. */
public final class AuthorizedSourceParser {
    private final SourceRegistry registry;
    private final SourceSnapshotParser parser;

    public AuthorizedSourceParser(SourceRegistry registry) {
        this.registry = registry;
        this.parser = new SourceSnapshotParser();
    }

    public SourceSnapshotParser.Snapshot parse(String role, InputStream input) throws IOException {
        if (!"PRODUCER".equals(role) && !"EDITOR".equals(role)) {
            throw new SecurityException("source parsing requires producer or editor role");
        }
        var snapshot = parser.parse(input);
        var source = registry.get(snapshot.sourceId());
        if (source == null || !source.name().equals(snapshot.sourceName()) || !source.url().toString().equals(snapshot.sourceUrl())) {
            throw new SecurityException("snapshot source is not registered with matching identity");
        }
        return snapshot;
    }
}
