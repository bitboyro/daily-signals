package ro.bitboy.dailysignals.bridge;

import ro.bitboy.dailysignals.PublicationManifest;

/** Writes v1 manifests for streamer deployments that predate schema version 2. */
public final class StreamerManifestBridge {
    private StreamerManifestBridge() {}

    public static PublicationManifest toV1(PublicationManifest manifest) {
        return new PublicationManifest(1, manifest.editionId(), manifest.status(), manifest.audioPath(),
                manifest.contentType(), manifest.sizeBytes(), manifest.checksumSha256(), null);
    }

    public static String toV1Json(PublicationManifest manifest) {
        var v1 = toV1(manifest);
        return "{\"schemaVersion\":1,\"editionId\":\"" + v1.editionId() + "\",\"status\":\"" + v1.status()
                + "\",\"audioPath\":\"" + v1.audioPath() + "\",\"contentType\":\"" + v1.contentType()
                + "\",\"sizeBytes\":" + v1.sizeBytes() + ",\"checksumSha256\":\"" + v1.checksumSha256() + "\"}";
    }
}
