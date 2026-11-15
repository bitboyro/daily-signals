package ro.bitboy.dailysignals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Copies mounted v0.x WAV recordings into the object store without overwriting different bytes. */
public final class V0RecordingImporter {
    public static final String RECORDINGS = "recordings";
    private static final long MAX_RECORDING_BYTES = 500_000_000L;

    public record Result(int imported, int alreadyPresent) {}

    public Result importFrom(Path stateDirectory, LocalS3ObjectAdapter store)
            throws IOException, InterruptedException {
        if (stateDirectory == null || store == null
                || !Files.isDirectory(stateDirectory, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("a v0.x state directory and object store are required");
        Path recordings = stateDirectory.resolve(RECORDINGS);
        if (!Files.exists(recordings, LinkOption.NOFOLLOW_LINKS)) return new Result(0, 0);
        if (!Files.isDirectory(recordings, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("v0.x recordings must be a directory");

        int imported = 0;
        int alreadyPresent = 0;
        try (var files = Files.list(recordings)) {
            for (Path recording : files.sorted().toList()) {
                String filename = recording.getFileName().toString();
                if (!filename.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]*\\.wav")
                        || !Files.isRegularFile(recording, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(recording) == 0 || Files.size(recording) > MAX_RECORDING_BYTES)
                    throw new IllegalArgumentException("v0.x recording must be a nonempty regular WAV file");
                byte[] source = Files.readAllBytes(recording);
                String sourceChecksum = sha256(source);
                String key = "editions/" + filename;
                var existing = store.getIfPresent(key);
                if (existing.isPresent()) {
                    if (!sourceChecksum.equals(sha256(existing.orElseThrow())))
                        throw new IOException("object collision for " + key);
                    alreadyPresent++;
                    continue;
                }
                store.put(key, source, "audio/wav");
                if (!sourceChecksum.equals(sha256(store.get(key))))
                    throw new IOException("checksum verification failed for " + key);
                imported++;
            }
        }
        return new Result(imported, alreadyPresent);
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
