package ro.bitboy.dailysignals;

/** Machine-readable provenance recorded beside generated audio. */
public record GeneratedAudioProvenance(
        int schemaVersion,
        String provider,
        String modelRevision,
        String modelSha256,
        String voiceId,
        String scriptSha256,
        String audioSha256) {
    public GeneratedAudioProvenance {
        if (schemaVersion != 1 || !"kokoro-local".equals(provider)
                || modelRevision == null || modelRevision.isBlank()
                || modelSha256 == null || !modelSha256.matches("[0-9a-f]{64}")
                || voiceId == null || voiceId.isBlank()
                || scriptSha256 == null || !scriptSha256.matches("[0-9a-f]{64}")
                || audioSha256 == null || !audioSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("complete generated-audio provenance required");
    }

    public String toJson() {
        return "{\"schemaVersion\":" + schemaVersion + ",\"provider\":\"" + provider
                + "\",\"modelRevision\":\"" + modelRevision + "\",\"modelSha256\":\"" + modelSha256
                + "\",\"voiceId\":\"" + voiceId + "\",\"scriptSha256\":\"" + scriptSha256
                + "\",\"audioSha256\":\"" + audioSha256 + "\"}";
    }
}
