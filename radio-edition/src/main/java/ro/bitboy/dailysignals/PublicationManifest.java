package ro.bitboy.dailysignals;

import java.util.List;

/** Shared publication contract consumed by the Rust streaming boundary; v2 adds the edition air date. */
public record PublicationManifest(int schemaVersion, String editionId, String status,
                                  String audioPath, String contentType, long sizeBytes, String checksumSha256,
                                  String airDate, List<Chapter> chapters) {
    public record Chapter(String title, long startMillis) {
        public Chapter {
            if (title == null || title.isBlank() || title.length() > 160 || startMillis < 0)
                throw new IllegalArgumentException("invalid chapter");
        }
    }

    public PublicationManifest {
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
        if ((schemaVersion < 1 || schemaVersion > 3) || !"PUBLISHED".equals(status) || editionId == null || !editionId.matches("[a-zA-Z0-9-]+"))
            throw new IllegalArgumentException("unpublished or unsupported manifest");
        if (schemaVersion == 1 ? airDate != null : airDate == null || !airDate.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw new IllegalArgumentException("air date belongs to schema version 2 or later");
        if (schemaVersion < 3 && !chapters.isEmpty() || schemaVersion == 3 && chapters.isEmpty())
            throw new IllegalArgumentException("chapters belong to schema version 3");
        long previous = -1;
        for (var chapter : chapters) {
            if (chapter.startMillis() <= previous) throw new IllegalArgumentException("chapters must be ordered");
            previous = chapter.startMillis();
        }
        if (audioPath == null || !audioPath.matches("[a-zA-Z0-9][a-zA-Z0-9/_.-]*") || audioPath.contains("..") ||
                !"audio/wav".equals(contentType) || sizeBytes <= 0 || checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("invalid audio metadata");
    }

    public PublicationManifest upgradeToV2(String date) {
        if (schemaVersion != 1) {
            throw new IllegalStateException("only schema version 1 manifests can be upgraded");
        }
        return new PublicationManifest(2, editionId, status, audioPath, contentType, sizeBytes, checksumSha256, date, List.of());
    }

    /** Field values are validated above, so no JSON escaping is needed. */
    public String toJson() {
        return "{\"schemaVersion\":" + schemaVersion + ",\"editionId\":\"" + editionId + "\""
                + (airDate == null ? "" : ",\"airDate\":\"" + airDate + "\"")
                + ",\"status\":\"" + status + "\",\"audioPath\":\"" + audioPath + "\",\"contentType\":\"" + contentType
                + "\",\"sizeBytes\":" + sizeBytes + ",\"checksumSha256\":\"" + checksumSha256 + "\""
                + (schemaVersion < 3 ? "" : ",\"chapters\":[" + chapters.stream()
                        .map(chapter -> "{\"title\":\"" + quote(chapter.title()) + "\",\"startMillis\":" + chapter.startMillis() + "}")
                        .reduce((left, right) -> left + "," + right).orElse("") + "]") + "}";
    }

    private static String quote(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
