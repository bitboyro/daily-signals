-- A feed produces many candidates. Keep source history searchable without making the feed URL unique.
ALTER TABLE candidate_story
    DROP CONSTRAINT IF EXISTS candidate_story_source_id_source_uri_key;
CREATE INDEX IF NOT EXISTS candidate_story_source_history_idx
    ON candidate_story (source_id, captured_at DESC, candidate_id);
