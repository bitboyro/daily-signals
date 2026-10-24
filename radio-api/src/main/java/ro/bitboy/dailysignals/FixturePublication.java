package ro.bitboy.dailysignals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Creates one synthetic, offline edition for the cross-language system check. */
public final class FixturePublication {
    private static final String AIR_DATE = "2026-01-15";

    private FixturePublication() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("usage: FixturePublication OUTPUT_DIRECTORY");
        Path root = Path.of(args[0]);
        var script = new ScriptAssembler.Script("Daily Signals. Synthetic news fixture.\n", List.of("fixture-1"));
        var audio = new FixtureVoiceProvider().render(script);
        var outbox = new PublishOutbox();
        outbox.stage("fixture-edition", "editions/fixture-edition.wav", audio.bytes());
        outbox.publish("fixture-edition", "PRODUCER", (key, bytes) -> {
            var target = root.resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        });
        // Listeners see the edition only once the consumer has applied its publication event.
        var consumer = new PublicationConsumer(manifest ->
                Files.writeString(root.resolve("manifest.json"), manifest.withAirDate(AIR_DATE).toJson()));
        for (var event : outbox.drainEvents()) {
            if (consumer.apply(event)) {
                Files.writeString(root.resolve("events.jsonl"), event.toJson() + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        }
    }
}
