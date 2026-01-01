"""RunAPI - Library API for ad-hoc SQL execution.

This module provides the RunAPI class for programmatic execution of
ad-hoc SQL files with result download to local files.

Example:
    >>> from dli import RunAPI, ExecutionContext, ExecutionMode
    >>> from dli.models.run import OutputFormat
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
"""

from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import TYPE_CHECKING, Any, Literal

from dli.core.client import BasecampClient, ServerConfig
from dli.exceptions import (
    ConfigurationError,
    ErrorCode,
    RunExecutionError,
    RunFileNotFoundError,
    RunLocalDeniedError,
    RunOutputError,
    RunServerUnavailableError,
)
from dli.models.common import ExecutionContext, ExecutionMode, ResultStatus
from dli.models.run import ExecutionPlan, OutputFormat, RunResult

if TYPE_CHECKING:
    from dli.core.executor import QueryExecutor

__all__ = ["RunAPI"]


class RunAPI:
    """Library API for ad-hoc SQL execution with result download.

    Executes SQL files against query engines and saves results to local files.
    Supports both local and server execution modes with server policy enforcement.

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations.

    Example:
        >>> from dli import RunAPI, ExecutionContext, ExecutionMode
        >>> from dli.models.run import OutputFormat
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
        executor: QueryExecutor | None = None,  # DI for testing
    ) -> None:
        """Initialize RunAPI.

        Args:
            context: Execution context. Defaults to ExecutionContext().
            client: Optional BasecampClient for dependency injection.
            executor: Optional QueryExecutor for dependency injection (local execution).
        """
        self.context = context or ExecutionContext()
        self._client = client
        self._executor = executor

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"RunAPI(context={self.context!r})"

    @property
    def _is_mock_mode(self) -> bool:
        """Check if running in mock mode."""
        return self.context.execution_mode == ExecutionMode.MOCK

    def _get_client(self) -> BasecampClient:
        """Get or create BasecampClient instance (lazy initialization).

        Returns:
            BasecampClient instance.
        """
        if self._client is not None:
            return self._client

        config = ServerConfig(
            url=self.context.server_url or "http://localhost:8081",
        )
        self._client = BasecampClient(
            config=config,
            mock_mode=self._is_mock_mode,
        )
        return self._client

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
        """Render SQL with simple parameter substitution.

        Supports {{ param }} and {{param}} syntax.
        """
        rendered = sql
        for key, value in parameters.items():
            # Handle both {{ key }} and {{key}}
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

        if not policy.success:
            # If we can't get policy, default to server mode
            return ExecutionMode.SERVER

        # Ensure policy_data is a dict
        policy_data: dict[str, Any] = (
            policy.data if isinstance(policy.data, dict) else {}
        )

        if prefer_local:
            if not policy_data.get("allow_local", False):
                raise RunLocalDeniedError(
                    message="Local execution not permitted by server policy",
                    code=ErrorCode.RUN_LOCAL_DENIED,
                )
            return ExecutionMode.LOCAL

        if prefer_server:
            if not policy_data.get("server_available", True):
                raise RunServerUnavailableError(
                    message="Server execution unavailable",
                    code=ErrorCode.RUN_SERVER_UNAVAILABLE,
                )
            return ExecutionMode.SERVER

        # Use server default
        default_mode = policy_data.get("default_mode", "server")
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
        mock_data: list[dict[str, Any]] = [
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
        dialect: str,  # noqa: ARG002
        limit: int | None,  # noqa: ARG002
        timeout: int,
    ) -> RunResult:
        """Execute query locally via direct engine connection.

        Uses injected executor if provided, otherwise creates one via ExecutorFactory.
        """
        import time

        from dli.core.executor import BaseExecutor
        from dli.core.models import ExecutionResult

        start_time = time.time()
        result: ExecutionResult

        # Use injected executor if provided (DI for testing)
        if self._executor is not None:
            # QueryExecutor protocol - uses execute(sql, params)
            result = self._executor.execute(rendered_sql)
        else:
            # Create executor via factory
            try:
                from dli.core.executor import ExecutorFactory

                executor = ExecutorFactory.create(
                    mode=ExecutionMode.LOCAL,
                    context=self.context,
                )
            except ImportError as e:
                raise RunExecutionError(
                    message=f"Failed to import executor: {e}",
                    code=ErrorCode.RUN_EXECUTION_FAILED,
                    cause=str(e),
                ) from e
            except ValueError as e:
                raise RunExecutionError(
                    message=f"Unsupported execution configuration: {e}",
                    code=ErrorCode.RUN_EXECUTION_FAILED,
                    cause=str(e),
                ) from e

            # Execute based on executor type
            # BaseExecutor subclasses use execute_sql(), others use execute()
            if isinstance(executor, BaseExecutor):
                result = executor.execute_sql(rendered_sql, timeout)
            else:
                # ServerExecutor and other executors with execute() method
                result = executor.execute(rendered_sql)

        if not result.success:
            raise RunExecutionError(
                message=result.error_message or "Query execution failed",
                code=ErrorCode.RUN_EXECUTION_FAILED,
                cause=result.error_message or "Unknown error",
            )

        # Get result rows
        result_rows: list[dict[str, Any]] = result.data if result.data else []
        row_count = result.row_count if result.row_count else len(result_rows)

        # Write output
        self._write_output(output_path, output_format, result_rows)

        duration_seconds = time.time() - start_time

        return RunResult(
            status=ResultStatus.SUCCESS,
            sql_path=sql_path,
            output_path=output_path,
            output_format=output_format,
            row_count=row_count,
            duration_seconds=duration_seconds,
            execution_mode=ExecutionMode.LOCAL,
            rendered_sql=rendered_sql,
        )

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

        # Handle response data - could be list or dict
        raw_data = response.data
        if isinstance(raw_data, list):
            result_rows: list[dict[str, Any]] = raw_data
            row_count = len(raw_data)
            duration_seconds = 0.0
            bytes_processed: int | None = None
            bytes_billed: int | None = None
        else:
            data: dict[str, Any] = raw_data if isinstance(raw_data, dict) else {}
            result_rows = data.get("rows", [])
            row_count = data.get("row_count", len(result_rows))
            duration_seconds = data.get("duration_seconds", 0.0)
            bytes_processed = data.get("bytes_processed")
            bytes_billed = data.get("bytes_billed")

        self._write_output(output_path, output_format, result_rows)

        return RunResult(
            status=ResultStatus.SUCCESS,
            sql_path=sql_path,
            output_path=output_path,
            output_format=output_format,
            row_count=row_count,
            duration_seconds=duration_seconds,
            execution_mode=ExecutionMode.SERVER,
            rendered_sql=rendered_sql,
            bytes_processed=bytes_processed,
            bytes_billed=bytes_billed,
        )

    def _write_output(
        self,
        output_path: Path,
        output_format: OutputFormat,
        rows: list[dict[str, Any]],
    ) -> None:
        """Write result rows to output file."""
        try:
            output_path.parent.mkdir(parents=True, exist_ok=True)

            if output_format == OutputFormat.JSON:
                with output_path.open("w", encoding="utf-8") as f:
                    for row in rows:
                        f.write(json.dumps(row, ensure_ascii=False, default=str) + "\n")

            elif output_format == OutputFormat.TSV:
                with output_path.open("w", encoding="utf-8", newline="") as f:
                    if rows:
                        writer = csv.DictWriter(
                            f, fieldnames=list(rows[0].keys()), delimiter="\t"
                        )
                        writer.writeheader()
                        writer.writerows(rows)

            else:  # CSV
                with output_path.open("w", encoding="utf-8", newline="") as f:
                    if rows:
                        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
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

        Raises:
            RunFileNotFoundError: If SQL file not found.
            ConfigurationError: If both prefer_local and prefer_server are True.

        Example:
            >>> plan = api.dry_run(
            ...     sql_path=Path("query.sql"),
            ...     output_path=Path("results.csv"),
            ...     parameters={"date": "2026-01-01"},
            ... )
            >>> print(f"Mode: {plan.execution_mode}, Valid: {plan.is_valid}")
            >>> print(f"SQL: {plan.rendered_sql}")
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
