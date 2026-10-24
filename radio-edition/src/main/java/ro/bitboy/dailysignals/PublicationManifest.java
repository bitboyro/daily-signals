package ro.bitboy.dailysignals;

/** Shared v1 publication contract consumed by the Rust streaming boundary. */
public record PublicationManifest(int schemaVersion, String editionId, String status,
                                  String audioPath, String contentType, long sizeBytes, String checksumSha256) {
    public PublicationManifest {
        if (schemaVersion != 1 || !"PUBLISHED".equals(status) || editionId == null || editionId.isBlank())
            throw new IllegalArgumentException("unpublished or unsupported manifest");
        if (audioPath == null || audioPath.startsWith("/") || audioPath.contains("..") ||
                !"audio/wav".equals(contentType) || sizeBytes <= 0 || checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("invalid audio metadata");
    }
}
