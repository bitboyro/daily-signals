# PostgreSQL reference integration

`radio-persistence` owns edition lifecycle schema and JDBC storage. The expected reference database is PostgreSQL. Local unit checks do not silently substitute an in-memory database. Integration tests use `DAILY_SIGNALS_TEST_DATABASE_URL` (for example `jdbc:postgresql://localhost:5432/daily_signals_test`), `DAILY_SIGNALS_TEST_DATABASE_USER`, and `DAILY_SIGNALS_TEST_DATABASE_PASSWORD`. GitHub Actions runs a PostgreSQL service container. A local run without these variables records the DB capability as unavailable, not passed.

Populated-schema promotion and rollback steps are documented in [database migration operations](operations/database-migration.md).
