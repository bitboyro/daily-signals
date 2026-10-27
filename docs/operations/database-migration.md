# Database migration and rollback

Daily Signals applies `V1__edition_lifecycle.sql` followed by `V2__source_category_backfill.sql`. V2 is safe for both fresh databases and populated pre-E7 schemas: it adds the category as nullable, backfills existing rows to `general`, then installs the default and `NOT NULL` constraint.

## Before promotion

Take a database-native snapshot and record its identifier with the application source SHA. Run the `migration-populated` check against an isolated PostgreSQL database. It must preserve draft, ready and published rows, revision values and row count. Do not infer production compatibility from an empty-schema migration.

## Transactional rollout

Place the application in write-drain mode, begin a transaction, apply the migrations, verify the row count and null count, then commit. Deploy application processes only after the database checks pass. Resume writes after the API and streamer health probes succeed.

## Rollback

Before commit, roll back the migration transaction; PostgreSQL restores the prior table shape and populated rows. After commit, do not drop `source_category` while newer application instances may be running. Roll back the application set first, restore the recorded database snapshot if required, and run the previous version's health checks. Record the failed migration, source SHA, snapshot identifier, row counts and operator decision in the incident note.

The migration tests use unique temporary schemas and remove them after verification. Never point these fixtures at a production schema.
