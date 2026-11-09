CREATE TABLE IF NOT EXISTS pronunciation_lexicon (
    term VARCHAR(64) PRIMARY KEY,
    spoken_form VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (length(trim(term)) > 0),
    CHECK (length(trim(spoken_form)) > 0)
);

CREATE TABLE IF NOT EXISTS pronunciation_lexicon_state (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0)
);

INSERT INTO pronunciation_lexicon_state(singleton, revision)
VALUES (TRUE, 0)
ON CONFLICT (singleton) DO NOTHING;

INSERT INTO pronunciation_lexicon(term, spoken_form)
VALUES ('AI', 'A I'), ('S3', 'S three')
ON CONFLICT (term) DO NOTHING;
