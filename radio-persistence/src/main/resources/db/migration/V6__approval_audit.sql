CREATE TABLE IF NOT EXISTS edition_approval_event (
    event_id BIGSERIAL PRIMARY KEY,
    edition_id VARCHAR(120) NOT NULL REFERENCES edition_lifecycle(edition_id) ON DELETE CASCADE,
    edition_revision INTEGER NOT NULL CHECK (edition_revision > 0),
    actor VARCHAR(200) NOT NULL CHECK (length(trim(actor)) > 0),
    approved_at TIMESTAMPTZ NOT NULL,
    UNIQUE (edition_id, edition_revision)
);

CREATE INDEX IF NOT EXISTS edition_approval_event_history_idx
    ON edition_approval_event (edition_id, approved_at, event_id);
