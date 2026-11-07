package ro.bitboy.dailysignals.persistence;

import ro.bitboy.dailysignals.StoryNormalizer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Transaction-neutral adapter used by a fetch window to persist every normalized candidate. */
public final class IngestedCandidateWriter {
    private final CandidateRepository repository;

    public IngestedCandidateWriter(CandidateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public int write(Connection connection, List<StoryNormalizer.Story> stories) throws SQLException {
        Objects.requireNonNull(stories, "stories");
        for (var story : stories) repository.save(connection, story);
        return stories.size();
    }
}
