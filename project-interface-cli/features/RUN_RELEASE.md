# RELEASE: Run Feature

> **Version:** 1.0.0
> **Status:** Released
> **Release Date:** 2026-01-01

---

## 1. Implementation Summary

### 1.1 Completed Features

| Feature | Status | Description |
|---------|--------|-------------|
| **RunAPI** | Implemented | Library API for ad-hoc SQL execution with result download |
| **OutputFormat** | Implemented | CSV, TSV, JSON output format enum |
| **RunResult** | Implemented | Execution result model with metadata |
| **ExecutionPlan** | Implemented | Dry run result model |
| **`dli run`** | Implemented | Execute SQL file and save results |
| **`--dry-run`** | Implemented | Validate and show execution plan |
| **`--local/--server`** | Implemented | Execution mode preference |
| **Parameter substitution** | Implemented | Jinja-style `{{ param }}` replacement |
| **Table/JSON Output** | Implemented | Rich table and JSON format support |
| **Mock Mode** | Implemented | Full mock support via BasecampClient |

### 1.2 Files Created/Modified

#### New Files (core/run/)

| File | Lines | Purpose |
|------|-------|---------|
| `core/run/__init__.py` | ~10 | Module exports |
| `core/run/models.py` | ~80 | Core models (RunConfig, ExecutionData) |

#### New Files (models/)

| File | Lines | Purpose |
|------|-------|---------|
| `models/run.py` | ~100 | Result models (OutputFormat, RunResult, ExecutionPlan) |

#### New Files (api/)

| File | Lines | Purpose |
|------|-------|---------|
| `api/run.py` | ~400 | RunAPI class (run, dry_run, render_sql) |

#### New Files (commands/)

| File | Lines | Purpose |
|------|-------|---------|
| `commands/run.py` | ~350 | CLI command (dli run) |

#### New Files (tests/)

| File | Tests | Purpose |
|------|-------|---------|
| `tests/cli/test_run_cmd.py` | ~50 | CLI command tests |
| `tests/api/test_run_api.py` | ~40 | API tests |
| `tests/core/run/test_models.py` | ~30 | Model tests |

#### Modified Files

| File | Changes |
|------|---------|
| `exceptions.py` | Added DLI-41x error codes (410-416), Run exception classes |
| `core/client.py` | Added `run_execute()`, `run_get_policy()` methods + mock data |
| `api/__init__.py` | Export RunAPI |
| `__init__.py` | Export RunAPI and Run exceptions |
| `commands/__init__.py` | Export run command |
| `main.py` | Register run as top-level command |

---

## 2. Public API

### 2.1 RunAPI Methods

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `run()` | sql_path, output_path, output_format, parameters, limit, timeout, dialect, prefer_local, prefer_server | `RunResult` | Execute SQL and save results |
| `dry_run()` | sql_path, output_path, output_format, parameters, dialect, prefer_local, prefer_server | `ExecutionPlan` | Validate and show execution plan |
| `render_sql()` | sql_path, parameters | `str` | Render SQL with parameter substitution |

### 2.2 Usage Examples

```python
from dli import RunAPI, ExecutionContext, ExecutionMode
from dli.models.run import OutputFormat
from pathlib import Path

# Create context and API
ctx = ExecutionContext(
    execution_mode=ExecutionMode.SERVER,
    server_url="http://basecamp:8080"
)
api = RunAPI(context=ctx)

# Execute SQL and save to CSV
result = api.run(
    sql_path=Path("query.sql"),
    output_path=Path("results.csv"),
    parameters={"date": "2026-01-01"},
)
print(f"Saved {result.row_count} rows to {result.output_path}")

# Dry run to preview execution
plan = api.dry_run(
    sql_path=Path("query.sql"),
    output_path=Path("results.csv"),
)
print(f"Mode: {plan.execution_mode}, Valid: {plan.is_valid}")

# Render SQL with parameters
sql = api.render_sql(
    sql_path=Path("query.sql"),
    parameters={"date": "2026-01-01"},
)
print(sql)
```

---

## 3. CLI Commands

### 3.1 Command Table

| Command | Description |
|---------|-------------|
| `dli run --sql <FILE> -o <PATH>` | Execute SQL file and save results |
| `dli run --sql <FILE> -o <PATH> --dry-run` | Validate and show execution plan |
| `dli run --sql <FILE> -o <PATH> --local` | Request local execution |
| `dli run --sql <FILE> -o <PATH> --server` | Request server execution |

### 3.2 Options

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--sql` | - | PATH | Required | SQL file path |
| `--output` | `-o` | PATH | Required | Output file path |
| `--format` | `-f` | ENUM | `csv` | Output format: csv, tsv, json |
| `--local` | - | FLAG | `false` | Request local execution |
| `--server` | - | FLAG | `false` | Request server execution |
| `--param` | `-p` | TEXT | - | Parameter: key=value (repeatable) |
| `--limit` | `-n` | INT | - | Maximum rows to return |
| `--timeout` | `-t` | INT | `300` | Query timeout in seconds |
| `--dialect` | - | ENUM | `bigquery` | SQL dialect: bigquery, trino |
| `--dry-run` | - | FLAG | `false` | Validate without executing |
| `--show-sql` | - | FLAG | `false` | Display rendered SQL |
| `--quiet` | `-q` | FLAG | `false` | Suppress progress output |
| `--path` | - | PATH | `.` | Project path |

### 3.3 Examples

```bash
# Basic usage - execute SQL and save to CSV
dli run --sql query.sql --output results.csv

# Local execution with parameters
dli run --sql daily_report.sql --output report.csv --local \
    --param date=2026-01-01 --param region=us-west

# JSON output with row limit
dli run --sql users.sql --output users.json --format json --limit 100

# Dry run - validate without execution
dli run --sql complex_query.sql --output results.csv --dry-run

# Show rendered SQL with parameters
dli run --sql query.sql --output results.csv \
    --param date=2026-01-01 --show-sql
```

---

## 4. Error Codes

### 4.1 DLI-41x Run Error Codes

| Code | Name | Description | HTTP Status |
|------|------|-------------|-------------|
| DLI-410 | RUN_FILE_NOT_FOUND | SQL file not found | 404 |
| DLI-411 | RUN_LOCAL_DENIED | Local execution denied by server policy | 403 |
| DLI-412 | RUN_SERVER_UNAVAILABLE | Server execution unavailable | 503 |
| DLI-413 | RUN_EXECUTION_FAILED | Query execution failed | 500 |
| DLI-414 | RUN_OUTPUT_FAILED | Cannot write output file | 500 |
| DLI-415 | RUN_TIMEOUT | Query timeout exceeded | 504 |
| DLI-416 | RUN_INVALID_PARAM | Invalid parameter format | 400 |

### 4.2 Exception Classes

```python
from dli.exceptions import (
    RunFileNotFoundError,    # DLI-410
    RunLocalDeniedError,     # DLI-411
    RunServerUnavailableError,  # DLI-412
    RunExecutionError,       # DLI-413
    RunOutputError,          # DLI-414
    RunTimeoutError,         # DLI-415
    RunInvalidParamError,    # DLI-416
)
```

---

## 5. Data Models

### 5.1 OutputFormat Enum

```python
class OutputFormat(str, Enum):
    CSV = "csv"    # Comma-separated values
    TSV = "tsv"    # Tab-separated values
    JSON = "json"  # JSON Lines format
```

### 5.2 RunResult Model

```python
class RunResult(BaseModel):
    status: ResultStatus           # SUCCESS, FAILED
    sql_path: Path                 # Input SQL file
    output_path: Path              # Output file created
    output_format: OutputFormat    # Format used
    row_count: int                 # Rows returned
    duration_seconds: float        # Execution time
    execution_mode: ExecutionMode  # LOCAL, SERVER, MOCK
    rendered_sql: str              # SQL after parameter substitution
    bytes_processed: int | None    # Bytes processed (if available)
    bytes_billed: int | None       # Bytes billed (if available)
```

### 5.3 ExecutionPlan Model

```python
class ExecutionPlan(BaseModel):
    sql_path: Path                 # Input SQL file
    output_path: Path              # Target output file
    output_format: OutputFormat    # Format to use
    dialect: str                   # SQL dialect
    execution_mode: ExecutionMode  # Resolved mode
    rendered_sql: str              # Rendered SQL
    parameters: dict[str, str]     # Parameters used
    is_valid: bool                 # Whether plan is valid
    validation_error: str | None   # Error if invalid
```

---

## 6. Output Formats

### 6.1 CSV Format (Default)

```csv
user_id,event_count,created_at
1001,45,2026-01-01T10:30:00Z
1002,23,2026-01-01T11:15:00Z
```

Properties: comma delimiter, double-quote escaping, UTF-8 encoding, header included.

### 6.2 TSV Format

```tsv
user_id	event_count	created_at
1001	45	2026-01-01T10:30:00Z
1002	23	2026-01-01T11:15:00Z
```

Properties: tab delimiter, escape special chars, UTF-8 encoding, header included.

### 6.3 JSON Format (JSONL)

```json
{"user_id": 1001, "event_count": 45, "created_at": "2026-01-01T10:30:00Z"}
{"user_id": 1002, "event_count": 23, "created_at": "2026-01-01T11:15:00Z"}
```

Properties: one JSON object per line, compact format, streaming-friendly.

---

## 7. Test Coverage

### 7.1 Test Summary

| Category | File | Tests | Description |
|----------|------|-------|-------------|
| CLI | `tests/cli/test_run_cmd.py` | ~50 | Command options, output formats |
| API | `tests/api/test_run_api.py` | ~40 | run(), dry_run(), render_sql() |
| Models | `tests/core/run/test_models.py` | ~30 | Model validation |
| **Total** | | **~120** | |

### 7.2 Overall Test Count

| Category | Before | After | Delta |
|----------|--------|-------|-------|
| CLI Tests | ~878 | ~928 | +50 |
| API Tests | ~427 | ~467 | +40 |
| Core Tests | ~566 | ~596 | +30 |
| **Total** | **~1871** | **~1991** | **+120** |

---

## 8. Architecture

### 8.1 Module Structure

```
src/dli/core/run/
├── __init__.py       # Module exports
└── models.py         # RunConfig, ExecutionData

src/dli/models/
└── run.py            # OutputFormat, RunResult, ExecutionPlan

src/dli/api/
└── run.py            # RunAPI class

src/dli/commands/
└── run.py            # CLI command
```

### 8.2 Data Flow

```
User: dli run --sql query.sql -o results.csv
    │
    ▼
┌──────────────────────┐
│ commands/run.py      │
├──────────────────────┤
│ 1. Parse arguments   │
│ 2. Resolve paths     │
│ 3. Call RunAPI       │
│ 4. Display result    │
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│ RunAPI               │
├──────────────────────┤
│ 1. Read SQL file     │
│ 2. Render parameters │
│ 3. Check policy      │
│ 4. Execute query     │
│ 5. Write output      │
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│ BasecampClient       │
├──────────────────────┤
│ run_get_policy()     │
│ run_execute()        │
│ (mock/real API)      │
└──────────────────────┘
    │
    ▼
RunResult (saved to output file)
```

---

## 9. Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Top-level `dli run` command | Distinct from `dataset run` / `metric run` for ad-hoc queries |
| `--sql` and `--output` required | Explicit inputs, no stdin/stdout |
| No fallback on policy rejection | Transparency over convenience |
| JSONL for JSON format | Streaming-friendly, jq-compatible |
| DLI-41x error code range | Sub-range of execution (4xx) errors |
| Server policy check every run | Consistent enforcement |

---

## 10. Known Limitations

| Limitation | Description | Future Phase |
|------------|-------------|--------------|
| No result streaming | Full result must fit in memory | Phase 2 |
| No query cancellation | Cannot cancel running run queries | Phase 2 |
| No cost estimation | No preview of query cost | Phase 2 |
| Single file output | Cannot split large results | Phase 2 |

---

## 11. Future Work

### Phase 2 (Enhanced Features)

- [ ] Result streaming for large datasets
- [ ] Query cancellation support
- [ ] Cost estimation before execution
- [ ] Multiple output file support
- [ ] Progress bar for long queries

---

**Last Updated:** 2026-01-01
