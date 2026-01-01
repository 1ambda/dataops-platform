# FEATURE: Query Command - Query Execution Metadata

> **Version:** 1.1.0
> **Status:** ✅ Complete (v1.0.0)
> **Created:** 2026-01-01
> **Last Updated:** 2026-01-01
> **Implementation:** [QUERY_RELEASE.md](./QUERY_RELEASE.md)
> **Benchmarks:** Databricks CLI, BigQuery bq, dbt Cloud, SqlMesh

---

## 1. Overview

### 1.1 Purpose

`dli query` provides access to query execution metadata from Basecamp Server's catalog. Users can discover, filter, and analyze historical query executions across the platform: their own queries, system-generated queries, and queries from other users.

> **Note:** This command retrieves query **metadata** (state, timing, tables used), not query results.

### 1.2 Core Principles

| Principle | Description |
|-----------|-------------|
| **Server-Based** | All metadata comes from Basecamp Server catalog API (not direct query engine access) |
| **Scope-Based Filtering** | Unified `--scope` option replaces separate subcommands for cleaner UX |
| **Tag Abstraction** | Unified `--tag` option abstracts Trino Query Tags and BigQuery Job Labels |
| **Pagination Ready** | Designed for large result sets with offset-based pagination |
| **Lineage Integration** | Used tables can be linked to catalog for impact analysis |

### 1.3 Key Features

| Feature | Status | Description |
|---------|--------|-------------|
| **Scope Filtering** | ✅ Complete | Filter by `my`, `system`, `user`, or `all` queries via `--scope` |
| **Account Search** | ✅ Complete | Optional keyword filters by account name |
| **SQL Search** | ✅ Complete | `--sql` option searches query text content |
| **Status Filtering** | ✅ Complete | Filter by pending, running, success, failed, or cancelled |
| **Tag Filtering** | ✅ Complete | Cross-engine tag/label filtering |
| **Query Detail** | ✅ Complete | Deep dive into specific query execution |
| **Query Cancellation** | ✅ Complete | Cancel specific query or all running queries for an account |

### 1.4 Industry Benchmarking

| Tool | Key Features | Applied to dli |
|------|--------------|----------------|
| **Databricks CLI** | `query-history list --filter-by-user-id`, `--filter-by-warehouse-id`, `--filter-by-status` | Scope-based filtering, status filter |
| **BigQuery bq** | `bq ls -j --project_id`, `bq show --job=true`, `INFORMATION_SCHEMA.JOBS` view | Job listing, detailed view, max results |
| **dbt Cloud** | Model Query History via Discovery API, `run_results.json` artifact | Metadata focus, API-first design |
| **SqlMesh** | INFORMATION_SCHEMA queries, schema-stored metadata | Consistent metadata model |

### 1.5 Existing System Integration Points

| Integration Area | Existing Pattern | Application |
|------------------|------------------|-------------|
| **CLI** | `commands/catalog.py` structure | Similar subcommand approach |
| **Library API** | `CatalogAPI` pattern | `QueryAPI` follows same facade pattern |
| **Models** | `core/catalog/models.py` | Extended with query models |
| **Client** | `BasecampClient.catalog_*` methods | Added `query_*` methods |
| **Exceptions** | DLI-7xx Catalog errors | Use DLI-78x sub-range for Query |

---

## 2. Query Account Types

### 2.1 Account Segmentation

| Account Type | Status | Description | Examples |
|--------------|--------|-------------|----------|
| **Personal** | ✅ Complete | Individual user accounts | `user@company.com`, `john.doe@corp.com` |
| **System** | ✅ Complete | Service/system accounts | `airflow-prod`, `dbt-runner`, `scheduled-job` |

### 2.2 Detection Logic (Server-Side)

The server determines account type based on:
- Email domain patterns
- Service account naming conventions
- Explicit account type metadata

The CLI passes the filter parameter; the server handles account classification.

---

## 3. CLI Design

### 3.1 Command Structure

```
dli query <subcommand> [arguments] [options]
```

| Subcommand | Status | Arguments | Description |
|------------|--------|-----------|-------------|
| `list` | ✅ Complete | `[ACCOUNT_KEYWORD]` | List queries with scope and filters |
| `show` | ✅ Complete | `<QUERY_ID>` | Show detailed query metadata |
| `cancel` | ✅ Complete | `<QUERY_ID>` or `--user ACCOUNT` | Cancel running query(s) |

### 3.2 Subcommand: `list` - List Queries

```bash
dli query list [ACCOUNT_KEYWORD] [options]
```

Lists queries with flexible scope-based filtering. The optional `ACCOUNT_KEYWORD` searches **account names only**.

**Key Options:**

| Option | Status | Type | Default | Description |
|--------|--------|------|---------|-------------|
| `--scope` | ✅ Complete | ENUM | `my` | Query scope: `my`, `system`, `user`, `all` |
| `--sql` | ✅ Complete | TEXT | - | Filter by SQL query text content |
| `--status` | ✅ Complete | ENUM | - | Filter by state |
| `--tag` | ✅ Complete | TEXT | - | Filter by tag (repeatable, AND logic) |
| `--limit` | ✅ Complete | INT | `10` | Maximum number of results |
| `--offset` | ✅ Complete | INT | `0` | Pagination offset |
| `--since` | ✅ Complete | TEXT | `24h` | Start time (ISO8601 or relative: `1h`, `7d`) |
| `--until` | ✅ Complete | TEXT | - | End time (ISO8601 or relative) |
| `--format` | ✅ Complete | ENUM | `table` | Output format: `table`, `json` |
| `--engine` | ✅ Complete | ENUM | - | Filter by engine: `bigquery`, `trino` |

**Scope Values:**

| Scope | Status | Description |
|-------|--------|-------------|
| `my` | ✅ Complete | Queries executed by current authenticated user (default) |
| `system` | ✅ Complete | Queries from system/service accounts |
| `user` | ✅ Complete | Queries from personal (non-system) accounts |
| `all` | ✅ Complete | All accessible queries regardless of account type |

**Examples:**

```bash
# My recent queries (default: scope=my, limit=10)
$ dli query list

# My failed queries only
$ dli query list --status failed

# System account queries matching "airflow" in account name
$ dli query list airflow --scope system

# All queries containing specific SQL pattern
$ dli query list --scope all --sql "SELECT * FROM users"

# User queries with specific tag
$ dli query list --scope user --tag "experiment::ab_test_v2"
```

### 3.3 Subcommand: `show` - Query Detail

```bash
dli query show <QUERY_ID> [options]
```

Displays detailed metadata for a specific query execution.

| Option | Status | Type | Default | Description |
|--------|--------|------|---------|-------------|
| `--full-query` | ✅ Complete | FLAG | `false` | Show complete query text (not truncated) |
| `--format` | ✅ Complete | ENUM | `table` | Output format: `table`, `json` |

**Example:**

```bash
# Show query detail
$ dli query show bq_job_abc123

# Show with full query text
$ dli query show bq_job_abc123 --full-query

# JSON output
$ dli query show bq_job_abc123 --format json
```

### 3.4 Subcommand: `cancel` - Cancel Running Query(s)

```bash
dli query cancel <QUERY_ID> [options]
dli query cancel --user <ACCOUNT> [options]
```

Cancels running query(s). Can target a specific query by ID or all running queries for an account.

| Argument/Option | Status | Type | Default | Description |
|-----------------|--------|------|---------|-------------|
| `QUERY_ID` | ✅ Complete | TEXT | - | Specific query ID to cancel (mutually exclusive with `--user`) |
| `--user` | ✅ Complete | TEXT | - | Cancel all running queries for specified account |
| `--dry-run` | ✅ Complete | FLAG | `false` | Show what would be cancelled without executing |
| `--force` | ✅ Complete | FLAG | `false` | Skip confirmation prompt |
| `--format` | ✅ Complete | ENUM | `table` | Output format: `table`, `json` |

**Examples:**

```bash
# Cancel specific query
$ dli query cancel bq_job_abc123

# Cancel all running queries for a system account
$ dli query cancel --user airflow-prod

# Dry run - see what would be cancelled
$ dli query cancel --user dbt-runner --dry-run

# Force cancel without confirmation
$ dli query cancel --user airflow-prod --force
```

---

## 4. API Design (QueryAPI)

### 4.1 QueryAPI Class

| Method | Status | Returns | Description |
|--------|--------|---------|-------------|
| `list_queries()` | ✅ Complete | `QueryListResult` | List queries with scope and filters |
| `get()` | ✅ Complete | `QueryDetail` | Get detailed query metadata |
| `get_result()` | ✅ Complete | `QueryResultData` | Get query result data (if available) |
| `cancel()` | ✅ Complete | `QueryCancelResult` | Cancel query(s) by ID or user |

**Key Features:**
- ✅ Unified `list_queries()` method with scope parameter
- ✅ Dependency injection support (`client` parameter)
- ✅ Mock mode support via `ExecutionContext`
- ✅ Comprehensive error handling with DLI-78x codes

**Usage Example:**

```python
from dli import QueryAPI, ExecutionContext, ExecutionMode
from dli.core.query.models import QueryScope, QueryState

ctx = ExecutionContext(
    execution_mode=ExecutionMode.SERVER,
    server_url="http://basecamp:8080",
)
api = QueryAPI(context=ctx)

# List my queries
result = api.list_queries()

# List system queries with filters
result = api.list_queries(
    scope=QueryScope.SYSTEM,
    account_keyword="airflow",
    status=QueryState.FAILED,
)

# Get query detail
detail = api.get("bq_job_abc123")

# Cancel query
result = api.cancel(query_id="bq_job_abc123")
```

### 4.2 Result Models

| Model | Status | Purpose |
|-------|--------|---------|
| `QueryListResult` | ✅ Complete | List operation results with pagination metadata |
| `QueryDetailResult` | ✅ Complete | Detailed query information |
| `QueryCancelResult` | ✅ Complete | Cancellation operation results |

---

## 5. Data Models

### 5.1 Core Models

| Model | Status | Purpose |
|-------|--------|---------|
| `AccountType` | ✅ Complete | Enum for personal/system account types |
| `QueryScope` | ✅ Complete | Enum for my/system/user/all scopes |
| `QueryState` | ✅ Complete | Enum for pending/running/success/failed/cancelled states |
| `TableReference` | ✅ Complete | Table metadata with read/write access type |
| `QueryInfo` | ✅ Complete | Summary query information for list views |
| `QueryDetail` | ✅ Complete | Detailed query execution metadata |
| `QueryResources` | ✅ Complete | Resource usage metrics (bytes, slots, etc.) |

---

## 6. Error Codes (DLI-78x)

Query errors use a sub-range of Catalog errors (DLI-7xx):

| Code | Status | Name | Description |
|------|--------|------|-------------|
| DLI-780 | ✅ Complete | QUERY_NOT_FOUND | Query ID not found |
| DLI-781 | ✅ Complete | QUERY_ACCESS_DENIED | Access denied to query |
| DLI-782 | ✅ Complete | QUERY_CANCEL_FAILED | Failed to cancel query |
| DLI-783 | ✅ Complete | QUERY_INVALID_FILTER | Invalid filter parameter values |
| DLI-784 | ✅ Complete | QUERY_SERVER_ERROR | Server-side error |

**Exception Classes:**
- ✅ `QueryNotFoundError`
- ✅ `QueryAccessDeniedError`
- ✅ `QueryCancelError`
- ✅ `QueryInvalidFilterError`

---

## 7. Basecamp Server API Endpoints

| Operation | Status | Method | Endpoint |
|-----------|--------|--------|----------|
| List Queries | ✅ Complete | GET | `/api/v1/catalog/queries` |
| Query Detail | ✅ Complete | GET | `/api/v1/catalog/queries/{query_id}` |
| Cancel Query | ✅ Complete | POST | `/api/v1/catalog/queries/{query_id}/cancel` |
| Cancel by Account | ✅ Complete | POST | `/api/v1/catalog/queries/cancel?user={account}` |

---

## 8. Success Criteria

### 8.1 Feature Completion

| Feature | Status | Completion Condition |
|---------|--------|---------------------|
| QueryAPI | ✅ Complete | `list_queries()` works with all scope values in mock mode |
| CLI list | ✅ Complete | `dli query list --scope my` returns formatted output |
| CLI show | ✅ Complete | `dli query show QUERY_ID` shows detail |
| CLI cancel | ✅ Complete | `dli query cancel` works for ID and `--user` |
| Filters | ✅ Complete | `--status`, `--sql`, `--tag`, `--limit` work correctly |
| Error handling | ✅ Complete | DLI-78x codes return appropriate messages |

### 8.2 Test Quality

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Unit test coverage | >= 80% | ~95% | ✅ Complete |
| Mock mode tests | Each scope value has mock test | 170 tests | ✅ Complete |
| CLI command tests | Each subcommand tested | 50 tests | ✅ Complete |

### 8.3 Code Quality

| Principle | Status | Verification |
|-----------|--------|--------------|
| **Single Responsibility** | ✅ Complete | QueryAPI delegates to BasecampClient |
| **Consistent Pattern** | ✅ Complete | Follows CatalogAPI facade pattern |
| **Dependency Inversion** | ✅ Complete | QueryAPI accepts optional client via DI |

---

## 9. Implementation Summary

### 9.1 Completed Components

| Component | Status | Location |
|-----------|--------|----------|
| Core Models | ✅ Complete | `core/query/models.py` |
| Result Models | ✅ Complete | `models/query.py` |
| QueryAPI | ✅ Complete | `api/query.py` |
| CLI Commands | ✅ Complete | `commands/query.py` |
| Client Methods | ✅ Complete | `core/client.py` (query_list, query_get, query_cancel) |
| Error Codes | ✅ Complete | `exceptions.py` (DLI-780 to DLI-784) |
| Mock Data | ✅ Complete | `core/client.py` (MOCK_QUERY_DATA) |
| Tests | ✅ Complete | 170 tests (CLI: 50, API: 54, Models: 66) |

### 9.2 Quality Metrics

| Metric | Value | Status |
|--------|-------|--------|
| pyright errors | 0 | ✅ Pass |
| ruff violations | 0 | ✅ Pass |
| Test count | 170 | ✅ Pass |
| Test coverage | ~95% | ✅ Pass |

---

## Appendix A: Design Decisions

| # | Topic | Decision | Rationale |
|---|-------|----------|-----------|
| 1 | Subcommand consolidation | Unified `list` with `--scope` | Cleaner UX, single method for all scopes |
| 2 | `--scope` default | `my` | Users typically want their own queries first |
| 3 | Keyword search target | Account name only | Clear separation from SQL search |
| 4 | SQL text search | `--sql` option | Explicit option for query content search |
| 5 | Pagination | Offset-based | Simpler, matches catalog pattern |
| 6 | Default `--limit` | `10` | Queries include more metadata than catalog items |
| 7 | Default time filter | `24h` | Balance between scope and performance |
| 8 | Time filter syntax | ISO8601 + relative | Both formats, server handles parsing |
| 9 | Tag filter logic | AND | Precise filtering with multiple tags |
| 10 | Query text in list | ID only | Full preview in `show` command |
| 11 | Cancellation scope | Query ID or `--user` | Batch cancellation via account |
| 12 | Account type display | Visible in list | Important for filtering context |
| 13 | Error code range | DLI-78x | Sub-range of Catalog (7xx) |
| 14 | API pattern | Facade over `BasecampClient` | Consistent with `CatalogAPI`, `WorkflowAPI` |
| 15 | Mock priority | Full mock support first | Enable development without server |
| 16 | `--status` short form | `-S` (uppercase) | Avoid conflict with `dataset list --source/-s` |
| 17 | `--scope` new pattern | Ownership-based filtering | New pattern for future commands to adopt |

---

## Appendix B: Review Log

### Domain Implementer Review (feature-interface-cli)

**Date**: 2026-01-01

| Priority | Issue | Resolution |
|----------|-------|------------|
| High | Separate subcommands (`my`, `system`, `user`, `all`) add complexity | Consolidated into `list` with `--scope` option |
| High | `KEYWORD` searching both account and SQL is confusing | Split into positional `ACCOUNT_KEYWORD` and `--sql` option |
| High | `--max/-n` conflicts with catalog.py `--limit/-n` | Changed to `--limit/-n` |
| High | `--failed/--running/--success` flags inconsistent | Replaced with `--status/-S` enum |
| High | `-e` short form conflicts with workflow `-e` | Removed short form from `--engine` |
| Medium | QueryCancelResult model missing | Added to Result Models |
| Medium | Cancel endpoints not documented | Added to API Endpoints section |

### Technical Review (expert-python)

**Date**: 2026-01-01

| Priority | Issue | Resolution |
|----------|-------|------------|
| High | Missing QueryCancelResult import in API | Added to imports |
| High | cancel() uses ValueError instead of DLIError | Use ConfigurationError for validation |
| Medium | BasecampClient creation differs from CatalogAPI | Follow ServerConfig pattern |
| Medium | state and failed_only are mutually exclusive | Removed `failed_only`, use `--status failed` |
| Medium | Engine type repeated, should use TypeAlias | Define QueryEngine type |
| Medium | QueryScope enum missing | Added to core models |

---

**Last Updated:** 2026-01-01
