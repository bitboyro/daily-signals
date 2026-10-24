package ro.bitboy.dailysignals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Creates one synthetic, offline edition for the cross-language system check. */
public final class FixturePublication {
    private FixturePublication() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("usage: FixturePublication OUTPUT_DIRECTORY");
        Path root = Path.of(args[0]);
        var script = new ScriptAssembler.Script("Daily Signals. Synthetic news fixture.\n", List.of("fixture-1"));
        var audio = new FixtureVoiceProvider().render(script);
        var outbox = new PublishOutbox();
        outbox.stage("fixture-edition", "editions/fixture-edition.wav", audio.bytes());
        var manifest = outbox.publish("fixture-edition", "PRODUCER", (key, bytes) -> {
            var target = root.resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        });
        String json = "{\"schemaVersion\":2,\"editionId\":\"fixture-edition\",\"airDate\":\"2026-01-15\","
                + "\"status\":\"PUBLISHED\",\"audioPath\":\"" + manifest.audioPath() + "\","
                + "\"contentType\":\"audio/wav\",\"sizeBytes\":" + manifest.sizeBytes() + ","
                + "\"checksumSha256\":\"" + manifest.checksumSha256() + "\"}";
        Files.writeString(root.resolve("manifest.json"), json);
    }
}
