ALTER TABLE edition_lifecycle
    ADD COLUMN IF NOT EXISTS source_category VARCHAR(80);
UPDATE edition_lifecycle
SET source_category = 'general'
WHERE source_category IS NULL;
ALTER TABLE edition_lifecycle
    ALTER COLUMN source_category SET DEFAULT 'general';
ALTER TABLE edition_lifecycle
    ALTER COLUMN source_category SET NOT NULL;
