# nl2sql: ask your relational database questions in plain English

nl2sql turns a question like *"Which 5 carriers had the most late deliveries last quarter?"* into correct SQL
for your real schema, checks it against strict guardrails, runs it read-only and returns the rows.
Use it as a Java library, a REST server, or a free Windows desktop app.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.alok26sinha/nl2sql-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.alok26sinha/nl2sql-core)
[![CI](https://github.com/alok26sinha/nl2sql-java/actions/workflows/ci.yml/badge.svg)](https://github.com/alok26sinha/nl2sql-java/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/alok26sinha/nl2sql-java)](LICENSE)
![Java 21](https://img.shields.io/badge/Java-21-blue)

<!-- DEMO GIF GOES HERE: docs/demo.gif (question typed, SQL shown, result table, then a follow-up question) -->
<!-- ![nl2sql demo](docs/demo.gif) -->

- **Databases:** MySQL, MariaDB, PostgreSQL, SQL Server, Oracle, SQLite (drivers bundled in the server)
- **LLMs:** OpenAI, Azure OpenAI, Anthropic, Groq, Ollama, or any OpenAI-compatible endpoint
- **Real SQL:** joins, aggregations, CTEs, date logic, the right dialect. If the database rejects the query, the
  model fixes it and retries.
- **Guardrails:** a single SELECT only, known tables and columns only, blocked columns, read-only transactions,
  a row limit and a statement timeout
- **Privacy:** only the schema (table and column names, plus a few sample values of code-like columns) goes to the
  LLM, never your result rows
- **Follow-up questions:** pass earlier turns and ask "now only for Mumbai"

**Who it's for:** business users who want their own KPIs and ad-hoc reports without waiting for a developer;
product teams adding an "ask your data" box; analysts who want a fast first draft of a complex query.

## 60-second quick start

### Desktop app (Windows, no code)

1. Download the installer from [nl2sql-desktop releases](https://github.com/alok26sinha/nl2sql-desktop/releases/latest).
2. Enter your database (type, host, name, user, password; a read-only user is recommended) and your AI model
   (endpoint URL, model name, API key).
3. Type a question.

### Java library

```xml
<dependency>
  <groupId>io.github.alok26sinha</groupId>
  <artifactId>nl2sql-core</artifactId>
  <version>0.1.0</version>
</dependency>
<!-- plus the JDBC driver for your database -->
```

```java
Nl2SqlSettings s = new Nl2SqlSettings();
s.getDb().setType("postgres");
s.getDb().setHost("localhost");
s.getDb().setName("sales");
s.getDb().setUsername("readonly");
s.getDb().setPassword(System.getenv("DB_PASSWORD"));
s.getLlm().setModel("gpt-4.1-mini");
s.getLlm().setApiKey(System.getenv("LLM_API_KEY"));

try (Nl2SqlEngine engine = Nl2SqlEngine.create(s)) {
    QueryResult r = engine.ask("Top 10 customers by revenue this year", List.of());
    System.out.println(r.sql());
    r.rows().forEach(System.out::println);
}
```

Java 21, Apache-2.0. The core library has no Spring dependency. More in
[Embedding the library](#embedding-the-library-in-your-application).

### REST server

```bash
export NL2SQL_DB_TYPE=mysql NL2SQL_DB_HOST=localhost NL2SQL_DB_NAME=shop NL2SQL_DB_USERNAME=ro NL2SQL_DB_PASSWORD='...'
export NL2SQL_LLM_MODEL=gpt-4.1-mini NL2SQL_LLM_API_KEY='sk-...'
java -jar nl2sql-0.1.0.jar      # from GitHub Releases
curl -s localhost:8080/v1/ask -H 'Content-Type: application/json' -d '{"question":"How many orders shipped late last week?"}'
```

Endpoints: `/v1/ask`, `/v1/generate`, `/v1/schema`, `/v1/info`, `/v1/examples`. Set `NL2SQL_SERVER_API_KEY` to
require the `X-API-Key` header. Full setup below.

## REST server: full setup

1. Download `nl2sql-<version>.jar` from [Releases](https://github.com/alok26sinha/nl2sql-java/releases) (needs Java 21+).
2. Configure it with environment variables...

   ```bash
   export NL2SQL_DB_TYPE=mysql NL2SQL_DB_HOST=localhost NL2SQL_DB_NAME=tms
   export NL2SQL_DB_USERNAME=nl2sql_reader NL2SQL_DB_PASSWORD='...'
   export NL2SQL_LLM_BASE_URL=https://api.openai.com/v1 NL2SQL_LLM_MODEL=gpt-4.1-mini NL2SQL_LLM_API_KEY='sk-...'
   java -jar nl2sql-0.1.0.jar
   ```

   ...or copy [`nl2sql.properties.example`](nl2sql.properties.example) to `nl2sql.properties` next to the jar and
   run `java -jar nl2sql-0.1.0.jar`. Use `NL2SQL_CONFIG=/path/to/file.properties` to load another file.

   On Windows (PowerShell): `$env:NL2SQL_DB_TYPE="mysql"` and so on.

3. Ask:

   ```bash
   curl -s localhost:8080/v1/ask -H 'Content-Type: application/json' \
        -d '{"question": "Which 5 carriers had the most late deliveries last quarter?"}'
   ```

   ```json
   {
     "question": "Which 5 carriers had the most late deliveries last quarter?",
     "sql": "SELECT c.carrier_name, COUNT(*) AS late_deliveries FROM shipments s JOIN carriers c ...",
     "explanation": "Counts delivered shipments whose delivery time is after the promised time ...",
     "clarification": null,
     "tables_used": ["shipments", "carriers"],
     "attempts": 1,
     "error": null,
     "columns": ["carrier_name", "late_deliveries"],
     "rows": [["Gati Express", 14], ["TCI Freight", 11]],
     "row_count": 5,
     "truncated": false,
     "elapsed_ms": 18.4
   }
   ```

### On Windows from a source checkout

```powershell
powershell -ExecutionPolicy Bypass -File .\run-local.ps1      # uses nl2sql.properties (and nl2sql.env if present)
powershell -ExecutionPolicy Bypass -File .\run-local.ps1 -Ask "How many shipments are in transit?"   # second window
```

The script reads `nl2sql.properties` and, if present, `nl2sql.env` (or the file given with `-EnvFile`) from the project folder, downloads portable copies of Java 21 and Maven into `.tools\`
if they are missing, builds the jar on first use and starts the server.
`NL2SQL_DB_URL` can be a `jdbc:` URL or the Python version's style (`mysql+pymysql://user:pw@host:3306/db`).

## Configuration

Precedence, highest first:

1. Command-line arguments (`--nl2sql.db.host=...`)
2. Environment variables
3. The properties file (`NL2SQL_CONFIG`, default `./nl2sql.properties`)
4. Built-in defaults

| Property | Environment variable | Default | Notes |
|---|---|---|---|
| `nl2sql.db.type` | `NL2SQL_DB_TYPE` | (required) | `mysql`, `mariadb`, `postgres`, `sqlserver`, `oracle`, `sqlite` |
| `nl2sql.db.host` | `NL2SQL_DB_HOST` | | SQL Server named instance: `server\INSTANCE` |
| `nl2sql.db.port` | `NL2SQL_DB_PORT` | per type | 3306 / 5432 / 1433 / 1521 |
| `nl2sql.db.name` | `NL2SQL_DB_NAME` | | Database. Oracle: service name. SQLite: file path |
| `nl2sql.db.schema` | `NL2SQL_DB_SCHEMA` | user default | PostgreSQL, SQL Server, Oracle |
| `nl2sql.db.username` | `NL2SQL_DB_USERNAME` | | Use a read-only account |
| `nl2sql.db.password` | `NL2SQL_DB_PASSWORD` | | |
| `nl2sql.db.options` | `NL2SQL_DB_OPTIONS` | | Extra JDBC options, `a=1&b=2` or `a=1;b=2` |
| `nl2sql.db.url` | `NL2SQL_DB_URL` | | Full JDBC URL; overrides type/host/port/name/options |
| `nl2sql.db.include-tables` | `NL2SQL_INCLUDE_TABLES` | all | Comma-separated |
| `nl2sql.db.exclude-tables` | `NL2SQL_EXCLUDE_TABLES` | | Comma-separated |
| `nl2sql.db.sample-values` | `NL2SQL_SAMPLE_VALUES` | `true` | Show the model a few distinct values of code-like columns (never personal data) |
| `nl2sql.db.pool-size` | `NL2SQL_DB_POOL_SIZE` | `5` | |
| `nl2sql.llm.provider` | `NL2SQL_LLM_PROVIDER` | `openai` | `openai` (any OpenAI-compatible API) or `anthropic` |
| `nl2sql.llm.base-url` | `NL2SQL_LLM_BASE_URL` | `https://api.openai.com/v1` | Azure: `https://<resource>.openai.azure.com/openai/v1` |
| `nl2sql.llm.api-key` | `NL2SQL_LLM_API_KEY` | | |
| `nl2sql.llm.model` | `NL2SQL_LLM_MODEL` | `gpt-4.1-mini` | Azure: the deployment name |
| `nl2sql.llm.temperature` | `NL2SQL_LLM_TEMPERATURE` | `0` | |
| `nl2sql.llm.max-tokens` | `NL2SQL_LLM_MAX_TOKENS` | `1500` | |
| `nl2sql.llm.timeout-seconds` | `NL2SQL_LLM_TIMEOUT` | `60` | |
| `nl2sql.llm.headers` | `NL2SQL_LLM_HEADERS` | | Extra headers for gateways: `Name: value; Other: value` |
| `nl2sql.guardrails.max-rows` | `NL2SQL_MAX_ROWS` | `1000` | Larger results are cut and flagged `truncated` |
| `nl2sql.guardrails.statement-timeout-seconds` | `NL2SQL_STATEMENT_TIMEOUT` | `30` | |
| `nl2sql.guardrails.blocked-columns` | `NL2SQL_BLOCKED_COLUMNS` | | `table.column,...`; hidden from the model and refused in SQL |
| `nl2sql.guardrails.allow-select-star` | `NL2SQL_ALLOW_SELECT_STAR` | `true` | |
| `nl2sql.guardrails.max-correction-attempts` | `NL2SQL_MAX_CORRECTION_ATTEMPTS` | `2` | Times the model may fix its own failing SQL (0–5) |
| `nl2sql.semantics-path` | `NL2SQL_SEMANTICS_PATH` | | JSON with table/column descriptions, synonyms and a glossary |
| `nl2sql.examples-path` | `NL2SQL_EXAMPLES_PATH` | | JSON file of verified question/SQL pairs (also written by `POST /v1/examples`) |
| `nl2sql.extra-instructions` | `NL2SQL_EXTRA_INSTRUCTIONS` | | Free text added to the prompt |
| `nl2sql.server.api-key` | `NL2SQL_SERVER_API_KEY` | | Requires header `X-API-Key` on `/v1/*` |
| `server.address` | `NL2SQL_SERVER_HOST` | `127.0.0.1` | `0.0.0.0` to accept remote connections (set an API key!) |
| `server.port` | `NL2SQL_SERVER_PORT` | `8080` | |

### Semantics file (optional)

```json
{
  "tables": {
    "shipments": {
      "description": "One row per consignment",
      "synonyms": ["loads", "consignments"],
      "columns": {"stat_cd": "Status: D=delivered, T=in transit, X=cancelled"}
    }
  },
  "glossary": {"late": "delivered_at > promised_delivery_at"}
}
```

## REST API

The full contract is served at `GET /openapi.yaml`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Liveness (no key needed) |
| GET | `/v1/info` | Database, type, schema, table count, version |
| GET | `/v1/schema` | Tables and columns visible to the model |
| POST | `/v1/schema/refresh` | Re-read the schema |
| POST | `/v1/ask` | `{"question": "...", "history": [{"question": "...", "sql": "..."}]}` → SQL + rows |
| POST | `/v1/generate` | Same body → validated SQL only, not run |
| POST | `/v1/examples` | `{"question": "...", "sql": "..."}` → store a verified example (201) |

If a question is ambiguous, `clarification` holds a question for the user; send the answer back as a new question
with the earlier turn in `history`. If the model cannot produce valid SQL after its correction attempts, `error`
explains why (HTTP 200). Failures reaching the LLM return 502, and an unreadable database returns 503.

## Safety

- Only a single `SELECT` (or `WITH ... SELECT`) is accepted. No DML/DDL, `SELECT INTO`, `FOR UPDATE`, file or
  network functions, sleep functions, or multiple statements.
- Every table and column must exist in the exposed schema; blocked columns are refused.
- Queries run in a read-only transaction where the database supports it, with a statement timeout and row
  limit, and are always rolled back.
- **Still use a database account that can only `SELECT` the tables you expose.** SQL Server has no read-only
  transactions, so the account's permissions are the real protection there:

  ```sql
  -- MySQL / MariaDB
  CREATE USER 'nl2sql_reader'@'%' IDENTIFIED BY '...'; GRANT SELECT ON tms.* TO 'nl2sql_reader'@'%';
  -- PostgreSQL
  CREATE ROLE nl2sql_reader LOGIN PASSWORD '...'; GRANT USAGE ON SCHEMA public TO nl2sql_reader;
  GRANT SELECT ON ALL TABLES IN SCHEMA public TO nl2sql_reader;
  -- SQL Server
  CREATE LOGIN nl2sql_reader WITH PASSWORD = '...'; CREATE USER nl2sql_reader FOR LOGIN nl2sql_reader;
  ALTER ROLE db_datareader ADD MEMBER nl2sql_reader;
  -- Oracle
  CREATE USER nl2sql_reader IDENTIFIED BY "..."; GRANT CREATE SESSION TO nl2sql_reader;
  GRANT SELECT ON tms.shipments TO nl2sql_reader;  -- per table; set NL2SQL_DB_SCHEMA=TMS
  ```

- The schema (table and column names, and a few sample values of code-like columns) is sent to the LLM. Rows
  returned by queries are not.

## Embedding the library in your application

No separate server is needed: add the core library and the JDBC driver for your database.

```xml
<dependency>
  <groupId>io.github.alok26sinha</groupId>
  <artifactId>nl2sql-core</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <version>9.4.0</version>
</dependency>
```

```java
Nl2SqlSettings s = new Nl2SqlSettings();
s.getDb().setType("mysql");
s.getDb().setHost("localhost");
s.getDb().setName("capstone");
s.getDb().setUsername("nl2sql_ro");
s.getDb().setPassword(System.getenv("DB_PASSWORD"));
s.getLlm().setBaseUrl("https://api.openai.com/v1");
s.getLlm().setModel("gpt-4.1-mini");
s.getLlm().setApiKey(System.getenv("LLM_API_KEY"));

try (Nl2SqlEngine engine = Nl2SqlEngine.create(s)) {        // create once and share; thread-safe
    QueryResult r = engine.ask("How many shipments are in transit?", List.of());
    if (r.ok()) {
        System.out.println(r.sql());
        r.rows().forEach(System.out::println);
    } else {
        System.out.println(r.clarification() != null ? r.clarification() : r.error());
    }
}
```

To reuse your application's connection pool (it should use a read-only account), build the engine yourself;
nl2sql then does not close the pool:

```java
LlmProvider llm = LlmProviders.create(s.getLlm(), Nl2SqlEngine.defaultJson());
Nl2SqlEngine engine = new Nl2SqlEngine(s, appDataSource, llm, false);
```

Other calls: `generate(question, history)` returns validated SQL without running it, `addExample(question, sql)`
stores a verified example, `catalog()` returns the schema, and `history` (a list of earlier `Turn(question, sql)`)
enables follow-up questions. The library uses Jackson 2, HikariCP, JSqlParser and SLF4J, and no Spring.

### Spring Boot starter

In a Spring Boot application, add the starter instead of wiring the engine yourself:

```xml
<dependency>
  <groupId>io.github.alok26sinha</groupId>
  <artifactId>nl2sql-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

It auto-configures a single `Nl2SqlEngine` bean from your `nl2sql.*` properties (the same keys as the table
above, e.g. `nl2sql.db.type`, `nl2sql.llm.model`, `nl2sql.guardrails.max-rows`). If your application already has a
`DataSource` bean, the engine **reuses it** (use a read-only account) and does not close it on shutdown; set at
least `nl2sql.db.type` so the engine knows the dialect. With no `DataSource` bean it opens its own pool from
`nl2sql.db.*`. A `LlmProvider` bean, if present, is used instead of the `nl2sql.llm.*` settings, and defining your
own `Nl2SqlEngine` bean turns the auto-configuration off.

```java
@RestController
class AskController {
    private final Nl2SqlEngine engine;
    AskController(Nl2SqlEngine engine) { this.engine = engine; } // injected by the starter

    @PostMapping("/ask")
    QueryResult ask(@RequestBody String question) { return engine.ask(question, List.of()); }
}
```

The core library stays Spring-free; the starter is the only module that depends on Spring.

## Running as a service

The official image is published to GHCR on each release as both `X.Y.Z` and `latest`:
[`ghcr.io/alok26sinha/nl2sql`](https://github.com/users/alok26sinha/packages/container/package/nl2sql).
The container listens on `0.0.0.0:8080`; set `NL2SQL_SERVER_HOST` or `NL2SQL_SERVER_PORT` to override those defaults.
Configure database, LLM, and other settings with the `NL2SQL_*` environment variables in the table above.

Create `nl2sql.env` with your required `NL2SQL_DB_*` and `NL2SQL_LLM_*` settings, and set a strong
`NL2SQL_SERVER_API_KEY`. The API key is optional in the application, but should be treated as required whenever
the server is reachable by untrusted clients; it is checked against the `X-API-Key` header on `/v1/*`.
Keep the env file private.

```bash
docker run -d --name nl2sql --restart unless-stopped -p 8080:8080 --env-file nl2sql.env \
  ghcr.io/alok26sinha/nl2sql:latest
```

For a fixed deployment version, replace `latest` with the release version, for example `0.1.0`.

## Building from source

```bash
mvn verify            # unit tests + SQLite; other databases run when NL2SQL_TEST_<DB>_URL is set
java -jar nl2sql-server/target/nl2sql-0.1.0-SNAPSHOT.jar
```

Modules:

- `nl2sql-core`: framework-free engine (schema introspection, prompting, guardrails, execution, LLM clients)
- `nl2sql-spring-boot-starter`: Spring Boot auto-configuration that exposes an `Nl2SqlEngine` bean
- `nl2sql-server`: Spring Boot 4 REST API and runnable jar

CI runs the full suite against MySQL 8.4, MariaDB 11, PostgreSQL 16, SQL Server 2022 and Oracle 23 Free.
Pushing a tag `vX.Y.Z` publishes `nl2sql-core` to Maven Central and `nl2sql-X.Y.Z.jar` to GitHub Releases;
see [RELEASING.md](RELEASING.md).

## License

Apache License 2.0. The bundled JDBC drivers keep their own licenses. Notably, MySQL Connector/J is GPLv2 with the
Universal FOSS Exception. Check that it suits how you distribute the jar, or use the MariaDB driver
(`jdbc:mariadb://` URLs work with MySQL servers).
