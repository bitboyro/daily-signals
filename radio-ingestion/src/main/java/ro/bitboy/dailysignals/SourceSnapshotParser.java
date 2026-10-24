package ro.bitboy.dailysignals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SourceSnapshotParser {
    public record RawStory(String id, String title, String summary, Instant publishedAt, String category) {}
    public record Snapshot(String snapshotId, Instant capturedAt, String sourceId, String sourceName,
                           String sourceUrl, List<RawStory> stories) {}
    private final ObjectMapper mapper = new ObjectMapper();

    public Snapshot parse(InputStream input) throws IOException {
        JsonNode root = mapper.readTree(input);
        if (root == null || !"synthetic-test-content".equals(required(root,"license"))) {
            throw new IllegalArgumentException("only licensed synthetic fixtures are accepted in the pilot");
        }
        JsonNode source = root.path("source");
        List<RawStory> stories = new ArrayList<>();
        if (!root.path("stories").isArray()) throw new IllegalArgumentException("stories must be an array");
        for (JsonNode item : root.path("stories")) {
            stories.add(new RawStory(required(item,"id"),required(item,"title"),required(item,"summary"),
                    Instant.parse(required(item,"publishedAt")), item.path("category").asText("general")));
        }
        return new Snapshot(required(root,"snapshotId"),Instant.parse(required(root,"capturedAt")),
                required(source,"id"),required(source,"name"),required(source,"url"),List.copyOf(stories));
    }
    private static String required(JsonNode node, String field) {
        String value=node.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("missing "+field);
        return value;
    }
}
