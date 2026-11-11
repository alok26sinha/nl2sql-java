# Contributing to nl2sql

Thanks for helping. Bug reports, prompt examples, docs fixes and code are all welcome.

## Before you start

- For anything bigger than a small fix, open an issue first so we can agree on the approach.
- Issues labelled `good first issue` are a good place to begin.

## Build and test

You need Java 21 and Maven.

```bash
mvn verify
```

Unit tests and the SQLite tests always run. To test another database, set `NL2SQL_TEST_<DB>_URL`
(for example `NL2SQL_TEST_POSTGRES_URL`) to a JDBC URL for a disposable database.

## Pull requests

- One topic per PR; keep it small and focused.
- Add or update tests for behaviour changes. Guardrail changes always need tests showing what is refused.
- Do not weaken the guardrails (single SELECT, known tables/columns, blocked columns, read-only transactions,
  row limit, timeout) without discussing it in an issue first.
- Never commit real credentials, API keys or customer data. Use SQLite test data (see `EngineSqliteTest`).
- Update the README if you add a setting or an endpoint.

## Reporting bugs

Include the database type and version, the LLM provider and model, the question, the generated SQL (from the
response) and the error. Rename confidential table or column names if you need to.

## Prompt examples

Good question → SQL pairs make the engine better. Please submit them with the database dialect and a short
description of the schema.

## License

By contributing you agree that your contributions are licensed under Apache-2.0.
