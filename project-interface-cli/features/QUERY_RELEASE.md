# RELEASE: Query Feature

> **Version:** 1.0.0
> **Status:** Complete
> **Release Date:** 2026-01-01

---

## 1. Implementation Summary

### 1.1 Completed Features

| Feature | Status | Description |
|---------|--------|-------------|
| **QueryAPI** | Implemented | Library API for query execution metadata access |
| **QueryScope** | Implemented | Scope-based filtering (my, system, user, all) |
| **QueryState** | Implemented | Query state enum (pending, running, success, failed, cancelled) |
| **QueryInfo** | Implemented | Summary model for query list results |
| **QueryDetail** | Implemented | Full query detail with execution metadata |
| **`dli query list`** | Implemented | List queries with scope and filters |
| **`dli query show`** | Implemented | Show query detail by ID |
| **`dli query cancel`** | Implemented | Cancel query by ID or user |
| **Table Output** | Implemented | Rich table format for query results |
| **JSON Output** | Implemented | `--format json` for programmatic use |
| **Mock Mode** | Implemented | Full mock support via BasecampClient |

### 1.2 Files Created/Modified

#### New Files (core/query/)

| File | Lines | Purpose |
|------|-------|---------|
| `core/query/__init__.py` | ~50 | Module exports |
| `core/query/models.py` | ~200 | Core models (AccountType, QueryScope, QueryState, TableReference, QueryInfo, QueryDetail) |

#### New Files (models/)

| File | Lines | Purpose |
|------|-------|---------|
| `models/query.py` | ~100 | Result models (QueryListResult, QueryDetailResult, QueryCancelResult) |

#### New Files (api/)

| File | Lines | Purpose |
|------|-------|---------|
| `api/query.py` | ~300 | QueryAPI class (list_queries, get, get_result, cancel) |

#### New Files (commands/)

| File | Lines | Purpose |
|------|-------|---------|
| `commands/query.py` | ~400 | CLI commands (list, show, cancel) |

#### New Files (tests/)

| File | Tests | Purpose |
|------|-------|---------|
| `tests/cli/test_query_cmd.py` | 50 | CLI command tests |
| `tests/api/test_query_api.py` | 54 | API tests |
| `tests/core/query/test_models.py` | 66 | Model tests |

#### Modified Files

| File | Changes |
|------|---------|
| `exceptions.py` | Added DLI-78x error codes (780-784), Query exception classes |
| `core/client.py` | Added `query_list()`, `query_get()`, `query_cancel()` methods + mock data |
| `api/__init__.py` | Export QueryAPI |
| `__init__.py` | Export QueryAPI and Query exceptions |
| `commands/__init__.py` | Export query_app |
| `main.py` | Register query_app subcommand |

---

## 2. Usage Guide

### 2.1 `dli query list` Command

List queries with scope-based filtering:

```bash
# List my queries (default scope)
dli query list

# List system account queries (airflow, dbt-runner, etc.)
dli query list --scope system

# List queries for a specific account keyword
dli query list airflow --scope system

# Filter by SQL content
dli query list --sql "SELECT * FROM users"

# Filter by status
dli query list --status running

# Filter by tags (AND logic when repeated)
dli query list -t production -t analytics

# Time range filters
dli query list --since 2026-01-01 --until 2026-01-02

# Filter by engine
dli query list --engine bigquery

# Pagination
dli query list --limit 20 --offset 40

# JSON output
dli query list --format json
```

### 2.2 `dli query show` Command

Show detailed information for a specific query:

```bash
# Show query detail
dli query show bq_job_abc123

# JSON output
dli query show bq_job_abc123 --format json
```

### 2.3 `dli query cancel` Command

Cancel queries by ID or user:

```bash
# Cancel a specific query
dli query cancel bq_job_abc123

# Cancel all running queries for a user (with confirmation)
dli query cancel --user airflow-prod

# Dry run (preview what would be cancelled)
dli query cancel --user airflow-prod --dry-run

# Force cancel without confirmation
dli query cancel --user airflow-prod --force
```

### 2.4 Command Options

#### `dli query list`

| Option | Description | Default |
|--------|-------------|---------|
| `account_keyword` | Filter by account name (positional, optional) | None |
| `--scope` | Query scope (my, system, user, all) | `my` |
| `--sql` | Filter by SQL text content | None |
| `-S, --status` | Filter by query state | None |
| `-t, --tag` | Filter by tags (repeatable, AND logic) | None |
| `-n, --limit` | Maximum results | `50` |
| `--offset` | Skip first N results | `0` |
| `--since` | Start time filter | None |
| `--until` | End time filter | None |
| `--engine` | Filter by engine (bigquery, trino) | None |
| `-f, --format` | Output format (table, json) | `table` |

#### `dli query show`

| Option | Description | Default |
|--------|-------------|---------|
| `query_id` | Query ID (positional, required) | Required |
| `-f, --format` | Output format (table, json) | `table` |

#### `dli query cancel`

| Option | Description | Default |
|--------|-------------|---------|
| `query_id` | Query ID (positional, optional) | None |
| `--user` | Cancel all running queries for account | None |
| `--dry-run` | Preview what would be cancelled | `False` |
| `--force` | Skip confirmation prompt | `False` |

---

## 3. Architecture

### 3.1 Module Structure

```
src/dli/core/query/
├── __init__.py       # Module exports
└── models.py         # AccountType, QueryScope, QueryState, TableReference, QueryInfo, QueryDetail

src/dli/models/
└── query.py          # QueryListResult, QueryDetailResult, QueryCancelResult

src/dli/api/
└── query.py          # QueryAPI class

src/dli/commands/
└── query.py          # CLI commands
```

### 3.2 Data Models

```python
class AccountType(str, Enum):
    PERSONAL = "personal"
    SYSTEM = "system"

class QueryScope(str, Enum):
    MY = "my"          # Current user's queries only
    SYSTEM = "system"  # System account queries (airflow, dbt, etc.)
    USER = "user"      # Specific user's queries
    ALL = "all"        # All accessible queries

class QueryState(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    SUCCESS = "success"
    FAILED = "failed"
    CANCELLED = "cancelled"

@dataclass
class TableReference:
    catalog: str
    schema: str
    table: str
    access_type: str  # read, write

    @property
    def full_name(self) -> str:
        return f"{self.catalog}.{self.schema}.{self.table}"

@dataclass
class QueryInfo:
    query_id: str
    account_id: str
    account_type: AccountType
    engine: str
    state: QueryState
    sql_preview: str  # First 100 chars of SQL
    started_at: datetime | None
    ended_at: datetime | None
    duration_ms: int | None
    rows_processed: int | None
    bytes_processed: int | None
    tags: list[str]

@dataclass
class QueryDetail(QueryInfo):
    sql: str  # Full SQL text
    error_message: str | None
    tables_read: list[TableReference]
    tables_written: list[TableReference]
    created_at: datetime
    updated_at: datetime
    metadata: dict[str, Any]
```

### 3.3 Data Flow

```
User CLI Command
    │
    ▼
┌──────────────────────┐
│ query.py (CLI)       │
├──────────────────────┤
│ 1. Parse arguments   │
│ 2. Build filters     │
│ 3. Call QueryAPI     │
│ 4. Format output     │
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│ QueryAPI             │
├──────────────────────┤
│ 1. Validate params   │
│ 2. Call client       │
│ 3. Build result      │
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│ BasecampClient       │
├──────────────────────┤
│ query_list()         │
│ query_get()          │
│ query_cancel()       │
│ (mock/real API)      │
└──────────────────────┘
    │
    ▼
QueryListResult / QueryDetailResult / QueryCancelResult
```

### 3.4 Exception Handling

```python
# DLI-78x Error Codes
class ErrorCode(str, Enum):
    QUERY_NOT_FOUND = "DLI-780"
    QUERY_ACCESS_DENIED = "DLI-781"
    QUERY_CANCEL_FAILED = "DLI-782"
    QUERY_INVALID_FILTER = "DLI-783"
    QUERY_SERVER_ERROR = "DLI-784"

# Exception Classes
class QueryNotFoundError(DLIError):
    """Query ID not found."""

class QueryAccessDeniedError(DLIError):
    """Access denied to query."""

class QueryCancelError(DLIError):
    """Query cancellation failed."""

class QueryInvalidFilterError(DLIError):
    """Invalid filter parameters."""
```

---

## 4. Display Formats

### 4.1 Table Format (Default)

Query list:

```
                         My Queries (scope: my)
┏━━━━━━━━━━━━━━━━━┳━━━━━━━━━━━┳━━━━━━━━━━━┳━━━━━━━━━━┳━━━━━━━━━━━━┳━━━━━━━━━━━┓
┃ Query ID        ┃ Account   ┃ Engine    ┃ State    ┃ Duration   ┃ Rows      ┃
┡━━━━━━━━━━━━━━━━━╇━━━━━━━━━━━╇━━━━━━━━━━━╇━━━━━━━━━━╇━━━━━━━━━━━━╇━━━━━━━━━━━┩
│ bq_job_abc123   │ kun       │ bigquery  │ success  │ 1.23s      │ 1,234     │
│ bq_job_def456   │ kun       │ bigquery  │ running  │ 45.6s      │ -         │
│ trino_xyz789    │ kun       │ trino     │ failed   │ 0.5s       │ 0         │
└─────────────────┴───────────┴───────────┴──────────┴────────────┴───────────┘
Showing 3 of 3 queries
```

Query detail:

```
                         Query Detail: bq_job_abc123
┏━━━━━━━━━━━━━━━━━━━┳━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃ Field             ┃ Value                                              ┃
┡━━━━━━━━━━━━━━━━━━━╇━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┩
│ Query ID          │ bq_job_abc123                                      │
│ Account           │ kun (personal)                                     │
│ Engine            │ bigquery                                           │
│ State             │ success                                            │
│ Started At        │ 2026-01-01 10:00:00                               │
│ Ended At          │ 2026-01-01 10:00:01                               │
│ Duration          │ 1.23s                                              │
│ Rows Processed    │ 1,234                                              │
│ Bytes Processed   │ 1.5 MB                                             │
│ Tags              │ production, analytics                              │
└───────────────────┴────────────────────────────────────────────────────┘

SQL:
SELECT user_id, COUNT(*) as clicks
FROM analytics.events
WHERE event_type = 'click'
GROUP BY user_id

Tables Read:
  - analytics.events (read)

Tables Written:
  (none)
```

### 4.2 JSON Format

Query list (`--format json`):

```json
{
  "queries": [
    {
      "query_id": "bq_job_abc123",
      "account_id": "kun",
      "account_type": "personal",
      "engine": "bigquery",
      "state": "success",
      "sql_preview": "SELECT user_id, COUNT(*) as clicks FROM...",
      "started_at": "2026-01-01T10:00:00Z",
      "ended_at": "2026-01-01T10:00:01Z",
      "duration_ms": 1230,
      "rows_processed": 1234,
      "bytes_processed": 1500000,
      "tags": ["production", "analytics"]
    }
  ],
  "total": 1,
  "limit": 50,
  "offset": 0
}
```

Query detail (`--format json`):

```json
{
  "query_id": "bq_job_abc123",
  "account_id": "kun",
  "account_type": "personal",
  "engine": "bigquery",
  "state": "success",
  "sql": "SELECT user_id, COUNT(*) as clicks\nFROM analytics.events\nWHERE event_type = 'click'\nGROUP BY user_id",
  "sql_preview": "SELECT user_id, COUNT(*) as clicks FROM...",
  "started_at": "2026-01-01T10:00:00Z",
  "ended_at": "2026-01-01T10:00:01Z",
  "duration_ms": 1230,
  "rows_processed": 1234,
  "bytes_processed": 1500000,
  "tags": ["production", "analytics"],
  "error_message": null,
  "tables_read": [
    {
      "catalog": "analytics",
      "schema": "public",
      "table": "events",
      "access_type": "read"
    }
  ],
  "tables_written": [],
  "created_at": "2026-01-01T10:00:00Z",
  "updated_at": "2026-01-01T10:00:01Z",
  "metadata": {}
}
```

---

## 5. Mock Data

The mock implementation in `BasecampClient` provides comprehensive test data covering:

### 5.1 Account Types

| Account | Type | Description |
|---------|------|-------------|
| `kun` | Personal | Current user's queries |
| `alice`, `bob` | Personal | Other users' queries |
| `airflow-prod` | System | Airflow scheduler queries |
| `dbt-runner` | System | dbt transformation queries |
| `spark-etl` | System | Spark ETL job queries |

### 5.2 Query States

| State | Count | Description |
|-------|-------|-------------|
| `pending` | 2 | Queued queries |
| `running` | 3 | Currently executing |
| `success` | 5 | Completed successfully |
| `failed` | 2 | Execution failed |
| `cancelled` | 1 | User cancelled |

### 5.3 Engines

| Engine | Count | Description |
|--------|-------|-------------|
| `bigquery` | 8 | Google BigQuery queries |
| `trino` | 5 | Trino/Presto queries |

### 5.4 Mock Data Structure

```python
# Mock queries in BasecampClient
MOCK_QUERIES = [
    {
        "query_id": "bq_job_personal_001",
        "account_id": "kun",
        "account_type": "personal",
        "engine": "bigquery",
        "state": "success",
        "sql": "SELECT * FROM analytics.daily_metrics WHERE date = '2026-01-01'",
        "tags": ["production", "analytics"],
        ...
    },
    {
        "query_id": "bq_job_airflow_001",
        "account_id": "airflow-prod",
        "account_type": "system",
        "engine": "bigquery",
        "state": "running",
        "sql": "INSERT INTO warehouse.fact_orders SELECT ...",
        "tags": ["etl", "daily"],
        ...
    },
    ...
]
```

---

## 6. Library API

### 6.1 QueryAPI Methods

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `list_queries()` | scope, account_keyword, sql, status, tags, limit, offset, since, until, engine | `QueryListResult` | List queries with filters |
| `get()` | query_id | `QueryDetailResult` | Get query detail |
| `get_result()` | query_id, limit | `QueryResultData` | Get query result data |
| `cancel()` | query_id, user, dry_run | `QueryCancelResult` | Cancel query(s) |

### 6.2 Usage Examples

```python
from dli import QueryAPI, ExecutionContext, ExecutionMode
from dli.core.query.models import QueryScope, QueryState

# Create context and API
ctx = ExecutionContext(
    execution_mode=ExecutionMode.SERVER,
    server_url="http://basecamp:8080"
)
api = QueryAPI(context=ctx)

# List my queries
result = api.list_queries()
for query in result.queries:
    print(f"{query.query_id}: {query.state}")

# List system queries for airflow
result = api.list_queries(
    scope=QueryScope.SYSTEM,
    account_keyword="airflow"
)

# Filter by state and engine
result = api.list_queries(
    status=QueryState.RUNNING,
    engine="bigquery"
)

# Get query detail
detail = api.get("bq_job_abc123")
print(f"SQL: {detail.query.sql}")
print(f"Tables read: {[t.full_name for t in detail.query.tables_read]}")

# Cancel a specific query
result = api.cancel(query_id="bq_job_abc123")
print(f"Cancelled: {result.cancelled_count}")

# Cancel all running queries for a user (dry run)
result = api.cancel(user="airflow-prod", dry_run=True)
print(f"Would cancel {result.cancelled_count} queries:")
for q in result.cancelled_queries:
    print(f"  - {q.query_id}")
```

### 6.3 Mock Mode Testing

```python
from dli import QueryAPI, ExecutionContext, ExecutionMode

# Mock mode for testing
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
api = QueryAPI(context=ctx)

# All operations work with mock data
result = api.list_queries(scope=QueryScope.ALL)
assert len(result.queries) > 0

detail = api.get(result.queries[0].query_id)
assert detail.query is not None
```

---

## 7. Error Codes

### 7.1 DLI-78x Query Error Codes

| Code | Name | Description | HTTP Status |
|------|------|-------------|-------------|
| DLI-780 | QUERY_NOT_FOUND | Query ID not found in catalog | 404 |
| DLI-781 | QUERY_ACCESS_DENIED | No permission to access query | 403 |
| DLI-782 | QUERY_CANCEL_FAILED | Failed to cancel query | 500 |
| DLI-783 | QUERY_INVALID_FILTER | Invalid filter parameter values | 400 |
| DLI-784 | QUERY_SERVER_ERROR | Server-side error | 500 |

### 7.2 Exception Handling Example

```python
from dli import QueryAPI, ExecutionContext, ExecutionMode
from dli.exceptions import (
    QueryNotFoundError,
    QueryAccessDeniedError,
    QueryCancelError,
    QueryInvalidFilterError
)

api = QueryAPI(context=ExecutionContext(execution_mode=ExecutionMode.SERVER))

try:
    detail = api.get("nonexistent_query_id")
except QueryNotFoundError as e:
    print(f"Query not found: {e.error_code}")  # DLI-780
except QueryAccessDeniedError as e:
    print(f"Access denied: {e.error_code}")  # DLI-781

try:
    api.cancel(query_id="bq_job_abc123")
except QueryCancelError as e:
    print(f"Cancel failed: {e.message}")  # DLI-782
```

---

## 8. Test Coverage

### 8.1 Test Summary

| Category | File | Tests | Description |
|----------|------|-------|-------------|
| CLI | `tests/cli/test_query_cmd.py` | 50 | Command invocation, output formatting |
| API | `tests/api/test_query_api.py` | 54 | API method behavior, error handling |
| Models | `tests/core/query/test_models.py` | 66 | Model construction, validation |
| **Total** | | **170** | |

### 8.2 Test Coverage Areas

| Area | Tests | Description |
|------|-------|-------------|
| Scope filtering | 12 | my, system, user, all scope behavior |
| State filtering | 8 | pending, running, success, failed, cancelled |
| Tag filtering | 6 | Single tag, multiple tags (AND logic) |
| Time range | 8 | since, until, combined ranges |
| Pagination | 6 | limit, offset, edge cases |
| Cancel operations | 15 | By ID, by user, dry-run, force |
| Error handling | 20 | All DLI-78x error codes |
| Output formats | 10 | table, json for all commands |
| Mock data | 15 | Mock data completeness |
| Model validation | 66 | Field validation, defaults |

---

## 9. Known Limitations

| Limitation | Description | Future Phase |
|------------|-------------|--------------|
| No result streaming | Full result must fit in memory | Phase 2 |
| No query history retention | Only active/recent queries | Phase 2 |
| No cost estimation | No query cost preview | Phase 2 |
| No query templates | No saved query support | Phase 2 |

---

## 10. Future Work

### Phase 2 (Enhanced Features)

- [ ] Query result streaming for large results
- [ ] Query cost estimation before execution
- [ ] Saved query templates
- [ ] Query history with retention policy
- [ ] Query performance analytics

### Phase 3 (Advanced Features)

- [ ] Query scheduling
- [ ] Query approval workflow
- [ ] Cross-engine query federation
- [ ] Query caching with TTL

---

## 11. Quality Metrics

| Metric | Value |
|--------|-------|
| pyright errors | 0 |
| ruff violations | 0 |
| Test count | 170 |
| Test coverage | ~95% |

---

**Last Updated:** 2026-01-01
