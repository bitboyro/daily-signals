package ro.bitboy.dailysignals;

/** Shared publication contract consumed by the Rust streaming boundary; v2 adds the edition air date. */
public record PublicationManifest(int schemaVersion, String editionId, String status,
                                  String audioPath, String contentType, long sizeBytes, String checksumSha256,
                                  String airDate) {
    public PublicationManifest {
        if ((schemaVersion != 1 && schemaVersion != 2) || !"PUBLISHED".equals(status) || editionId == null || editionId.isBlank())
            throw new IllegalArgumentException("unpublished or unsupported manifest");
        if (schemaVersion == 1 ? airDate != null : airDate == null || !airDate.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw new IllegalArgumentException("air date belongs to schema version 2");
        if (audioPath == null || audioPath.startsWith("/") || audioPath.contains("..") ||
                !"audio/wav".equals(contentType) || sizeBytes <= 0 || checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("invalid audio metadata");
    }
}
