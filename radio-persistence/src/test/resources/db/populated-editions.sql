-- Pre-E7 production-shaped snapshot: populated lifecycle rows before source-category backfill.
CREATE TABLE edition_lifecycle (
    edition_id VARCHAR(120) PRIMARY KEY,
    air_date DATE NOT NULL,
    zone_id VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'READY', 'PUBLISHED')),
    revision INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO edition_lifecycle (edition_id, air_date, zone_id, status, revision) VALUES
('archive-2026-01-13', DATE '2026-01-13', 'Europe/Bucharest', 'PUBLISHED', 2),
('morning-2026-01-14', DATE '2026-01-14', 'Europe/Bucharest', 'READY', 1),
('draft-2026-01-15', DATE '2026-01-15', 'Europe/Bucharest', 'DRAFT', 0);
