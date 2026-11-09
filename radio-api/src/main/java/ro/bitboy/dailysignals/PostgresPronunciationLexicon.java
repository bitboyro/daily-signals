package ro.bitboy.dailysignals;

import ro.bitboy.dailysignals.persistence.PronunciationRepository;

import java.sql.SQLException;

/** PostgreSQL-backed pronunciation lexicon used when the API has database configuration. */
final class PostgresPronunciationLexicon implements PronunciationLexiconBackend {
    private final DatabaseRuntime database;
    private final PronunciationRepository pronunciations = new PronunciationRepository();

    PostgresPronunciationLexicon(DatabaseRuntime database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override public String toJson() {
        try (var connection = database.open()) {
            var snapshot = pronunciations.load(connection);
            return PronunciationLexiconStore.json(snapshot.entries(), snapshot.revision());
        } catch (SQLException failure) {
            throw unavailable(failure);
        }
    }

    @Override public String saveJson(String text) {
        var entries = PronunciationLexiconStore.parse(text);
        try (var connection = database.open()) {
            var snapshot = pronunciations.replace(connection, entries);
            return PronunciationLexiconStore.json(snapshot.entries(), snapshot.revision());
        } catch (SQLException failure) {
            throw unavailable(failure);
        }
    }

    @Override public String speakable(String text) {
        try (var connection = database.open()) {
            return new PronunciationDictionary(pronunciations.load(connection).entries()).speakable(text);
        } catch (SQLException failure) {
            throw unavailable(failure);
        }
    }

    private static IllegalStateException unavailable(SQLException failure) {
        return new IllegalStateException("pronunciation database unavailable", failure);
    }
}
