CREATE TABLE IF NOT EXISTS candidate_story (
    candidate_id VARCHAR(120) PRIMARY KEY,
    source_id VARCHAR(80) NOT NULL,
    source_uri TEXT NOT NULL,
    headline TEXT NOT NULL,
    summary TEXT NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_id, source_uri)
);

CREATE TABLE IF NOT EXISTS candidate_review (
    candidate_id VARCHAR(120) PRIMARY KEY REFERENCES candidate_story(candidate_id) ON DELETE CASCADE,
    decision VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (decision IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewer VARCHAR(120),
    reason TEXT,
    reviewed_at TIMESTAMPTZ,
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    CHECK (
        (decision = 'PENDING' AND reviewer IS NULL AND reviewed_at IS NULL)
        OR (decision <> 'PENDING' AND reviewer IS NOT NULL AND reviewed_at IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS edition_candidate (
    edition_id VARCHAR(120) NOT NULL REFERENCES edition_lifecycle(edition_id) ON DELETE CASCADE,
    candidate_id VARCHAR(120) NOT NULL REFERENCES candidate_story(candidate_id),
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (edition_id, candidate_id),
    UNIQUE (edition_id, position)
);

CREATE INDEX IF NOT EXISTS candidate_story_captured_at_idx
    ON candidate_story (captured_at DESC, candidate_id);
CREATE INDEX IF NOT EXISTS candidate_review_decision_idx
    ON candidate_review (decision, candidate_id);
