# FEATURE: Run Command - Ad-hoc SQL Execution with Result Download

| Attribute | Value |
|-----------|-------|
| **Version** | 1.0.0 |
| **Status** | ✅ Complete |
| **Created** | 2026-01-01 |
| **Last Updated** | 2026-01-01 |
| **Implementation** | See [RUN_RELEASE.md](./RUN_RELEASE.md) |
| **References** | BigQuery bq, Databricks CLI, dbt, SQLMesh |

---

## 1. Overview

### 1.1 Purpose

`dli run` executes ad-hoc SQL files against query engines (BigQuery/Trino) and downloads results to local files. Unlike `dli dataset run` or `dli metric run`, which execute registered specs, this command runs arbitrary SQL files without registration.

**Key Distinction:**

| Command | Input Type | Registration |
|---------|------------|--------------|
| `dli dataset run` / `dli metric run` | Registered spec name | Required |
| `dli run` | Raw SQL file | Not required |

### 1.2 Core Principles

| Principle | Description |
|-----------|-------------|
| **Server-Policy First** | Server determines execution mode; user flags are requests, not commands |
| **No Fallback** | If server rejects execution mode request, return error (no silent fallback) |
| **Result Download** | Results are always saved to local files (`--output` required) |
| **Execution Transparency** | Clear indication of where execution occurred (local vs server) |
| **Existing Pattern Reuse** | Follows `ExecutionMode` pattern from `EXECUTION_RELEASE.md` |

### 1.3 Key Features

| Feature | Description |
|---------|-------------|
| **SQL File Execution** | Execute SQL from `.sql` files against configured engines |
| **Result Download** | Save query results to local CSV, TSV, or JSON files |
| **Execution Mode Control** | `--local` / `--server` flags with server policy override |
| **Parameter Substitution** | Jinja-style `{{ param }}` variable substitution |
| **Format Options** | CSV (default), TSV, or JSON output formats |
| **Dry Run** | Validate SQL and show execution plan without executing |

### 1.4 Industry Benchmarking

| Tool | Key Features | Applied to `dli run` |
|------|--------------|----------------------|
| **BigQuery bq** | `bq query --format=csv "SQL"`, `--destination_table`, `--max_rows` | `--format`, `--output`, `--limit` |
| **Databricks CLI** | SQL execution via API, result streaming | Server mode execution pattern |
| **dbt** | `dbt run` generates `run_results.json` artifact | Execution metadata in output |
| **SQLMesh** | `sqlmesh run` with plan/apply phases | `--dry-run` for validation |

### 1.5 System Integration Points

| Integration Area | Existing Pattern | Application |
|------------------|------------------|-------------|
| **Execution Model** | `ExecutionMode.LOCAL`, `SERVER`, `MOCK` | Reuse for run command |
| **CLI Commands** | `commands/dataset.py` structure | Similar command pattern |
| **Library API** | `DatasetAPI` facade pattern | `RunAPI` follows same pattern |
| **Client** | `BasecampClient` methods | Add `run_*` methods |
| **Exceptions** | DLI-4xx execution errors | Use DLI-41x sub-range for Run |

---

## 2. Execution Mode and Server Policy

### 2.1 Execution Mode Overview

| Mode | Description | Execution Location |
|------|-------------|--------------------|
| `LOCAL` | Direct connection to query engine | User's machine / local environment |
| `SERVER` | Execute via Basecamp Server API | Basecamp Server infrastructure |
| `MOCK` | Test mode with mock data | No actual execution |

### 2.2 Server Policy Enforcement

The server maintains execution policies that override user requests:

```
User Request + Server Policy = Final Execution Mode
```

| User Request | Server Policy | Result |
|--------------|---------------|--------|
| `--local` | `allow_local: true` | Execute locally |
| `--local` | `allow_local: false` | Error (DLI-411) |
| `--server` | `server_available: true` | Execute on server |
| `--server` | `server_available: false` | Error (DLI-412) |
| (none) | Policy default | Use server's default mode |

### 2.3 No Fallback Policy

**Design Decision:** When the server rejects an execution mode request, the CLI returns an error instead of silently falling back to another mode.

```bash
# Server rejects local execution
$ dli run --sql query.sql --output results.csv --local
Error [DLI-411]: Local execution not permitted by server policy.
Server requires: --server mode for this operation.

# Server is unavailable
$ dli run --sql query.sql --output results.csv --server
Error [DLI-412]: Server execution unavailable.
Reason: Basecamp Server unreachable at https://basecamp.example.com
```

---

## 3. CLI Design

### 3.1 Command Structure

```
dli run --sql <FILE> --output <PATH> [options]
```

### 3.2 Required Options

| Option | Short | Type | Description |
|--------|-------|------|-------------|
| `--sql` | - | PATH | SQL file path |
| `--output` | `-o` | PATH | Output file path |

### 3.3 Optional Options

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--format` | `-f` | ENUM | `csv` | Output format: `csv`, `tsv`, `json` |
| `--local` | - | FLAG | `false` | Request local execution |
| `--server` | - | FLAG | `false` | Request server execution |
| `--param` | `-p` | TEXT | - | Parameter: `key=value` (repeatable) |
| `--limit` | `-n` | INT | - | Maximum rows to return |
| `--timeout` | `-t` | INT | `300` | Query timeout in seconds (1-3600) |
| `--dialect` | - | ENUM | `bigquery` | SQL dialect: `bigquery`, `trino` |
| `--dry-run` | - | FLAG | `false` | Validate and show plan without executing |
| `--show-sql` | - | FLAG | `false` | Display rendered SQL before execution |
| `--quiet` | `-q` | FLAG | `false` | Suppress progress output |
| `--path` | - | PATH | `.` | Project path for config resolution |

### 3.4 Mutually Exclusive Options

| Option Group | Rule |
|--------------|------|
| `--local`, `--server` | Cannot use both; uses server policy default if neither specified |

### 3.5 Examples

```bash
# Basic usage - execute SQL and save to CSV
$ dli run --sql query.sql --output results.csv
Executing query.sql (server mode)...
Query completed in 12.5s
Rows returned: 1,234
Saved to: results.csv

# Local execution with parameters
$ dli run --sql daily_report.sql --output report.csv --local \
    --param date=2026-01-01 --param region=us-west
Executing daily_report.sql (local mode)...
Query completed in 8.2s
Rows returned: 567
Saved to: report.csv

# JSON output with row limit
$ dli run --sql users.sql --output users.json --format json --limit 100
Executing users.sql (server mode)...
Query completed in 3.1s
Rows returned: 100 (limited)
Saved to: users.json

# TSV output for spreadsheet compatibility
$ dli run --sql metrics.sql --output metrics.tsv --format tsv

# Dry run - validate without execution
$ dli run --sql complex_query.sql --output results.csv --dry-run
[DRY RUN] Would execute:
  SQL file: complex_query.sql
  Dialect: bigquery
  Mode: server (policy default)
  Output: results.csv (csv)
  Parameters: none

SQL Preview:
  SELECT user_id, COUNT(*) as cnt
  FROM events
  WHERE event_date = '2026-01-01'
  GROUP BY user_id

# Show rendered SQL with parameters
$ dli run --sql query.sql --output results.csv \
    --param date=2026-01-01 --show-sql
Rendered SQL:
  SELECT * FROM events WHERE event_date = '2026-01-01'

Executing query.sql (server mode)...

# Trino dialect execution
$ dli run --sql presto_query.sql --output results.csv --dialect trino

# Quiet mode for scripting
$ dli run --sql query.sql --output results.csv --quiet
```

### 3.6 Error Examples

```bash
# Missing required --sql option
$ dli run --output results.csv
Error: Missing option '--sql'.

# Missing required --output option
$ dli run --sql query.sql
Error: Missing option '--output' / '-o'.

# SQL file not found
$ dli run --sql nonexistent.sql --output results.csv
Error [DLI-002]: File not found: nonexistent.sql

# Server policy rejects local execution
$ dli run --sql query.sql --output results.csv --local
Error [DLI-411]: Local execution not permitted by server policy.
Server requires: --server mode for this operation.

# Both --local and --server specified
$ dli run --sql query.sql --output results.csv --local --server
Error: Cannot specify both --local and --server.

# Query timeout
$ dli run --sql slow_query.sql --output results.csv --timeout 10
Error [DLI-402]: Query timeout after 10 seconds.

# Invalid parameter format
$ dli run --sql query.sql --output results.csv --param invalid
Error: Invalid parameter format. Expected 'key=value', got 'invalid'.
```

---

## 4. Output Formats

### 4.1 CSV Format (Default)

Standard CSV with header row.

```csv
user_id,event_count,created_at
1001,45,2026-01-01T10:30:00Z
1002,23,2026-01-01T11:15:00Z
1003,78,2026-01-01T12:00:00Z
```

| Property | Value |
|----------|-------|
| Delimiter | `,` (comma) |
| Quote character | `"` (double quote) |
| Escape | `""` (double quote for quotes in values) |
| Line ending | `\n` (LF) |
| Header | Always included |
| Encoding | UTF-8 |

### 4.2 TSV Format

Tab-separated values for spreadsheet compatibility.

```tsv
user_id	event_count	created_at
1001	45	2026-01-01T10:30:00Z
1002	23	2026-01-01T11:15:00Z
1003	78	2026-01-01T12:00:00Z
```

| Property | Value |
|----------|-------|
| Delimiter | `\t` (tab) |
| Quoting | None (tabs/newlines escaped as `\t`, `\n`) |
| Line ending | `\n` (LF) |
| Header | Always included |
| Encoding | UTF-8 |

### 4.3 JSON Format

JSON Lines (JSONL) format with one JSON object per line.

```json
{"user_id": 1001, "event_count": 45, "created_at": "2026-01-01T10:30:00Z"}
{"user_id": 1002, "event_count": 23, "created_at": "2026-01-01T11:15:00Z"}
{"user_id": 1003, "event_count": 78, "created_at": "2026-01-01T12:00:00Z"}
```

| Property | Value |
|----------|-------|
| Format | JSON Lines (one object per line, not JSON array) |
| Pretty printing | None (compact) |
| Encoding | UTF-8 |
| Null values | `null` (not omitted) |
| Dates | ISO 8601 string format |

**Rationale for JSONL:**

- Streaming-friendly (can process line by line)
- Works with `jq` and other line-based tools
- Easier to append/concatenate files
- Memory-efficient for large datasets

---

## 5. API Design

> **Implementation:** Full API implementation details in [RUN_RELEASE.md](./RUN_RELEASE.md)

### 5.1 RunAPI Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `run()` | `RunResult` | ✅ Execute SQL file and save results to output file |
| `dry_run()` | `ExecutionPlan` | ✅ Validate SQL and show execution plan without executing |
| `render_sql()` | `str` | ✅ Render SQL file with parameter substitution |

**Example Usage:**

```python
from dli import RunAPI, ExecutionContext
from pathlib import Path

api = RunAPI(context=ExecutionContext())
result = api.run(
    sql_path=Path("query.sql"),
    output_path=Path("results.csv"),
    parameters={"date": "2026-01-01"},
)
```

### 5.2 Result Models

| Model | Status | Description |
|-------|--------|-------------|
| `OutputFormat` | ✅ Implemented | Enum: CSV, TSV, JSON |
| `RunResult` | ✅ Implemented | Execution result with metadata |
| `ExecutionPlan` | ✅ Implemented | Dry run validation result |

---

## 6. Integration Points

> **Implementation:** See [RUN_RELEASE.md](./RUN_RELEASE.md) for implementation details.

### 6.1 BasecampClient Methods

| Method | Status | Description |
|--------|--------|-------------|
| `run_get_policy()` | ✅ Implemented | Get server execution policy |
| `run_execute()` | ✅ Implemented | Execute SQL via server API |

### 6.2 Core Models

| Model | Status | Location |
|-------|--------|----------|
| `RunConfig` | ✅ Implemented | `core/run/models.py` |
| `ExecutionData` | ✅ Implemented | `core/run/models.py` |

---

## 7. Error Codes (DLI-41x)

> **Implementation:** See [RUN_RELEASE.md](./RUN_RELEASE.md) for exception class details.

Run errors use the DLI-41x sub-range of execution errors (DLI-4xx).

| Code | Exception Class | Status | Description |
|------|----------------|--------|-------------|
| DLI-410 | `RunFileNotFoundError` | ✅ Implemented | SQL file not found |
| DLI-411 | `RunLocalDeniedError` | ✅ Implemented | Server policy denies local execution |
| DLI-412 | `RunServerUnavailableError` | ✅ Implemented | Server execution unavailable |
| DLI-413 | `RunExecutionError` | ✅ Implemented | Query execution failed |
| DLI-414 | `RunOutputError` | ✅ Implemented | Cannot write output file |
| DLI-415 | `RunTimeoutError` | ✅ Implemented | Query timeout |
| DLI-416 | `RunParameterInvalidError` | ✅ Implemented | Invalid parameter format |

---

## 8. Server API Endpoints

> **Implementation:** See [RUN_RELEASE.md](./RUN_RELEASE.md) for request/response schemas.

| Operation | Method | Endpoint | Status |
|-----------|--------|----------|--------|
| Get execution policy | `GET` | `/api/v1/run/policy` | ✅ Implemented |
| Execute query | `POST` | `/api/v1/run/execute` | ✅ Implemented |

---

## 9. Success Criteria

### 9.1 Feature Completion

| Feature | Status | Verification |
|---------|--------|--------------|
| `RunAPI` | ✅ Complete | `run()`, `dry_run()`, `render_sql()` implemented |
| CLI run | ✅ Complete | `dli run --sql query.sql --output results.csv` |
| Parameters | ✅ Complete | `--param key=value` Jinja substitution |
| Formats | ✅ Complete | CSV, TSV, JSON outputs validated |
| Dry run | ✅ Complete | `--dry-run` shows execution plan |
| Error handling | ✅ Complete | DLI-410~416 exception classes |

### 9.2 Test Quality

| Metric | Target | Achieved |
|--------|--------|----------|
| Unit test coverage | >= 80% | ✅ ~120 tests |
| Mock mode tests | All formats | ✅ CSV/TSV/JSON |
| CLI command tests | Each option | ✅ 50 tests |
| Output format tests | Format validation | ✅ All verified |

### 9.3 Code Quality

| Principle | Status |
|-----------|--------|
| Single Responsibility | ✅ `RunAPI` delegates to client/executor |
| Consistent Pattern | ✅ Follows `DatasetAPI`/`QueryAPI` facade |
| Dependency Inversion | ✅ `RunAPI` accepts optional client via DI |
| No Silent Fallback | ✅ Policy rejection raises error |

---

## 10. Directory Structure

```
project-interface-cli/src/dli/
├── __init__.py           # ✅ RunAPI export added
├── api/
│   ├── __init__.py       # ✅ RunAPI export added
│   └── run.py            # ✅ Created: RunAPI class
├── models/
│   ├── __init__.py       # ✅ run model exports added
│   └── run.py            # ✅ Created: OutputFormat, RunResult, ExecutionPlan
├── commands/
│   ├── __init__.py       # ✅ run_app export added
│   └── run.py            # ✅ Created: CLI command
├── core/
│   ├── client.py         # ✅ run_* methods added
│   └── run/              # ✅ Created: directory
│       ├── __init__.py   # ✅ Created
│       └── models.py     # ✅ Created: RunConfig, ExecutionData
└── exceptions.py         # ✅ DLI-41x codes, Run exceptions added

tests/
├── api/
│   └── test_run_api.py   # ✅ Created: ~40 tests
├── cli/
│   └── test_run_cmd.py   # ✅ Created: ~50 tests
└── core/run/
    └── test_models.py    # ✅ Created: ~30 tests
```

---

## 11. Reference Patterns

| Implementation | Reference File | Pattern | Status |
|----------------|----------------|---------|--------|
| `RunAPI` | `api/dataset.py` | Facade pattern, mock mode, DI | ✅ Applied |
| Result models | `models/common.py` | Pydantic `BaseModel` with `Field` | ✅ Applied |
| CLI command | `commands/dataset.py` | Typer command structure | ✅ Applied |
| Client methods | `core/client.py` | `ServerResponse`, `mock_mode` check | ✅ Applied |
| Exceptions | `exceptions.py` | `DLIError` inheritance, `ErrorCode` | ✅ Applied |
| `ExecutionMode` | `models/common.py` | `LOCAL`, `SERVER`, `MOCK` enum | ✅ Applied |

---

## Appendix A: Command Summary

### Run SQL

```bash
dli run --sql <FILE> --output <PATH> [options]
```

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--sql` | - | PATH | (required) | SQL file path |
| `--output` | `-o` | PATH | (required) | Output file path |
| `--format` | `-f` | ENUM | `csv` | Output: `csv`, `tsv`, `json` |
| `--local` | - | FLAG | `false` | Request local execution |
| `--server` | - | FLAG | `false` | Request server execution |
| `--param` | `-p` | TEXT | - | Parameter: `key=value` (repeatable) |
| `--limit` | `-n` | INT | - | Maximum rows |
| `--timeout` | `-t` | INT | `300` | Timeout seconds |
| `--dialect` | - | ENUM | `bigquery` | Dialect: `bigquery`, `trino` |
| `--dry-run` | - | FLAG | `false` | Show plan without executing |
| `--show-sql` | - | FLAG | `false` | Display rendered SQL |
| `--quiet` | `-q` | FLAG | `false` | Suppress progress output |
| `--path` | - | PATH | `.` | Project path |

**Examples:**

```bash
dli run --sql query.sql --output results.csv                    # Basic CSV output
dli run --sql query.sql -o report.json -f json                  # JSON output
dli run --sql query.sql -o data.tsv -f tsv --local             # Local execution
dli run --sql query.sql -o out.csv -p date=2026-01-01 -p id=5  # Parameters
dli run --sql query.sql -o out.csv --dry-run                    # Dry run
dli run --sql query.sql -o out.csv --limit 100 --timeout 60     # Limits
```

---

## Appendix B: Design Decisions

| # | Topic | Decision | Rationale |
|---|-------|----------|-----------|
| 1 | Command name | `dli run` (top-level) | Distinct from `dataset run`, `metric run`: ad-hoc vs registered |
| 2 | `--sql` required | Yes | Explicit input file, not stdin |
| 3 | `--output` required | Yes | Always download results, not just view |
| 4 | Output format default | CSV | Most common, spreadsheet-compatible |
| 5 | JSON format | JSONL (one object per line) | Streaming-friendly, jq-compatible |
| 6 | Fallback on policy rejection | None (return error) | Transparency over convenience |
| 7 | Parameter syntax | `--param key=value` | Consistent with dbt, explicit |
| 8 | Timeout default | 300 seconds | Balance between large queries and resource limits |
| 9 | Dialect default | `bigquery` | Most common in this platform |
| 10 | Error code range | DLI-41x | Sub-range of execution (4xx) |
| 11 | Server policy check | Every execution | Ensure consistent enforcement |
| 12 | Local mode | Direct engine connection | Lower latency for development |
| 13 | Server mode | Via Basecamp API | Audit trail, resource management |
| 14 | Dry run | Validate + show plan | Safe verification before execution |
| 15 | Quiet mode | Suppress progress | Scripting/automation friendly |

---

## Appendix C: Comparison with Similar Commands

| Aspect | `dli run` | `dli dataset run` | `dli query show` |
|--------|-----------|-------------------|------------------|
| Input | Raw SQL file | Registered dataset name | Query ID |
| Output | Result file (CSV/JSON/TSV) | Result data | Query metadata |
| Registration | Not required | Required | N/A |
| Execution | Runs query | Runs query | Retrieves history |
| Use case | Ad-hoc analysis | Scheduled pipelines | Audit/debug |

---

**Last Updated:** 2026-01-01

> **Note:** This feature is complete. See [RUN_RELEASE.md](./RUN_RELEASE.md) for implementation details, file locations, test counts, and design decisions.
