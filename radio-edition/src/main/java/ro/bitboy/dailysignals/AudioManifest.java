package ro.bitboy.dailysignals;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

public record AudioManifest(LocalDate airDate, String zoneId, URI audioUri, List<String> storyIds) {
    public AudioManifest { storyIds=List.copyOf(storyIds); }
    public static AudioManifest fixture(EditionAssembler.Edition edition) {
        if(edition.segments().isEmpty()) throw new IllegalArgumentException("cannot publish an empty edition");
        return new AudioManifest(edition.airDate(),edition.zone().getId(),URI.create("/editions/demo/audio"),
                edition.segments().stream().map(c->c.story().id()).toList());
    }
}
