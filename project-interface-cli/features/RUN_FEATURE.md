# FEATURE: Run Command - Ad-hoc SQL Execution with Result Download

| Attribute | Value |
|-----------|-------|
| **Version** | 1.0.0 |
| **Status** | Implemented |
| **Created** | 2026-01-01 |
| **Last Updated** | 2026-01-01 |
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

### 5.1 RunAPI Class

```python
# File: dli/api/run.py

from __future__ import annotations

from pathlib import Path
from typing import Literal

from dli.core.client import BasecampClient
from dli.core.run.models import (
    ExecutionPlan,
    RunResult,
    OutputFormat,
)
from dli.exceptions import (
    ConfigurationError,
    ErrorCode,
    RunLocalDeniedError,
    RunServerUnavailableError,
    RunExecutionError,
    RunFileNotFoundError,
    RunOutputError,
)
from dli.models.common import ExecutionContext, ExecutionMode, ResultStatus

__all__ = ["RunAPI"]


class RunAPI:
    """Library API for ad-hoc SQL execution with result download.

    Executes SQL files against query engines and saves results to local files.
    Supports both local and server execution modes with server policy enforcement.

    Example:
        >>> from dli import RunAPI, ExecutionContext, ExecutionMode
        >>> from pathlib import Path
        >>> ctx = ExecutionContext(
        ...     execution_mode=ExecutionMode.SERVER,
        ...     server_url="http://basecamp:8080",
        ... )
        >>> api = RunAPI(context=ctx)
        >>> result = api.run(
        ...     sql_path=Path("query.sql"),
        ...     output_path=Path("results.csv"),
        ...     parameters={"date": "2026-01-01"},
        ... )
        >>> print(f"Rows: {result.row_count}, Duration: {result.duration_seconds}s")

    Attributes:
        context: Execution context with mode, server URL, etc.
    """

    def __init__(
        self,
        context: ExecutionContext | None = None,
        *,
        client: BasecampClient | None = None,  # DI for testing
    ) -> None:
        """Initialize RunAPI.

        Args:
            context: Execution context. Defaults to ExecutionContext().
            client: Optional BasecampClient for dependency injection.
        """
        self.context = context or ExecutionContext()
        self._client = client

    def __repr__(self) -> str:
        return f"RunAPI(context={self.context!r})"

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

        if self.context.execution_mode == ExecutionMode.SERVER:
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
    # Main Execution Method
    # =========================================================================

    def run(
        self,
        sql_path: Path,
        output_path: Path,
        *,
        output_format: OutputFormat = OutputFormat.CSV,
        parameters: dict[str, str] | None = None,
        limit: int | None = None,
        timeout: int = 300,
        dialect: Literal["bigquery", "trino"] = "bigquery",
        prefer_local: bool = False,
        prefer_server: bool = False,
    ) -> RunResult:
        """Execute SQL file and save results to output file.

        Args:
            sql_path: Path to SQL file.
            output_path: Path for output file.
            output_format: Output format (CSV, TSV, JSON). Default: CSV.
            parameters: Parameter substitutions for {{ param }} placeholders.
            limit: Maximum rows to return.
            timeout: Query timeout in seconds (1-3600). Default: 300.
            dialect: SQL dialect. Default: bigquery.
            prefer_local: Request local execution (server policy may override).
            prefer_server: Request server execution (server policy may override).

        Returns:
            RunResult with execution details and output file path.

        Raises:
            RunFileNotFoundError: If SQL file not found.
            RunLocalDeniedError: If server denies local execution request.
            RunServerUnavailableError: If server execution unavailable.
            RunExecutionError: If query execution fails.
            RunOutputError: If output file cannot be written.
            ConfigurationError: If both prefer_local and prefer_server are True.

        Example:
            >>> result = api.run(
            ...     sql_path=Path("query.sql"),
            ...     output_path=Path("results.csv"),
            ...     parameters={"date": "2026-01-01"},
            ...     limit=1000,
            ... )
            >>> print(f"Saved {result.row_count} rows to {result.output_path}")
        """
        # Validate inputs
        if prefer_local and prefer_server:
            raise ConfigurationError(
                message="Cannot specify both prefer_local and prefer_server",
                code=ErrorCode.CONFIG_INVALID,
            )

        if not sql_path.exists():
            raise RunFileNotFoundError(
                message=f"SQL file not found: {sql_path}",
                code=ErrorCode.RUN_FILE_NOT_FOUND,
                path=str(sql_path),
            )

        # Read and render SQL
        sql_content = sql_path.read_text(encoding="utf-8")
        rendered_sql = self._render_sql(sql_content, parameters or {})

        # Determine execution mode
        execution_mode = self._resolve_execution_mode(prefer_local, prefer_server)

        # Execute query
        if self._is_mock_mode:
            return self._mock_run(sql_path, output_path, output_format, rendered_sql)

        if execution_mode == ExecutionMode.LOCAL:
            return self._execute_local(
                sql_path=sql_path,
                output_path=output_path,
                output_format=output_format,
                rendered_sql=rendered_sql,
                dialect=dialect,
                limit=limit,
                timeout=timeout,
            )
        else:
            return self._execute_server(
                sql_path=sql_path,
                output_path=output_path,
                output_format=output_format,
                rendered_sql=rendered_sql,
                dialect=dialect,
                limit=limit,
                timeout=timeout,
            )

    def _render_sql(self, sql: str, parameters: dict[str, str]) -> str:
        """Render SQL with Jinja-style parameter substitution."""
        rendered = sql
        for key, value in parameters.items():
            rendered = rendered.replace(f"{{{{ {key} }}}}", value)
            rendered = rendered.replace(f"{{{{{key}}}}}", value)
        return rendered

    def _resolve_execution_mode(
        self, prefer_local: bool, prefer_server: bool
    ) -> ExecutionMode:
        """Resolve final execution mode based on preference and server policy."""
        if self._is_mock_mode:
            return ExecutionMode.MOCK

        # Check server policy
        client = self._get_client()
        policy = client.run_get_policy()

        if prefer_local:
            if not policy.data.get("allow_local", False):
                raise RunLocalDeniedError(
                    message="Local execution not permitted by server policy",
                    code=ErrorCode.RUN_LOCAL_DENIED,
                )
            return ExecutionMode.LOCAL

        if prefer_server:
            if not policy.data.get("server_available", True):
                raise RunServerUnavailableError(
                    message="Server execution unavailable",
                    code=ErrorCode.RUN_SERVER_UNAVAILABLE,
                )
            return ExecutionMode.SERVER

        # Use server default
        default_mode = policy.data.get("default_mode", "server")
        return ExecutionMode.LOCAL if default_mode == "local" else ExecutionMode.SERVER

    def _mock_run(
        self,
        sql_path: Path,
        output_path: Path,
        output_format: OutputFormat,
        rendered_sql: str,
    ) -> RunResult:
        """Mock execution for testing."""
        # Write mock output
        mock_data = [
            {"id": 1, "name": "mock_row_1", "value": 100},
            {"id": 2, "name": "mock_row_2", "value": 200},
        ]
        self._write_output(output_path, output_format, mock_data)

        return RunResult(
            status=ResultStatus.SUCCESS,
            sql_path=sql_path,
            output_path=output_path,
            output_format=output_format,
            row_count=len(mock_data),
            duration_seconds=0.0,
            execution_mode=ExecutionMode.MOCK,
            rendered_sql=rendered_sql,
        )

    def _execute_local(
        self,
        sql_path: Path,
        output_path: Path,
        output_format: OutputFormat,
        rendered_sql: str,
        dialect: str,
        limit: int | None,
        timeout: int,
    ) -> RunResult:
        """Execute query locally via direct engine connection."""
        # Implementation delegates to LocalExecutor
        from dli.core.executor import ExecutorFactory

        executor = ExecutorFactory.create(
            mode=ExecutionMode.LOCAL,
            context=self.context,
        )

        try:
            result_data = executor.execute(
                sql=rendered_sql,
                dialect=dialect,
                limit=limit,
                timeout=timeout,
            )
            self._write_output(output_path, output_format, result_data.rows)

            return RunResult(
                status=ResultStatus.SUCCESS,
                sql_path=sql_path,
                output_path=output_path,
                output_format=output_format,
                row_count=result_data.row_count,
                duration_seconds=result_data.duration_seconds,
                execution_mode=ExecutionMode.LOCAL,
                rendered_sql=rendered_sql,
            )
        except Exception as e:
            raise RunExecutionError(
                message=f"Query execution failed: {e}",
                code=ErrorCode.RUN_EXECUTION_FAILED,
                cause=str(e),
            ) from e

    def _execute_server(
        self,
        sql_path: Path,
        output_path: Path,
        output_format: OutputFormat,
        rendered_sql: str,
        dialect: str,
        limit: int | None,
        timeout: int,
    ) -> RunResult:
        """Execute query via Basecamp Server API."""
        client = self._get_client()
        response = client.run_execute(
            sql=rendered_sql,
            dialect=dialect,
            limit=limit,
            timeout=timeout,
        )

        if not response.success:
            raise RunExecutionError(
                message=response.error or "Server execution failed",
                code=ErrorCode.RUN_EXECUTION_FAILED,
                cause=response.error,
            )

        data = response.data or {}
        result_rows = data.get("rows", [])
        self._write_output(output_path, output_format, result_rows)

        return RunResult(
            status=ResultStatus.SUCCESS,
            sql_path=sql_path,
            output_path=output_path,
            output_format=output_format,
            row_count=data.get("row_count", len(result_rows)),
            duration_seconds=data.get("duration_seconds", 0.0),
            execution_mode=ExecutionMode.SERVER,
            rendered_sql=rendered_sql,
        )

    def _write_output(
        self,
        output_path: Path,
        output_format: OutputFormat,
        rows: list[dict],
    ) -> None:
        """Write result rows to output file."""
        import csv
        import json

        try:
            output_path.parent.mkdir(parents=True, exist_ok=True)

            if output_format == OutputFormat.JSON:
                with output_path.open("w", encoding="utf-8") as f:
                    for row in rows:
                        f.write(json.dumps(row, ensure_ascii=False, default=str) + "\n")

            elif output_format == OutputFormat.TSV:
                with output_path.open("w", encoding="utf-8", newline="") as f:
                    if rows:
                        writer = csv.DictWriter(f, fieldnames=rows[0].keys(), delimiter="\t")
                        writer.writeheader()
                        writer.writerows(rows)

            else:  # CSV
                with output_path.open("w", encoding="utf-8", newline="") as f:
                    if rows:
                        writer = csv.DictWriter(f, fieldnames=rows[0].keys())
                        writer.writeheader()
                        writer.writerows(rows)

        except OSError as e:
            raise RunOutputError(
                message=f"Cannot write output file: {output_path}",
                code=ErrorCode.RUN_OUTPUT_FAILED,
                path=str(output_path),
            ) from e

    # =========================================================================
    # Dry Run / Validation
    # =========================================================================

    def dry_run(
        self,
        sql_path: Path,
        output_path: Path,
        *,
        output_format: OutputFormat = OutputFormat.CSV,
        parameters: dict[str, str] | None = None,
        dialect: Literal["bigquery", "trino"] = "bigquery",
        prefer_local: bool = False,
        prefer_server: bool = False,
    ) -> ExecutionPlan:
        """Validate SQL and show execution plan without executing.

        Args:
            sql_path: Path to SQL file.
            output_path: Path for output file.
            output_format: Output format.
            parameters: Parameter substitutions.
            dialect: SQL dialect.
            prefer_local: Request local execution.
            prefer_server: Request server execution.

        Returns:
            ExecutionPlan with validation result and rendered SQL.

        Example:
            >>> plan = api.dry_run(
            ...     sql_path=Path("query.sql"),
            ...     output_path=Path("results.csv"),
            ...     parameters={"date": "2026-01-01"},
            ... )
            >>> print(f"Mode: {plan.execution_mode}, Valid: {plan.is_valid}")
            >>> print(f"SQL: {plan.rendered_sql}")
        """
        if not sql_path.exists():
            raise RunFileNotFoundError(
                message=f"SQL file not found: {sql_path}",
                code=ErrorCode.RUN_FILE_NOT_FOUND,
                path=str(sql_path),
            )

        sql_content = sql_path.read_text(encoding="utf-8")
        rendered_sql = self._render_sql(sql_content, parameters or {})

        # Resolve execution mode (may raise policy errors)
        try:
            execution_mode = self._resolve_execution_mode(prefer_local, prefer_server)
            mode_error = None
        except (RunLocalDeniedError, RunServerUnavailableError) as e:
            execution_mode = ExecutionMode.SERVER  # fallback for display
            mode_error = str(e)

        return ExecutionPlan(
            sql_path=sql_path,
            output_path=output_path,
            output_format=output_format,
            dialect=dialect,
            execution_mode=execution_mode,
            rendered_sql=rendered_sql,
            parameters=parameters or {},
            is_valid=mode_error is None,
            validation_error=mode_error,
        )

    # =========================================================================
    # SQL Rendering (Utility)
    # =========================================================================

    def render_sql(
        self,
        sql_path: Path,
        parameters: dict[str, str] | None = None,
    ) -> str:
        """Render SQL file with parameter substitution.

        Args:
            sql_path: Path to SQL file.
            parameters: Parameter substitutions.

        Returns:
            Rendered SQL string.

        Raises:
            RunFileNotFoundError: If SQL file not found.

        Example:
            >>> sql = api.render_sql(
            ...     sql_path=Path("query.sql"),
            ...     parameters={"date": "2026-01-01"},
            ... )
            >>> print(sql)
        """
        if not sql_path.exists():
            raise RunFileNotFoundError(
                message=f"SQL file not found: {sql_path}",
                code=ErrorCode.RUN_FILE_NOT_FOUND,
                path=str(sql_path),
            )

        sql_content = sql_path.read_text(encoding="utf-8")
        return self._render_sql(sql_content, parameters or {})
```

### 5.2 Result Models

```python
# File: dli/models/run.py

from __future__ import annotations

from enum import Enum
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field

from dli.models.common import ExecutionMode, ResultStatus

__all__ = [
    "OutputFormat",
    "RunResult",
    "ExecutionPlan",
]


class OutputFormat(str, Enum):
    """Output format for run results."""
    CSV = "csv"
    TSV = "tsv"
    JSON = "json"


class RunResult(BaseModel):
    """Result of SQL execution."""

    model_config = ConfigDict(frozen=True)

    status: ResultStatus = Field(description="Execution status")
    sql_path: Path = Field(description="Input SQL file path")
    output_path: Path = Field(description="Output file path")
    output_format: OutputFormat = Field(description="Output format used")
    row_count: int = Field(description="Number of rows returned")
    duration_seconds: float = Field(description="Execution duration in seconds")
    execution_mode: ExecutionMode = Field(description="Execution mode used")
    rendered_sql: str = Field(description="Rendered SQL after parameter substitution")
    bytes_processed: int | None = Field(default=None, description="Bytes processed")
    bytes_billed: int | None = Field(default=None, description="Bytes billed")

    @property
    def is_success(self) -> bool:
        """Check if execution was successful."""
        return self.status == ResultStatus.SUCCESS


class ExecutionPlan(BaseModel):
    """Execution plan for dry run."""

    model_config = ConfigDict(frozen=True)

    sql_path: Path = Field(description="Input SQL file path")
    output_path: Path = Field(description="Output file path")
    output_format: OutputFormat = Field(description="Output format")
    dialect: str = Field(description="SQL dialect")
    execution_mode: ExecutionMode = Field(description="Resolved execution mode")
    rendered_sql: str = Field(description="Rendered SQL")
    parameters: dict[str, str] = Field(default_factory=dict, description="Parameters used")
    is_valid: bool = Field(description="Whether execution plan is valid")
    validation_error: str | None = Field(default=None, description="Validation error if any")
```

---

## 6. Data Models

### 6.1 Core Models

```python
# File: dli/core/run/models.py

from __future__ import annotations

from enum import Enum
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field

__all__ = [
    "RunConfig",
    "ExecutionData",
]


class RunConfig(BaseModel):
    """Configuration for run execution."""

    model_config = ConfigDict(frozen=True)

    sql_path: Path = Field(description="Path to SQL file")
    output_path: Path = Field(description="Path for output file")
    output_format: str = Field(default="csv", description="Output format")
    parameters: dict[str, str] = Field(default_factory=dict, description="SQL parameters")
    limit: int | None = Field(default=None, description="Row limit")
    timeout: int = Field(default=300, description="Timeout in seconds")
    dialect: str = Field(default="bigquery", description="SQL dialect")


class ExecutionData(BaseModel):
    """Data returned from query execution."""

    model_config = ConfigDict(frozen=True)

    rows: list[dict] = Field(default_factory=list, description="Result rows")
    row_count: int = Field(description="Number of rows")
    duration_seconds: float = Field(description="Execution duration")
    bytes_processed: int | None = Field(default=None, description="Bytes processed")
    bytes_billed: int | None = Field(default=None, description="Bytes billed")
```

---

## 7. BasecampClient Methods

### 7.1 New Methods

```python
# File: dli/core/client.py (additions)

def run_get_policy(self) -> ServerResponse:
    """Get execution policy from server.

    Returns:
        ServerResponse with policy data:
        {
            "allow_local": bool,
            "server_available": bool,
            "default_mode": "local" | "server",
        }
    """
    if self.mock_mode:
        return ServerResponse(
            success=True,
            data={
                "allow_local": True,
                "server_available": True,
                "default_mode": "server",
            },
        )

    return self._get("/api/v1/run/policy")


def run_execute(
    self,
    sql: str,
    *,
    dialect: str = "bigquery",
    limit: int | None = None,
    timeout: int = 300,
) -> ServerResponse:
    """Execute SQL query via server.

    Args:
        sql: Rendered SQL to execute.
        dialect: SQL dialect.
        limit: Maximum rows.
        timeout: Timeout in seconds.

    Returns:
        ServerResponse with execution result:
        {
            "rows": list[dict],
            "row_count": int,
            "duration_seconds": float,
            "bytes_processed": int | None,
            "bytes_billed": int | None,
        }
    """
    if self.mock_mode:
        return ServerResponse(
            success=True,
            data={
                "rows": [
                    {"id": 1, "name": "mock_1", "value": 100},
                    {"id": 2, "name": "mock_2", "value": 200},
                ],
                "row_count": 2,
                "duration_seconds": 0.5,
                "bytes_processed": 1024,
                "bytes_billed": 1024,
            },
        )

    # POST /api/v1/run/execute
    return self._post(
        "/api/v1/run/execute",
        json={
            "sql": sql,
            "dialect": dialect,
            "limit": limit,
            "timeout": timeout,
        },
    )
```

---

## 8. Error Codes (DLI-41x)

Run errors use a sub-range of execution errors (DLI-4xx).

```python
# File: dli/exceptions.py (additions)

class ErrorCode(str, Enum):
    # ... existing codes ...

    # Run Errors (DLI-41x) - Sub-range of Execution (DLI-4xx)
    RUN_FILE_NOT_FOUND = "DLI-410"
    RUN_LOCAL_DENIED = "DLI-411"
    RUN_SERVER_UNAVAILABLE = "DLI-412"
    RUN_EXECUTION_FAILED = "DLI-413"
    RUN_OUTPUT_FAILED = "DLI-414"
    RUN_PARAMETER_INVALID = "DLI-415"
    RUN_TIMEOUT = "DLI-416"


@dataclass
class RunFileNotFoundError(DLIError):
    """SQL file not found error."""
    code: ErrorCode = ErrorCode.RUN_FILE_NOT_FOUND
    path: str = ""


@dataclass
class RunLocalDeniedError(DLIError):
    """Local execution denied by server policy."""
    code: ErrorCode = ErrorCode.RUN_LOCAL_DENIED


@dataclass
class RunServerUnavailableError(DLIError):
    """Server execution unavailable."""
    code: ErrorCode = ErrorCode.RUN_SERVER_UNAVAILABLE


@dataclass
class RunExecutionError(DLIError):
    """Query execution failed."""
    code: ErrorCode = ErrorCode.RUN_EXECUTION_FAILED
    cause: str = ""


@dataclass
class RunOutputError(DLIError):
    """Output file write error."""
    code: ErrorCode = ErrorCode.RUN_OUTPUT_FAILED
    path: str = ""
```

### 8.1 Error Code Summary

| Code | Name | Description |
|------|------|-------------|
| DLI-410 | `RUN_FILE_NOT_FOUND` | SQL file not found |
| DLI-411 | `RUN_LOCAL_DENIED` | Server policy denies local execution |
| DLI-412 | `RUN_SERVER_UNAVAILABLE` | Server execution unavailable |
| DLI-413 | `RUN_EXECUTION_FAILED` | Query execution failed |
| DLI-414 | `RUN_OUTPUT_FAILED` | Cannot write output file |
| DLI-415 | `RUN_PARAMETER_INVALID` | Invalid parameter format |
| DLI-416 | `RUN_TIMEOUT` | Query timeout |

---

## 9. Server API Endpoints

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Get execution policy | `GET` | `/api/v1/run/policy` |
| Execute query | `POST` | `/api/v1/run/execute` |

### 9.1 Policy Endpoint Response

```json
{
  "allow_local": true,
  "server_available": true,
  "default_mode": "server"
}
```

### 9.2 Execute Request

```json
{
  "sql": "SELECT * FROM users WHERE created_at > '2026-01-01'",
  "dialect": "bigquery",
  "limit": 1000,
  "timeout": 300
}
```

### 9.3 Execute Response

```json
{
  "rows": [
    {"id": 1, "name": "Alice", "created_at": "2026-01-15T10:30:00Z"},
    {"id": 2, "name": "Bob", "created_at": "2026-01-16T11:45:00Z"}
  ],
  "row_count": 2,
  "duration_seconds": 1.23,
  "bytes_processed": 1048576,
  "bytes_billed": 1048576
}
```

---

## 10. Implementation Priority

### Phase 1: MVP

| Priority | Task |
|----------|------|
| 1 | Data models (`core/run/models.py`, `models/run.py`) |
| 2 | `RunAPI` class with mock mode support |
| 3 | CLI `run` command with `--sql`, `--output`, `--format` |
| 4 | Parameter substitution (`--param key=value`) |
| 5 | Output writers (CSV, TSV, JSON) |
| 6 | Error codes (DLI-41x) and exceptions |
| 7 | Mock data in `client.py` |
| 8 | Unit tests for `RunAPI` mock mode |

### Phase 2: Execution Modes

| Priority | Task |
|----------|------|
| 1 | Server policy enforcement (`run_get_policy`) |
| 2 | `--local` / `--server` flags with policy validation |
| 3 | `ServerExecutor` integration for server mode |
| 4 | `LocalExecutor` integration for local mode |
| 5 | `--dry-run` command for validation |
| 6 | `--show-sql` for debugging |

### Phase 3: Polish

| Priority | Task |
|----------|------|
| 1 | `--timeout` enforcement |
| 2 | `--limit` support |
| 3 | Progress indicators (Rich console output) |
| 4 | `--quiet` mode |
| 5 | Integration tests with real server |
| 6 | Performance optimization for large result sets |

---

## 11. Success Criteria

### 11.1 Feature Completion

| Feature | Completion Condition |
|---------|----------------------|
| `RunAPI` | `run()` works with CSV/TSV/JSON in mock mode |
| CLI run | `dli run --sql query.sql --output results.csv` produces file |
| Parameters | `--param date=2026-01-01` substitutes correctly |
| Formats | CSV, TSV, JSON all produce valid output |
| Dry run | `--dry-run` shows plan without execution |
| Error handling | DLI-41x codes return appropriate messages |

### 11.2 Test Quality

| Metric | Target | Measurement |
|--------|--------|-------------|
| Unit test coverage | >= 80% | `pytest --cov` |
| Mock mode tests | All formats tested | Test file count |
| CLI command tests | Each option tested | `typer.testing.CliRunner` |
| Output format tests | CSV/TSV/JSON validation | Format-specific assertions |

### 11.3 Code Quality

| Principle | Verification |
|-----------|--------------|
| Single Responsibility | `RunAPI` delegates to client/executor |
| Consistent Pattern | Follows `DatasetAPI`/`QueryAPI` facade pattern |
| Dependency Inversion | `RunAPI` accepts optional client via DI |
| No Silent Fallback | Policy rejection raises error, not fallback |

---

## 12. Directory Structure

```
project-interface-cli/src/dli/
├── __init__.py           # ADD: RunAPI export
├── api/
│   ├── __init__.py       # ADD: RunAPI export
│   └── run.py            # NEW: RunAPI class
├── models/
│   ├── __init__.py       # ADD: run model exports
│   └── run.py            # NEW: OutputFormat, RunResult, ExecutionPlan
├── commands/
│   ├── __init__.py       # ADD: run_app export
│   └── run.py            # NEW: CLI command
├── core/
│   ├── client.py         # ADD: run_* methods
│   └── run/              # NEW: directory
│       ├── __init__.py
│       └── models.py     # NEW: RunConfig, ExecutionData
└── exceptions.py         # ADD: DLI-41x codes, Run exceptions
```

**Legend:** `NEW` = new file/directory, `ADD` = additions to existing file

---

## 13. Reference Patterns

| Implementation | Reference File | Pattern |
|----------------|----------------|---------|
| `RunAPI` | `api/dataset.py` | Facade pattern, mock mode, DI |
| Result models | `models/common.py` | Pydantic `BaseModel` with `Field` |
| CLI command | `commands/dataset.py` | Typer command structure |
| Client methods | `core/client.py` | `ServerResponse`, `mock_mode` check |
| Exceptions | `exceptions.py` | `DLIError` inheritance, `ErrorCode` |
| `ExecutionMode` | `models/common.py` | `LOCAL`, `SERVER`, `MOCK` enum |

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

## Appendix D: Review Log

### Domain Implementer Review (feature-interface-cli)

**Date**: 2026-01-01

| Priority | Issue | Resolution |
|----------|-------|------------|
| HIGH | `--sql` takes PATH but `query list --sql` takes TEXT content | Consider renaming to `--file` or making positional argument |
| HIGH | Model location: `OutputFormat` defined in two places | Keep only in `dli/models/run.py` (public API models) |
| HIGH | `ExecutorFactory` not defined in codebase | Add to Phase 2 scope or use direct instantiation pattern |
| MEDIUM | `--dialect` default should come from project config | Add fallback: `dialect or self.context.dialect or "bigquery"` |
| MEDIUM | Short option `-p` conflicts with `--path` in other commands | Remove `-p` from `--param` to avoid confusion |
| MEDIUM | `run_get_policy` naming inconsistent with existing pattern | Rename to `run_policy()` to match `query_list()` pattern |
| LOW | Missing CLI command template in document | Add Typer command structure example |
| LOW | Missing `main.py` registration example | Clarify top-level command registration |

### Technical Review (expert-python)

**Date**: 2026-01-01

| Priority | Issue | Resolution |
|----------|-------|------------|
| HIGH | Missing generic type for `dict` → `list[dict]` | Use `list[dict[str, Any]]` for type safety |
| HIGH | Inconsistent `dialect` type (Literal vs str) | Create `RunDialect: TypeAlias = Literal["bigquery", "trino"]` |
| HIGH | New exceptions not added to `__all__` | Add Run exceptions to `exceptions.py` `__all__` list |
| MEDIUM | Use `Mapping[str, str]` for input params | Prefer immutable type for function inputs |
| MEDIUM | Bare `Exception` catch in `_execute_local` | Catch specific executor exceptions |
| MEDIUM | `csv`/`json` imports inside method | Move stdlib imports to module level |
| LOW | Parameter rendering uses simple replace | Consider regex-based approach for robustness |
| LOW | Missing `executor` DI parameter in constructor | Add for testing consistency with `DatasetAPI` |
| LOW | Hardcoded mock data | Consider injectable mock data for testing |

### Recommendations Summary

**Immediate Fixes (Before Implementation):**

1. Rename `--sql` to `--file` or make positional argument
2. Move `OutputFormat` to only `dli/models/run.py`
3. Add `dict[str, Any]` type annotations throughout
4. Add new exceptions to `__all__` in `exceptions.py`
5. Create `RunDialect` type alias for consistency

**Design Decisions Confirmed:**

1. DLI-41x error code range is available (no conflicts)
2. No fallback policy is correct approach
3. JSONL format for JSON output is appropriate
4. Following `DatasetAPI`/`QueryAPI` facade pattern is correct

---

## Appendix E: Implementation Review Log

### Implementation Notes (2026-01-01)

**Status:** Feature fully implemented and tested.

| Area | Implementation Details |
|------|------------------------|
| **RunAPI** | `api/run.py` - Full implementation with run(), dry_run(), render_sql() methods |
| **CLI Command** | `commands/run.py` - Top-level `dli run` command with all options |
| **Models** | `models/run.py` - OutputFormat, RunResult, ExecutionPlan models |
| **Core Models** | `core/run/models.py` - RunConfig, ExecutionData for internal use |
| **Exceptions** | DLI-410 ~ DLI-416 error codes added to exceptions.py |
| **BasecampClient** | run_execute(), run_get_policy() methods added |

**Files Created:**

| File | Lines | Purpose |
|------|-------|---------|
| `src/dli/api/run.py` | ~400 | RunAPI class |
| `src/dli/models/run.py` | ~100 | Result models |
| `src/dli/core/run/__init__.py` | ~10 | Module exports |
| `src/dli/core/run/models.py` | ~80 | Core models |
| `src/dli/commands/run.py` | ~350 | CLI commands |
| `tests/api/test_run_api.py` | ~300 | API tests |
| `tests/cli/test_run_cmd.py` | ~400 | CLI tests |
| `tests/core/run/test_models.py` | ~150 | Model tests |

**Test Results:**

- Total new tests: ~120
- API tests: 40 tests (run, dry_run, render_sql, error handling)
- CLI tests: 50 tests (all options, error cases, output formats)
- Model tests: 30 tests (validation, serialization)
- All tests passing with 0 pyright errors

**Design Changes from Spec:**

1. `--sql` kept as option (not renamed to `--file`) for clarity
2. OutputFormat consolidated in `models/run.py` only
3. Added `executor` DI parameter for testing consistency
4. Used specific exception types instead of bare Exception catch
