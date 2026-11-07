package ro.bitboy.dailysignals.persistence;

import ro.bitboy.dailysignals.StoryNormalizer;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** Stores normalized ingestion candidates without discarding their source attribution. */
public final class CandidateRepository {
    public record StoredCandidate(String id, String sourceId, URI sourceUri, String headline, String summary,
                                  Instant capturedAt) {}

    public void save(Connection connection, StoryNormalizer.Story story) throws SQLException {
        if (connection == null || story == null || story.attribution() == null) {
            throw new IllegalArgumentException("connection, story and attribution are required");
        }
        try (var statement = connection.prepareStatement("""
                INSERT INTO candidate_story
                    (candidate_id, source_id, source_uri, headline, summary, captured_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (candidate_id) DO UPDATE SET
                    source_id = EXCLUDED.source_id,
                    source_uri = EXCLUDED.source_uri,
                    headline = EXCLUDED.headline,
                    summary = EXCLUDED.summary,
                    captured_at = EXCLUDED.captured_at
                """)) {
            statement.setString(1, story.id());
            statement.setString(2, story.attribution().sourceId());
            statement.setString(3, story.attribution().sourceUrl().toString());
            statement.setString(4, story.title());
            statement.setString(5, story.summary());
            statement.setTimestamp(6, java.sql.Timestamp.from(story.publishedAt()));
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
                INSERT INTO candidate_review (candidate_id) VALUES (?)
                ON CONFLICT (candidate_id) DO NOTHING
                """)) {
            statement.setString(1, story.id());
            statement.executeUpdate();
        }
    }

    public Optional<StoredCandidate> get(Connection connection, String id) throws SQLException {
        if (connection == null || id == null || id.isBlank()) {
            throw new IllegalArgumentException("connection and candidate id are required");
        }
        try (var statement = connection.prepareStatement("""
                SELECT candidate_id, source_id, source_uri, headline, summary, captured_at
                FROM candidate_story WHERE candidate_id = ?
                """)) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new StoredCandidate(rows.getString(1), rows.getString(2), URI.create(rows.getString(3)),
                        rows.getString(4), rows.getString(5), rows.getTimestamp(6).toInstant()));
            }
        }
    }
}
