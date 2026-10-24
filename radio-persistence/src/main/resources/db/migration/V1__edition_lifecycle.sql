CREATE TABLE IF NOT EXISTS edition_lifecycle (
    edition_id VARCHAR(120) PRIMARY KEY,
    air_date DATE NOT NULL,
    zone_id VARCHAR(100) NOT NULL,
    source_category VARCHAR(80) NOT NULL DEFAULT 'general',
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'READY', 'PUBLISHED')),
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
