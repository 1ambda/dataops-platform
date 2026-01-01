# FEATURE: Query Command - Query Execution Metadata

> **Version:** 1.1.0
> **Status:** ✅ Implemented (v0.5.0)
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

| Feature | Description |
|---------|-------------|
| **Scope Filtering** | Filter by `my`, `system`, `user`, or `all` queries via `--scope` |
| **Account Search** | Optional keyword filters by account name |
| **SQL Search** | `--sql` option searches query text content |
| **Status Filtering** | Filter by pending, running, success, failed, or cancelled |
| **Tag Filtering** | Cross-engine tag/label filtering |
| **Query Detail** | Deep dive into specific query execution |
| **Query Cancellation** | Cancel specific query or all running queries for an account |

### 1.4 Industry Benchmarking

| Tool | Key Features | Applied to dli |
|------|--------------|----------------|
| **Databricks CLI** | `query-history list --filter-by-user-id`, `--filter-by-warehouse-id`, `--filter-by-status` | Scope-based filtering, status filter |
| **BigQuery bq** | `bq ls -j --project_id`, `bq show --job=true`, `INFORMATION_SCHEMA.JOBS` view | Job listing, detailed view, max results |
| **dbt Cloud** | Model Query History via Discovery API, `run_results.json` artifact | Metadata focus, API-first design |
| **SqlMesh** | INFORMATION_SCHEMA queries, schema-stored metadata | Consistent metadata model |

### 1.5 Existing System Integration Points

| Integration Area | Existing Pattern | New Feature Application |
|------------------|------------------|-------------------------|
| **CLI** | `commands/catalog.py` structure | Similar subcommand approach |
| **Library API** | `CatalogAPI` pattern | `QueryAPI` follows same facade pattern |
| **Models** | `core/catalog/models.py` | Extend with query models |
| **Client** | `BasecampClient.catalog_*` methods | Add `query_*` methods |
| **Exceptions** | DLI-7xx Catalog errors | Use DLI-78x sub-range for Query |

---

## 2. Query Account Types

### 2.1 Account Segmentation

| Account Type | Description | Examples |
|--------------|-------------|----------|
| **Personal** | Individual user accounts | `user@company.com`, `john.doe@corp.com` |
| **System** | Service/system accounts | `airflow-prod`, `dbt-runner`, `scheduled-job` |

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

| Subcommand | Arguments | Description |
|------------|-----------|-------------|
| `list` | `[ACCOUNT_KEYWORD]` | List queries with scope and filters |
| `show` | `<QUERY_ID>` | Show detailed query metadata |
| `cancel` | `<QUERY_ID>` or `--user ACCOUNT` | Cancel running query(s) |

### 3.2 Subcommand: `list` - List Queries

```bash
dli query list [ACCOUNT_KEYWORD] [options]
```

Lists queries with flexible scope-based filtering. The optional `ACCOUNT_KEYWORD` searches **account names only**.

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--scope` | | ENUM | `my` | Query scope: `my`, `system`, `user`, `all` |
| `--sql` | | TEXT | - | Filter by SQL query text content |
| `--status` | `-S` | ENUM | - | Filter by state |
| `--tag` | `-t` | TEXT | - | Filter by tag (repeatable, AND logic) |
| `--limit` | `-n` | INT | `10` | Maximum number of results |
| `--offset` | | INT | `0` | Pagination offset |
| `--since` | | TEXT | `24h` | Start time (ISO8601 or relative: `1h`, `7d`) |
| `--until` | | TEXT | - | End time (ISO8601 or relative) |
| `--format` | `-f` | ENUM | `table` | Output format: `table`, `json` |
| `--engine` | | ENUM | - | Filter by engine: `bigquery`, `trino` |

**Status Values:** `pending`, `running`, `success`, `failed`, `cancelled`

**Scope Values:**

| Scope | Description |
|-------|-------------|
| `my` | Queries executed by current authenticated user (default) |
| `system` | Queries from system/service accounts |
| `user` | Queries from personal (non-system) accounts |
| `all` | All accessible queries regardless of account type |

**Examples:**

```bash
# My recent queries (default: scope=my, limit=10)
$ dli query list
QUERY_ID                              STATE     STARTED              DURATION  TABLES
bq_job_abc123                         SUCCESS   2026-01-01 10:30:45  12.5s     3
bq_job_def456                         FAILED    2026-01-01 09:15:22  0.8s      1
trino_query_xyz789                    SUCCESS   2025-12-31 23:45:00  45.2s     5

Showing 3 of 156 queries

# My failed queries only
$ dli query list --status failed
QUERY_ID                              STATE     STARTED              ERROR
bq_job_def456                         FAILED    2026-01-01 09:15:22  Quota exceeded

# My queries with specific tag
$ dli query list --tag "team::analytics"

# My queries from last 7 days
$ dli query list --since "7d" --limit 50

# System account queries matching "airflow" in account name
$ dli query list airflow --scope system
QUERY_ID                              ACCOUNT          STATE     STARTED              DURATION
airflow_job_001                       airflow-prod     SUCCESS   2026-01-01 00:00:00  120.5s
airflow_job_002                       airflow-dev      RUNNING   2026-01-01 10:45:00  -

# All queries containing specific SQL pattern
$ dli query list --scope all --sql "SELECT * FROM users"
QUERY_ID                              ACCOUNT               STATE     STARTED
bq_job_users_001                      alice@company.com     SUCCESS   2026-01-01 10:30:45
trino_users_xyz                       dbt-runner            SUCCESS   2026-01-01 09:15:00

# User queries with specific tag
$ dli query list --scope user --tag "experiment::ab_test_v2"

# All BigQuery queries matching account keyword
$ dli query list alice --scope all --engine bigquery

# Failed system queries from last 24 hours
$ dli query list --scope system --status failed --since "24h"

# Combine account keyword and SQL filter
$ dli query list airflow --scope system --sql "daily_metrics"

# JSON output for programmatic access
$ dli query list --scope my --format json
```

**Search Behavior:**

| Filter | Searches |
|--------|----------|
| `ACCOUNT_KEYWORD` (positional) | Account name only |
| `--sql` | Query SQL text content |

Both can be combined for precise filtering.

### 3.3 Subcommand: `show` - Query Detail

```bash
dli query show <QUERY_ID> [options]
```

Displays detailed metadata for a specific query execution.

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--full-query` | | FLAG | `false` | Show complete query text (not truncated) |
| `--format` | `-f` | ENUM | `table` | Output format: `table`, `json` |

**Example Output:**

```bash
$ dli query show bq_job_abc123

Query Details:
  Query ID:     bq_job_abc123
  Engine:       bigquery
  State:        SUCCESS
  Account:      alice@company.com
  Account Type: personal

Timing:
  Started:      2026-01-01 10:30:45 UTC
  Finished:     2026-01-01 10:30:57 UTC
  Duration:     12.5s
  Queue Time:   0.2s

Resources:
  Bytes Processed:  1.2 GB
  Bytes Billed:     1.2 GB
  Slot Time:        45.0s

Tables Used:
  - bigquery.analytics.raw_events (read)
  - bigquery.analytics.users (read)
  - bigquery.analytics.daily_metrics (write)

Tags:
  - team::analytics
  - pipeline::daily_aggregation
  - version::v2.3.1

Query Preview:
  SELECT user_id, COUNT(*) as event_count
  FROM analytics.raw_events
  WHERE event_date = '2026-01-01'
  GROUP BY user_id
  ...truncated (2500 chars)
```

**Examples:**

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

**Arguments & Options:**

| Argument/Option | Short | Type | Default | Description |
|-----------------|-------|------|---------|-------------|
| `QUERY_ID` | | TEXT | - | Specific query ID to cancel (mutually exclusive with `--user`) |
| `--user` | | TEXT | - | Cancel all running queries for specified account |
| `--dry-run` | | FLAG | `false` | Show what would be cancelled without executing |
| `--force` | | FLAG | `false` | Skip confirmation prompt |
| `--format` | `-f` | ENUM | `table` | Output format: `table`, `json` |

**Examples:**

```bash
# Cancel specific query
$ dli query cancel bq_job_abc123
Cancelling query bq_job_abc123...
Query cancelled successfully

# Cancel all running queries for a system account
$ dli query cancel --user airflow-prod
Found 3 running queries for account 'airflow-prod':
  - airflow_job_001 (running for 5m 23s)
  - airflow_job_002 (running for 2m 10s)
  - airflow_job_003 (running for 45s)

Cancel all 3 queries? [y/N]: y
Cancelled 3 queries

# Cancel all running queries for a user account
$ dli query cancel --user alice@company.com
Found 1 running query for account 'alice@company.com':
  - trino_xyz789 (running for 12m 45s)

Cancel 1 query? [y/N]: y
Cancelled 1 query

# Dry run - see what would be cancelled
$ dli query cancel --user dbt-runner --dry-run
[DRY RUN] Would cancel 2 running queries for account 'dbt-runner':
  - dbt_run_001 (running for 8m 15s)
  - dbt_run_002 (running for 3m 30s)

# Force cancel without confirmation
$ dli query cancel --user airflow-prod --force
Cancelled 3 queries for account 'airflow-prod'
```

**Error Handling:**

| Scenario | Behavior |
|----------|----------|
| Query not found | `[DLI-780] Query 'xyz' not found` |
| Query already completed | Warning: `Query 'abc' already completed (state: SUCCESS)` |
| No running queries for account | `No running queries found for account 'xyz'` |
| Access denied | `[DLI-781] Access denied to cancel query 'abc'` |

---

## 4. API Design (QueryAPI)

### 4.1 QueryAPI Class

```python
# dli/api/query.py

from __future__ import annotations

from datetime import datetime
from typing import Literal

from dli.core.client import BasecampClient
from dli.core.query.models import (
    QueryDetail,
    QueryInfo,
    QueryScope,
    QueryState,
)
from dli.exceptions import (
    ConfigurationError,
    ErrorCode,
    QueryAccessDeniedError,
    QueryNotFoundError,
)
from dli.models.common import ExecutionContext, ExecutionMode, ResultStatus
from dli.models.query import (
    QueryCancelResult,
    QueryDetailResult,
    QueryListResult,
)

__all__ = ["QueryAPI"]


class QueryAPI:
    """Library API for query execution metadata.

    Provides programmatic access to query execution history and metadata
    from the Basecamp Server catalog.

    Example:
        >>> from dli import QueryAPI, ExecutionContext, ExecutionMode
        >>> from dli.core.query.models import QueryScope
        >>> ctx = ExecutionContext(
        ...     execution_mode=ExecutionMode.SERVER,
        ...     server_url="http://basecamp:8080",
        ... )
        >>> api = QueryAPI(context=ctx)
        >>> # List my queries (default scope)
        >>> result = api.list_queries(limit=20)
        >>> for query in result.queries:
        ...     print(f"{query.query_id}: {query.state}")
        >>>
        >>> # List system queries with account filter
        >>> result = api.list_queries(
        ...     scope=QueryScope.SYSTEM,
        ...     account_keyword="airflow",
        ...     status=QueryState.FAILED,
        ... )

    Attributes:
        context: Execution context with mode, server URL, etc.
    """

    def __init__(
        self,
        context: ExecutionContext | None = None,
        *,
        client: BasecampClient | None = None,  # DI for testing
    ) -> None:
        """Initialize QueryAPI.

        Args:
            context: Execution context. Defaults to ExecutionContext().
            client: Optional BasecampClient for dependency injection.
        """
        self.context = context or ExecutionContext()
        self._client = client

    def __repr__(self) -> str:
        return f"QueryAPI(context={self.context!r})"

    @property
    def _is_mock_mode(self) -> bool:
        """Check if running in mock mode."""
        return self.context.execution_mode == ExecutionMode.MOCK

    def _get_client(self) -> BasecampClient:
        """Get or create BasecampClient instance."""
        if self._client is not None:
            return self._client

        if self._is_mock_mode:
            return BasecampClient(mock_mode=True)

        if not self.context.server_url:
            raise ConfigurationError(
                message="server_url required for SERVER mode",
                code=ErrorCode.CONFIG_INVALID,
            )

        return BasecampClient(
            server_url=self.context.server_url,
            api_token=self.context.api_token,
            mock_mode=False,
        )

    # =========================================================================
    # List Queries (Unified Method)
    # =========================================================================

    def list_queries(
        self,
        *,
        scope: QueryScope = QueryScope.MY,
        account_keyword: str | None = None,
        sql_pattern: str | None = None,
        status: QueryState | None = None,
        tags: list[str] | None = None,
        engine: Literal["bigquery", "trino"] | None = None,
        since: datetime | str | None = None,
        until: datetime | str | None = None,
        limit: int = 10,
        offset: int = 0,
    ) -> QueryListResult:
        """List queries with flexible scope-based filtering.

        Args:
            scope: Query scope - MY, SYSTEM, USER, or ALL. Defaults to MY.
            account_keyword: Filter by account name (optional, searches account name only).
            sql_pattern: Filter by SQL query text content (optional).
            status: Filter by query state.
            tags: Filter by tags (AND logic).
            engine: Filter by query engine.
            since: Start time filter (datetime or relative string like "1h", "7d").
                   Defaults to "24h".
            until: End time filter.
            limit: Maximum number of results (default: 10).
            offset: Pagination offset (default: 0).

        Returns:
            QueryListResult with list of QueryInfo objects.

        Example:
            >>> # My failed queries (default scope=MY)
            >>> result = api.list_queries(status=QueryState.FAILED, since="24h")
            >>> for q in result.queries:
            ...     print(f"{q.query_id}: {q.error_message}")

            >>> # System queries for specific account
            >>> result = api.list_queries(
            ...     scope=QueryScope.SYSTEM,
            ...     account_keyword="airflow",
            ... )

            >>> # All queries with SQL pattern
            >>> result = api.list_queries(
            ...     scope=QueryScope.ALL,
            ...     sql_pattern="SELECT * FROM users",
            ... )

            >>> # Combine account and SQL filters
            >>> result = api.list_queries(
            ...     scope=QueryScope.SYSTEM,
            ...     account_keyword="airflow",
            ...     sql_pattern="daily_metrics",
            ... )
        """
        client = self._get_client()
        response = client.query_list(
            scope=scope.value,
            account_keyword=account_keyword,
            sql_pattern=sql_pattern,
            state=status.value if status else None,
            tags=tags,
            engine=engine,
            since=since.isoformat() if isinstance(since, datetime) else since,
            until=until.isoformat() if isinstance(until, datetime) else until,
            limit=limit,
            offset=offset,
        )

        if not response.success:
            # Handle errors based on response
            return QueryListResult(
                queries=[],
                total_count=0,
                has_more=False,
                status=ResultStatus.FAILURE,
            )

        data = response.data or {}
        queries = [QueryInfo.model_validate(q) for q in data.get("queries", [])]

        return QueryListResult(
            queries=queries,
            total_count=data.get("total_count", len(queries)),
            has_more=data.get("has_more", False),
            status=ResultStatus.SUCCESS,
        )

    # =========================================================================
    # Query Detail
    # =========================================================================

    def get(self, query_id: str, *, include_full_query: bool = False) -> QueryDetail:
        """Get detailed metadata for a specific query.

        Args:
            query_id: The query ID to retrieve.
            include_full_query: Include complete query text (not truncated).

        Returns:
            QueryDetail with full query metadata.

        Raises:
            QueryNotFoundError: If query ID not found.
            QueryAccessDeniedError: If access denied.

        Example:
            >>> detail = api.get("bq_job_abc123")
            >>> print(f"Duration: {detail.duration_seconds}s")
            >>> print(f"Tables: {[t.name for t in detail.tables_used]}")
        """
        client = self._get_client()
        response = client.query_get(
            query_id=query_id,
            include_full_query=include_full_query,
        )

        if not response.success:
            if response.status_code == 404:
                raise QueryNotFoundError(
                    message=f"Query '{query_id}' not found",
                    code=ErrorCode.QUERY_NOT_FOUND,
                    query_id=query_id,
                )
            if response.status_code == 403:
                raise QueryAccessDeniedError(
                    message=f"Access denied to query '{query_id}'",
                    code=ErrorCode.QUERY_ACCESS_DENIED,
                    query_id=query_id,
                )
            raise ConfigurationError(
                message=response.error or "Failed to get query",
                code=ErrorCode.QUERY_SERVER_ERROR,
            )

        return QueryDetail.model_validate(response.data)

    # =========================================================================
    # Query Cancellation
    # =========================================================================

    def cancel(
        self,
        query_id: str | None = None,
        *,
        user: str | None = None,
        dry_run: bool = False,
    ) -> QueryCancelResult:
        """Cancel running query(s).

        Must provide either query_id OR user, not both.

        Args:
            query_id: Specific query ID to cancel.
            user: Account name to cancel all running queries for.
            dry_run: If True, return what would be cancelled without executing.

        Returns:
            QueryCancelResult with cancelled query details.

        Raises:
            QueryNotFoundError: If query ID not found.
            QueryAccessDeniedError: If access denied.
            ConfigurationError: If both query_id and user are provided, or neither.

        Example:
            >>> # Cancel specific query
            >>> result = api.cancel(query_id="bq_job_abc123")
            >>> print(f"Cancelled: {result.cancelled_count}")

            >>> # Cancel all queries for an account
            >>> result = api.cancel(user="airflow-prod", dry_run=True)
            >>> print(f"Would cancel {len(result.queries)} queries")
        """
        # Validation
        if query_id and user:
            raise ConfigurationError(
                message="Cannot specify both query_id and user",
                code=ErrorCode.QUERY_INVALID_FILTER,
            )
        if not query_id and not user:
            raise ConfigurationError(
                message="Must specify either query_id or user",
                code=ErrorCode.QUERY_INVALID_FILTER,
            )

        client = self._get_client()
        response = client.query_cancel(
            query_id=query_id,
            user=user,
            dry_run=dry_run,
        )

        if not response.success:
            if response.status_code == 404:
                raise QueryNotFoundError(
                    message=f"Query '{query_id}' not found",
                    code=ErrorCode.QUERY_NOT_FOUND,
                    query_id=query_id or "",
                )
            if response.status_code == 403:
                raise QueryAccessDeniedError(
                    message=f"Access denied to cancel query",
                    code=ErrorCode.QUERY_ACCESS_DENIED,
                    query_id=query_id or "",
                )
            raise ConfigurationError(
                message=response.error or "Failed to cancel query",
                code=ErrorCode.QUERY_CANCEL_FAILED,
            )

        data = response.data or {}
        queries = [QueryInfo.model_validate(q) for q in data.get("queries", [])]

        return QueryCancelResult(
            cancelled_count=data.get("cancelled_count", len(queries)),
            queries=queries,
            dry_run=dry_run,
            status=ResultStatus.SUCCESS,
        )
```

### 4.2 Result Models

```python
# dli/models/query.py

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field

from dli.core.query.models import QueryInfo, QueryDetail
from dli.models.common import ResultStatus

__all__ = [
    "QueryListResult",
    "QueryDetailResult",
    "QueryCancelResult",
]


class QueryListResult(BaseModel):
    """Result of query list operations."""

    model_config = ConfigDict(frozen=True)

    queries: list[QueryInfo] = Field(default_factory=list, description="List of queries")
    total_count: int = Field(description="Total count of matching queries")
    has_more: bool = Field(default=False, description="Whether more results are available")
    status: ResultStatus = Field(description="Operation status")


class QueryDetailResult(BaseModel):
    """Result of query detail operation."""

    model_config = ConfigDict(frozen=True)

    query: QueryDetail = Field(description="Query detail")
    status: ResultStatus = Field(description="Operation status")


class QueryCancelResult(BaseModel):
    """Result of query cancellation operation."""

    model_config = ConfigDict(frozen=True)

    cancelled_count: int = Field(description="Number of queries cancelled")
    queries: list[QueryInfo] = Field(default_factory=list, description="Cancelled queries")
    dry_run: bool = Field(default=False, description="Whether this was a dry run")
    status: ResultStatus = Field(description="Operation status")
```

---

## 5. Data Models

### 5.1 Core Models

```python
# dli/core/query/models.py

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

__all__ = [
    "AccountType",
    "QueryScope",
    "QueryState",
    "QueryInfo",
    "QueryDetail",
    "TableReference",
    "QueryResources",
]


class AccountType(str, Enum):
    """Type of account that executed the query."""
    PERSONAL = "personal"
    SYSTEM = "system"


class QueryScope(str, Enum):
    """Scope for query listing."""
    MY = "my"          # Queries by current user
    SYSTEM = "system"  # Queries from system/service accounts
    USER = "user"      # Queries from personal (non-system) accounts
    ALL = "all"        # All accessible queries


class QueryState(str, Enum):
    """Query execution state."""
    PENDING = "pending"
    RUNNING = "running"
    SUCCESS = "success"
    FAILED = "failed"
    CANCELLED = "cancelled"


class TableReference(BaseModel):
    """Reference to a table used in a query."""

    model_config = ConfigDict(frozen=True)

    name: str = Field(description="Fully qualified table name")
    operation: Literal["read", "write"] = Field(description="Table operation type")
    alias: str | None = Field(default=None, description="Table alias in query")


class QueryInfo(BaseModel):
    """Summary information about a query execution.

    Used in list views for quick overview of query executions.
    """

    model_config = ConfigDict(frozen=True)

    query_id: str = Field(description="Unique query identifier")
    engine: Literal["bigquery", "trino"] = Field(description="Query engine")
    state: QueryState = Field(description="Current query state")
    account: str = Field(description="Account that executed the query")
    account_type: AccountType = Field(description="Type of account")
    started_at: datetime = Field(description="Query start time")
    finished_at: datetime | None = Field(default=None, description="Query finish time")
    duration_seconds: float | None = Field(default=None, description="Query duration in seconds")
    tables_used_count: int = Field(default=0, description="Number of tables used")
    error_message: str | None = Field(default=None, description="Error message if failed")
    tags: list[str] = Field(default_factory=list, description="Query tags/labels")

    @property
    def is_running(self) -> bool:
        """Check if query is currently running."""
        return self.state in (QueryState.PENDING, QueryState.RUNNING)

    @property
    def is_terminal(self) -> bool:
        """Check if query has reached terminal state."""
        return self.state in (QueryState.SUCCESS, QueryState.FAILED, QueryState.CANCELLED)


class QueryDetail(BaseModel):
    """Detailed information about a query execution.

    Includes full metadata, resource usage, and tables used.
    """

    model_config = ConfigDict(frozen=True)

    query_id: str = Field(description="Unique query identifier")
    engine: Literal["bigquery", "trino"] = Field(description="Query engine")
    state: QueryState = Field(description="Current query state")
    account: str = Field(description="Account that executed the query")
    account_type: AccountType = Field(description="Type of account")

    # Timing
    started_at: datetime = Field(description="Query start time")
    finished_at: datetime | None = Field(default=None, description="Query finish time")
    duration_seconds: float | None = Field(default=None, description="Query duration")
    queue_time_seconds: float | None = Field(default=None, description="Time spent in queue")

    # Resources
    bytes_processed: int | None = Field(default=None, description="Bytes processed")
    bytes_billed: int | None = Field(default=None, description="Bytes billed")
    slot_time_seconds: float | None = Field(default=None, description="Slot time")
    rows_affected: int | None = Field(default=None, description="Rows affected")

    # Tables
    tables_used: list[TableReference] = Field(default_factory=list, description="Tables used")

    # Metadata
    tags: list[str] = Field(default_factory=list, description="Query tags/labels")
    error_message: str | None = Field(default=None, description="Error message if failed")
    error_code: str | None = Field(default=None, description="Error code if failed")

    # Query text
    query_preview: str | None = Field(default=None, description="Truncated query text")
    query_text: str | None = Field(default=None, description="Full query text (if requested)")

    @property
    def tables_read(self) -> list[TableReference]:
        """Get tables read by the query."""
        return [t for t in self.tables_used if t.operation == "read"]

    @property
    def tables_written(self) -> list[TableReference]:
        """Get tables written by the query."""
        return [t for t in self.tables_used if t.operation == "write"]
```

---

## 6. BasecampClient Methods

### 6.1 New Methods

```python
# Add to dli/core/client.py

def query_list(
    self,
    *,
    scope: str = "my",
    account_keyword: str | None = None,
    sql_pattern: str | None = None,
    state: str | None = None,
    tags: list[str] | None = None,
    engine: str | None = None,
    since: str | None = None,
    until: str | None = None,
    limit: int = 10,
    offset: int = 0,
) -> ServerResponse:
    """List queries with unified scope-based filtering.

    Args:
        scope: Query scope - "my", "system", "user", or "all".
        account_keyword: Filter by account name.
        sql_pattern: Filter by SQL query text content.
        state: Filter by query state.
        tags: Filter by tags (AND logic).
        engine: Filter by query engine.
        since: Start time (ISO8601 or relative).
        until: End time.
        limit: Max results.
        offset: Pagination offset.

    Returns:
        ServerResponse with query list data.
    """
    if self.mock_mode:
        # Filter mock data based on scope
        mock_queries = self._get_mock_queries_by_scope(scope, account_keyword, sql_pattern)
        return ServerResponse(
            success=True,
            data={
                "queries": mock_queries[:limit],
                "total_count": len(mock_queries),
                "has_more": len(mock_queries) > limit,
            },
        )

    # GET /api/v1/catalog/queries?scope={scope}&account={keyword}&sql={pattern}&...
    params = {
        "scope": scope,
        "limit": limit,
        "offset": offset,
    }
    if account_keyword:
        params["account"] = account_keyword
    if sql_pattern:
        params["sql"] = sql_pattern
    if state:
        params["state"] = state
    if tags:
        params["tags"] = ",".join(tags)
    if engine:
        params["engine"] = engine
    if since:
        params["since"] = since
    if until:
        params["until"] = until

    return self._get("/api/v1/catalog/queries", params=params)


def _get_mock_queries_by_scope(
    self,
    scope: str,
    account_keyword: str | None,
    sql_pattern: str | None,
) -> list[dict]:
    """Filter mock queries based on scope and filters."""
    queries = MOCK_QUERY_DATA.get("all_queries", [])

    # Filter by scope
    if scope == "my":
        queries = [q for q in queries if q.get("account") == "current_user@company.com"]
    elif scope == "system":
        queries = [q for q in queries if q.get("account_type") == "system"]
    elif scope == "user":
        queries = [q for q in queries if q.get("account_type") == "personal"]
    # "all" returns everything

    # Filter by account keyword
    if account_keyword:
        queries = [q for q in queries if account_keyword.lower() in q.get("account", "").lower()]

    # Filter by SQL pattern
    if sql_pattern:
        queries = [q for q in queries if sql_pattern.lower() in q.get("query_preview", "").lower()]

    return queries


def query_get(
    self,
    query_id: str,
    *,
    include_full_query: bool = False,
) -> ServerResponse:
    """Get detailed query metadata."""
    if self.mock_mode:
        query = MOCK_QUERY_DATA.get("query_details", {}).get(query_id)
        if not query:
            return ServerResponse(success=False, error="Query not found", status_code=404)
        return ServerResponse(success=True, data=query)

    # GET /api/v1/catalog/queries/{query_id}
    params = {}
    if include_full_query:
        params["include_full_query"] = "true"

    return self._get(f"/api/v1/catalog/queries/{query_id}", params=params)


def query_cancel(
    self,
    query_id: str | None = None,
    *,
    user: str | None = None,
    dry_run: bool = False,
) -> ServerResponse:
    """Cancel running query(s).

    Args:
        query_id: Specific query ID to cancel.
        user: Account name to cancel all running queries for.
        dry_run: If True, return what would be cancelled without executing.

    Returns:
        ServerResponse with cancelled query details.
    """
    if self.mock_mode:
        if query_id:
            return ServerResponse(
                success=True,
                data={
                    "cancelled_count": 1,
                    "queries": [{"query_id": query_id, "state": "cancelled"}],
                },
            )
        elif user:
            mock_running = [
                q for q in MOCK_QUERY_DATA.get("all_queries", [])
                if q.get("account") == user and q.get("state") in ("pending", "running")
            ]
            return ServerResponse(
                success=True,
                data={
                    "cancelled_count": len(mock_running) if not dry_run else 0,
                    "queries": mock_running,
                },
            )
        return ServerResponse(success=False, error="Invalid request", status_code=400)

    # POST /api/v1/catalog/queries/{query_id}/cancel
    # POST /api/v1/catalog/queries/cancel?user={account}
    if query_id:
        return self._post(f"/api/v1/catalog/queries/{query_id}/cancel", json={"dry_run": dry_run})
    else:
        return self._post("/api/v1/catalog/queries/cancel", params={"user": user}, json={"dry_run": dry_run})
```

---

## 7. Error Codes (DLI-78x)

Query errors use a sub-range of Catalog errors (DLI-7xx):

```python
# Add to dli/exceptions.py

class ErrorCode(str, Enum):
    # ... existing codes ...

    # Query Errors (DLI-78x) - Sub-range of Catalog (DLI-7xx)
    QUERY_NOT_FOUND = "DLI-780"
    QUERY_ACCESS_DENIED = "DLI-781"
    QUERY_TIMEOUT = "DLI-782"
    QUERY_INVALID_FILTER = "DLI-783"
    QUERY_SERVER_ERROR = "DLI-784"
    QUERY_CANCEL_FAILED = "DLI-785"


@dataclass
class QueryNotFoundError(DLIError):
    """Query not found error."""
    code: ErrorCode = ErrorCode.QUERY_NOT_FOUND
    query_id: str = ""


@dataclass
class QueryAccessDeniedError(DLIError):
    """Query access denied error."""
    code: ErrorCode = ErrorCode.QUERY_ACCESS_DENIED
    query_id: str = ""
```

---

## 8. Basecamp Server API Endpoints

| Operation | Method | Endpoint |
|-----------|--------|----------|
| List Queries | GET | `/api/v1/catalog/queries` |
| Query Detail | GET | `/api/v1/catalog/queries/{query_id}` |
| Cancel Query | POST | `/api/v1/catalog/queries/{query_id}/cancel` |
| Cancel by Account | POST | `/api/v1/catalog/queries/cancel?user={account}` |

### Query Parameters for List Endpoint

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `scope` | enum | `my` | Query scope: `my`, `system`, `user`, `all` |
| `account` | string | - | Filter by account name (keyword search) |
| `sql` | string | - | Filter by SQL query text content |
| `state` | enum | - | Filter by state |
| `tags` | string[] | - | Filter by tags (comma-separated, AND logic) |
| `engine` | enum | - | Filter by engine: `bigquery`, `trino` |
| `since` | string | `24h` | Start time (ISO8601 or relative: `1h`, `7d`) |
| `until` | string | - | End time (ISO8601 or relative) |
| `limit` | int | `10` | Maximum results (max: 100) |
| `offset` | int | `0` | Pagination offset |

**State Values:** `pending`, `running`, `success`, `failed`, `cancelled`

---

## 9. Implementation Priority

### Phase 1: MVP

1. **Data models** (`core/query/models.py`): `QueryScope`, `QueryState`, `AccountType`, `QueryInfo`, `QueryDetail`
2. **QueryAPI class** with `list_queries()` method and mock support
3. **Result models** (`models/query.py`): `QueryListResult`, `QueryCancelResult`
4. **CLI `query list`** command with `--scope` option
5. **CLI `query show`** command
6. **CLI `query cancel`** command with `--user` option
7. **Error codes** (DLI-78x) and exceptions
8. **Mock data** in `client.py`
9. **Unit tests** for QueryAPI mock mode

### Phase 2: Filters and Output

1. **`--status` filter** implementation
2. **`--sql` filter** implementation
3. **`--tag` filter** implementation
4. **`--since`/`--until` time filters**
5. **`--engine` filter**
6. **Rich table output** with status coloring
7. **JSON output** format
8. **Pagination** (`--offset`)

### Phase 3: Server Integration

1. **Basecamp Server API** endpoints
2. **Tag abstraction layer** (Trino Query Tag / BigQuery Job Label)
3. **Integration tests** with real server
4. **Lineage integration** (tables used -> catalog lookup)

---

## 10. Success Criteria

### 10.1 Feature Completion

| Feature | Completion Condition |
|---------|---------------------|
| QueryAPI | `list_queries()` works with all scope values in mock mode |
| CLI list | `dli query list --scope my` returns formatted output |
| CLI show | `dli query show QUERY_ID` shows detail |
| Filters | `--status`, `--sql`, `--tag`, `--limit` work correctly |
| Error handling | DLI-78x codes return appropriate messages |

### 10.2 Test Quality

| Metric | Target | Measurement |
|--------|--------|-------------|
| Unit test coverage | >= 80% | `pytest --cov` |
| Mock mode tests | Each scope value has mock test | Test file count |
| CLI command tests | Each subcommand tested | `typer.testing.CliRunner` |

### 10.3 Code Quality

| Principle | Verification |
|-----------|--------------|
| **Single Responsibility** | QueryAPI delegates to BasecampClient |
| **Consistent Pattern** | Follows CatalogAPI facade pattern |
| **Dependency Inversion** | QueryAPI accepts optional client via DI |

---

## 11. Directory Structure

```
project-interface-cli/src/dli/
├── __init__.py           # Add QueryAPI export
├── api/
│   ├── __init__.py       # Add QueryAPI export
│   └── query.py          # NEW: QueryAPI class
├── models/
│   ├── __init__.py       # Add query model exports
│   └── query.py          # NEW: Result models
├── commands/
│   ├── __init__.py       # Add query_app export
│   └── query.py          # NEW: CLI commands
├── core/
│   ├── client.py         # ADD: query_* methods, MOCK_QUERY_DATA
│   └── query/            # NEW
│       ├── __init__.py
│       └── models.py     # QueryScope, QueryInfo, QueryDetail, etc.
└── exceptions.py         # ADD: DLI-78x codes, Query exceptions
```

---

## 12. Reference Patterns

| Implementation | Reference File | Pattern to Follow |
|----------------|----------------|-------------------|
| QueryAPI | `api/catalog.py` | Facade pattern, mock mode, DI |
| Result models | `models/common.py` | Pydantic BaseModel with Field |
| CLI commands | `commands/catalog.py` | Typer command, subcommands |
| Client methods | `core/client.py` | ServerResponse, mock_mode check |
| Exceptions | `exceptions.py` | DLIError inheritance, ErrorCode |

---

## Appendix A: Command Summary

### List Queries

```bash
dli query list [ACCOUNT_KEYWORD] [options]
```

| Option | Short | Default | Values |
|--------|-------|---------|--------|
| `--scope` | | `my` | `my`, `system`, `user`, `all` |
| `--sql` | | - | SQL text pattern |
| `--status` | `-S` | - | `pending`, `running`, `success`, `failed`, `cancelled` |
| `--tag` | `-t` | - | Tag value (repeatable, AND logic) |
| `--limit` | `-n` | `10` | Number |
| `--offset` | | `0` | Number |
| `--since` | | `24h` | ISO8601 or relative (`1h`, `7d`) |
| `--until` | | - | ISO8601 or relative |
| `--engine` | | - | `bigquery`, `trino` |
| `--format` | `-f` | `table` | `table`, `json` |

**Examples:**

```bash
dli query list                                    # My queries (default)
dli query list --status failed                    # My failed queries
dli query list --scope system                     # System queries
dli query list airflow --scope system             # System queries by 'airflow' accounts
dli query list --scope all --sql "SELECT * FROM" # All queries with SQL pattern
dli query list alice --scope user --tag team::ds # User 'alice' queries with tag
```

### Show Query Detail

```bash
dli query show <QUERY_ID> [--full-query] [--format json]
```

### Cancel Query

```bash
dli query cancel <QUERY_ID>
dli query cancel --user <ACCOUNT> [--dry-run] [--force]
```

---

## Appendix B: Design Decisions

| # | Topic | Decision | Rationale |
|---|-------|----------|-----------|
| 1 | Subcommand consolidation | Unified `list` with `--scope` | Cleaner UX, single method for all scopes |
| 2 | `--scope` default | `my` | Users typically want their own queries first |
| 3 | Keyword search target | Account name only | Clear separation from SQL search |
| 4 | SQL text search | `--sql` option | Explicit option for query content search |
| 5 | Pagination | Offset-based | Simpler, matches catalog pattern |
| 6 | Default `--limit` | `10` | Queries include more metadata than catalog items (10 vs 50 in catalog) |
| 7 | Default time filter | `24h` | Balance between scope and performance |
| 8 | Time filter syntax | ISO8601 + relative | Both formats, server handles parsing |
| 9 | Tag filter logic | AND | Precise filtering with multiple tags |
| 10 | Query text in list | ID only | Full preview in `show` command |
| 11 | Cancellation scope | Query ID or `--user` | Batch cancellation via account |
| 12 | Account type display | Visible in list | Important for filtering context |
| 13 | Lineage integration | Phase 3 | Requires catalog lookup implementation |
| 14 | Export to file | Phase 2 | Nice-to-have feature |
| 15 | Error code range | DLI-78x | Sub-range of Catalog (7xx) |
| 16 | API pattern | Facade over `BasecampClient` | Consistent with `CatalogAPI`, `WorkflowAPI` |
| 17 | Mock priority | Full mock support first | Enable development without server |
| 18 | `--status` short form | `-S` (uppercase) | Avoid conflict with `dataset list --source/-s` |
| 19 | `--scope` new pattern | Ownership-based filtering | New pattern for future commands to adopt |

---

## Appendix C: Review Log

> Feedback from implementation agents incorporated on 2026-01-01.

### Domain Implementer Review (feature-interface-cli)

**Date**: 2026-01-01

| Priority | Issue | Resolution |
|----------|-------|------------|
| High | Separate subcommands (`my`, `system`, `user`, `all`) add complexity | Consolidated into `list` with `--scope` option |
| High | `KEYWORD` searching both account and SQL is confusing | Split into positional `ACCOUNT_KEYWORD` and `--sql` option |
| High | `--max/-n` conflicts with catalog.py `--limit/-n` | Changed to `--limit/-n` |
| High | `--failed/--running/--success` flags inconsistent | Replaced with `--status/-S` enum (uppercase to avoid -s conflict) |
| High | `-e` short form conflicts with workflow `-e` | Removed short form from `--engine` |
| Medium | QueryCancelResult model missing | Added to Section 4.2 Result Models |
| Medium | Cancel endpoints not documented | Added to Section 8 API Endpoints |
| Low | Time filter default not documented | Added "default: 24h" to help text |

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
| Low | Missing test strategy examples | Added to Section 10 |

### v1.1.0 Changes (2026-01-01)

| Area | Before (v1.0.0) | After (v1.1.0) |
|------|-----------------|----------------|
| Command structure | Separate `my`, `system`, `user`, `all` subcommands | Unified `list` with `--scope` option |
| Scope default | N/A (separate commands) | `--scope my` |
| Account search | `[KEYWORD]` searched both account and SQL | `[ACCOUNT_KEYWORD]` searches account only |
| SQL search | Part of `[KEYWORD]` | Separate `--sql` option |
| API methods | `list_my_queries()`, `list_system_queries()`, etc. | Single `list_queries(scope=...)` |
| Client methods | `query_list_my()`, `query_list_system()`, etc. | Single `query_list(scope=...)` |

### v1.1.0 Review Findings (2026-01-01)

| Priority | Category | Issue | Resolution |
|----------|----------|-------|------------|
| High | Option Conflict | `--status/-s` conflicts with `dataset list --source/-s` | Changed short form to `-S` (uppercase) |
| High | New Pattern | `--scope` is a new concept not in other commands | Documented as intentional pattern for ownership filtering |
| High | Default Value | `--limit 10` differs from `catalog list --limit 50` | Kept 10 (queries heavier than catalog items), rationale in Appendix B |
| Medium | Time Parsing | `--since` relative time ("1h", "7d") needs implementation | Add `parse_relative_time()` to `commands/utils.py` |
| Medium | Missing Export | `QueryAPI` needs export in `__init__.py` files | Added to directory structure spec |
| Medium | Error Hierarchy | Query errors should have common base class | Add `QueryError(DLIError)` base |
| Low | Terminology | `cancel --user` vs `--account` inconsistency | Keep `--user` (matches Databricks CLI pattern) |
| Low | Mock Data | `MOCK_QUERY_DATA` needs definition in `client.py` | Specified in Phase 1 implementation |
