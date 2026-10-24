-- Historical draft-only schema; V1 must preserve its rows while adding lifecycle fields.
CREATE TABLE edition_lifecycle (
    edition_id VARCHAR(120) PRIMARY KEY,
    air_date DATE NOT NULL,
    zone_id VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'READY', 'PUBLISHED')),
    revision INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO edition_lifecycle (edition_id, air_date, zone_id, status)
VALUES ('legacy-edition', DATE '2026-01-14', 'Europe/Bucharest', 'DRAFT');
