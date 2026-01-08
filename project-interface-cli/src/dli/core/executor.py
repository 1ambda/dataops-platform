"""Execution engine for the DLI Core Engine.

This module provides:
- QueryExecutor: Protocol for query executors (DI interface for Library API)
- BaseExecutor: Abstract base class for SQL executors (internal)
- MockExecutor: Mock executor for testing
- DatasetExecutor: 3-stage execution engine (Pre -> Main -> Post)
- ExecutorFactory: Factory for creating executors based on ExecutionMode

Architecture Note:
    There are two executor interfaces in this module:

    1. QueryExecutor (Protocol) - For Library API dependency injection
       - Used by DatasetAPI, MetricAPI for DI
       - Methods: execute(sql, params), test_connection()

    2. BaseExecutor (ABC) - For internal 3-stage execution
       - Used by DatasetExecutor for Pre/Main/Post stages
       - Methods: execute_sql(sql, timeout), dry_run(sql), test_connection()

    In Phase 2, these may be unified when actual database execution is implemented.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
import time
from typing import TYPE_CHECKING, Any, Protocol, runtime_checkable

from dli.core.models import (
    DatasetExecutionResult,
    DatasetSpec,
    ExecutionResult,
)
from dli.core.types import DryRunResult

if TYPE_CHECKING:
    from dli.core.client import BasecampClient
    from dli.models.common import ExecutionContext, ExecutionMode


# =============================================================================
# Protocol for Library API DI (Dependency Injection)
# =============================================================================


@runtime_checkable
class QueryExecutor(Protocol):
    """Protocol for query executors (DI interface for Library API).

    This protocol defines the minimal interface for dependency injection
    in DatasetAPI and MetricAPI. Use this when you need to inject a
    custom executor for testing or alternative execution backends.

    Note:
        This is different from BaseExecutor which is used internally
        for the 3-stage execution engine (Pre -> Main -> Post).

    Example:
        >>> class MyExecutor:
        ...     def execute(self, sql: str, params: dict | None = None) -> ExecutionResult:
        ...         # Custom execution logic
        ...         ...
        ...     def test_connection(self) -> bool:
        ...         return True
        >>>
        >>> executor: QueryExecutor = MyExecutor()
        >>> api = DatasetAPI(context=ctx, executor=executor)
    """

    def execute(
        self, sql: str, params: dict[str, Any] | None = None
    ) -> ExecutionResult:
        """Execute a SQL query.

        Args:
            sql: SQL query to execute.
            params: Optional parameters for the query.

        Returns:
            ExecutionResult with query results.
        """
        ...

    def test_connection(self) -> bool:
        """Test the database connection.

        Returns:
            True if connection is successful.
        """
        ...


# =============================================================================
# Abstract Base Class for Internal Executors
# =============================================================================


class BaseExecutor(ABC):
    """Abstract base class for SQL query executors.

    All concrete executors (Trino, BigQuery, etc.) must inherit
    from this class and implement the required methods.
    """

    @abstractmethod
    def execute_sql(self, sql: str, timeout: int = 300) -> ExecutionResult:
        """Execute a SQL query and return results.

        Args:
            sql: SQL query to execute
            timeout: Execution timeout in seconds

        Returns:
            ExecutionResult with query results or error information
        """

    @abstractmethod
    def dry_run(self, sql: str) -> DryRunResult:
        """Perform a dry run of the query without executing.

        This is useful for validating queries and estimating costs
        before actual execution.

        Args:
            sql: SQL query to validate

        Returns:
            DryRunResult with validation status and metadata
            (e.g., bytes_processed, estimated_cost)
        """

    @abstractmethod
    def test_connection(self) -> bool:
        """Test the connection to the database.

        Returns:
            True if connection is successful, False otherwise
        """


class MockExecutor(BaseExecutor):
    """Mock executor for testing purposes.

    This executor simulates query execution without connecting
    to any actual database.
    """

    def __init__(
        self,
        mock_data: list[dict[str, Any]] | None = None,
        should_fail: bool = False,
        error_message: str = "Mock error",
    ):
        """Initialize the mock executor.

        Args:
            mock_data: Optional list of dictionaries to return as query results
            should_fail: Whether to simulate execution failure
            error_message: Error message to return on failure
        """
        self.mock_data = mock_data or []
        self.should_fail = should_fail
        self.error_message = error_message
        self.executed_sqls: list[str] = []

    def execute_sql(self, sql: str, timeout: int = 300) -> ExecutionResult:  # noqa: ARG002
        """Simulate query execution.

        Args:
            sql: SQL query (stored for inspection)
            timeout: Ignored in mock executor

        Returns:
            ExecutionResult with mock data
        """
        self.executed_sqls.append(sql)

        if self.should_fail:
            return ExecutionResult(
                dataset_name="",
                phase="main",
                success=False,
                error_message=self.error_message,
                rendered_sql=sql,
                execution_time_ms=10,
            )

        columns = list(self.mock_data[0].keys()) if self.mock_data else []

        return ExecutionResult(
            dataset_name="",
            phase="main",
            success=True,
            row_count=len(self.mock_data),
            columns=columns,
            data=self.mock_data,
            rendered_sql=sql,
            execution_time_ms=10,
        )

    def dry_run(self, sql: str) -> DryRunResult:
        """Simulate dry run.

        Args:
            sql: SQL query to validate

        Returns:
            Mock dry run result
        """
        self.executed_sqls.append(f"DRY_RUN: {sql}")
        return {
            "valid": True,
            "bytes_processed": 1000000,
            "bytes_processed_gb": 0.001,
            "estimated_cost_usd": 0.000005,
        }

    def test_connection(self) -> bool:
        """Always returns True for mock executor."""
        return True

    def reset(self) -> None:
        """Reset the executor state."""
        self.executed_sqls.clear()


class FailingMockExecutor(MockExecutor):
    """Mock executor that fails on specific statements.

    This is useful for testing error handling and continue_on_error behavior.
    """

    def __init__(
        self,
        mock_data: list[dict[str, Any]] | None = None,
        fail_on_statements: list[int] | None = None,
        error_message: str = "Mock error",
    ):
        """Initialize the failing mock executor.

        Args:
            mock_data: Optional list of dictionaries to return as query results
            fail_on_statements: List of statement indices to fail on (0-indexed)
            error_message: Error message to return on failure
        """
        super().__init__(mock_data=mock_data, error_message=error_message)
        self.fail_on_statements = set(fail_on_statements or [])
        self._execution_count = 0

    def execute_sql(self, sql: str, timeout: int = 300) -> ExecutionResult:  # noqa: ARG002
        """Simulate query execution with controlled failures.

        Args:
            sql: SQL query (stored for inspection)
            timeout: Ignored in mock executor

        Returns:
            ExecutionResult with mock data or error
        """
        self.executed_sqls.append(sql)
        current_index = self._execution_count
        self._execution_count += 1

        if current_index in self.fail_on_statements:
            return ExecutionResult(
                dataset_name="",
                phase="main",
                success=False,
                error_message=self.error_message,
                rendered_sql=sql,
                execution_time_ms=10,
            )

        columns = list(self.mock_data[0].keys()) if self.mock_data else []

        return ExecutionResult(
            dataset_name="",
            phase="main",
            success=True,
            row_count=len(self.mock_data),
            columns=columns,
            data=self.mock_data,
            rendered_sql=sql,
            execution_time_ms=10,
        )

    def reset(self) -> None:
        """Reset the executor state."""
        super().reset()
        self._execution_count = 0


class DatasetExecutor:
    """Dataset 3-stage execution engine (Pre -> Main -> Post).

    This executor handles the full dataset execution lifecycle:
    1. Pre-statements: Preparation queries (e.g., partition deletion)
    2. Main query: The primary dataset query
    3. Post-statements: Cleanup queries (e.g., optimization)

    Attributes:
        executor: Base executor for running SQL
    """

    def __init__(self, executor: BaseExecutor):
        """Initialize the dataset executor.

        Args:
            executor: Base executor implementation
        """
        self.executor = executor

    def execute(
        self,
        spec: DatasetSpec,
        rendered_sqls: dict[str, str | list[str]],
        *,
        skip_pre: bool = False,
        skip_post: bool = False,
    ) -> DatasetExecutionResult:
        """Execute a dataset with 3-stage execution.

        Args:
            spec: DatasetSpec with execution configuration
            rendered_sqls: Dictionary with rendered SQL:
                - "pre": List of pre-statement SQL strings
                - "main": Main query SQL string
                - "post": List of post-statement SQL strings
            skip_pre: Skip pre-statements
            skip_post: Skip post-statements

        Returns:
            DatasetExecutionResult with all execution results
        """
        start_time = time.time()
        pre_results: list[ExecutionResult] = []
        post_results: list[ExecutionResult] = []
        main_result: ExecutionResult | None = None
        overall_success = True
        error_message = None

        timeout = spec.execution.timeout_seconds

        # 1. Pre Statements
        if not skip_pre and "pre" in rendered_sqls:
            pre_sqls = rendered_sqls["pre"]
            if isinstance(pre_sqls, str):
                pre_sqls = [pre_sqls]

            for i, sql in enumerate(pre_sqls):
                stmt = spec.pre_statements[i] if i < len(spec.pre_statements) else None
                stmt_name = stmt.name if stmt else f"pre_{i}"

                result = self.executor.execute_sql(sql, timeout)
                result.phase = "pre"
                result.statement_name = stmt_name
                result.dataset_name = spec.name
                pre_results.append(result)

                if not result.success:
                    if stmt and stmt.continue_on_error:
                        continue
                    overall_success = False
                    error_message = (
                        f"Pre statement '{stmt_name}' failed: {result.error_message}"
                    )
                    break

        # 2. Main Statement
        if overall_success and "main" in rendered_sqls:
            main_sql = rendered_sqls["main"]
            if isinstance(main_sql, list):
                main_sql = main_sql[0]

            main_result = self.executor.execute_sql(main_sql, timeout)
            main_result.phase = "main"
            main_result.dataset_name = spec.name

            if not main_result.success:
                overall_success = False
                error_message = f"Main query failed: {main_result.error_message}"

        # 3. Post Statements
        if overall_success and not skip_post and "post" in rendered_sqls:
            post_sqls = rendered_sqls["post"]
            if isinstance(post_sqls, str):
                post_sqls = [post_sqls]

            for i, sql in enumerate(post_sqls):
                stmt = (
                    spec.post_statements[i] if i < len(spec.post_statements) else None
                )
                stmt_name = stmt.name if stmt else f"post_{i}"

                result = self.executor.execute_sql(sql, timeout)
                result.phase = "post"
                result.statement_name = stmt_name
                result.dataset_name = spec.name
                post_results.append(result)

                if not result.success:
                    if stmt and stmt.continue_on_error:
                        continue
                    overall_success = False
                    error_message = (
                        f"Post statement '{stmt_name}' failed: {result.error_message}"
                    )
                    break

        total_time = int((time.time() - start_time) * 1000)

        return DatasetExecutionResult(
            dataset_name=spec.name,
            success=overall_success,
            pre_results=pre_results,
            main_result=main_result,
            post_results=post_results,
            total_execution_time_ms=total_time,
            error_message=error_message,
        )

    def execute_phase(
        self,
        spec: DatasetSpec,
        rendered_sqls: dict[str, str | list[str]],
        phase: str,
    ) -> DatasetExecutionResult:
        """Execute only a specific phase.

        This method runs a single phase in isolation, unlike execute() which
        runs the full Pre -> Main -> Post pipeline.

        Args:
            spec: DatasetSpec with execution configuration
            rendered_sqls: Dictionary with rendered SQL
            phase: Phase to execute ("pre", "main", or "post")

        Returns:
            DatasetExecutionResult with results for the specified phase only
        """
        # Filter to only include the requested phase
        phase_sqls: dict[str, str | list[str]] = {}
        if phase in rendered_sqls:
            phase_sqls[phase] = rendered_sqls[phase]

        match phase:
            case "pre":
                # Skip main and post, only run pre
                return self.execute(spec, phase_sqls, skip_pre=False, skip_post=True)
            case "post":
                # Skip pre and main, only run post
                return self.execute(spec, phase_sqls, skip_pre=True, skip_post=False)
            case _:  # main
                # Only run main (no pre or post in phase_sqls)
                return self.execute(spec, phase_sqls, skip_pre=True, skip_post=True)


class ServerExecutor:
    """Executor for server-side query execution via Basecamp API.

    This executor sends queries to the Basecamp server for execution,
    which is useful in environments where direct database access is
    not available or not permitted.

    The executor implements the QueryExecutor protocol, allowing it to be
    used with dependency injection in DatasetAPI and MetricAPI.

    Attributes:
        server_url: Basecamp server URL.
        api_token: API authentication token.
        timeout: Request timeout in seconds.

    Example:
        >>> executor = ServerExecutor(
        ...     server_url="http://localhost:8081",
        ...     api_token="my-token",
        ... )
        >>> result = executor.execute("SELECT 1")
        >>> print(result.success)
        True
    """

    def __init__(
        self,
        server_url: str | None = None,
        api_token: str | None = None,
        timeout: int = 300,
    ) -> None:
        """Initialize the server executor.

        Args:
            server_url: Basecamp server URL.
            api_token: API authentication token.
            timeout: Request timeout in seconds (default: 300).
        """
        self.server_url = server_url
        self.api_token = api_token
        self.timeout = timeout
        self._client: BasecampClient | None = None

    @property
    def client(self) -> BasecampClient:
        """Get or create the Basecamp client (lazy initialization).

        Returns:
            BasecampClient instance configured with server_url and api_token.
        """
        if self._client is None:
            from dli.core.client import create_client  # noqa: PLC0415

            self._client = create_client(
                url=self.server_url,
                timeout=self.timeout,
                api_key=self.api_token,
                mock_mode=False,
            )
        return self._client

    def execute(
        self, sql: str, params: dict[str, Any] | None = None
    ) -> ExecutionResult:
        """Execute query via Basecamp server.

        Sends the SQL query to the Basecamp server's execution API and
        transforms the response into an ExecutionResult.

        Args:
            sql: SQL query to execute.
            params: Optional parameters for query template substitution.

        Returns:
            ExecutionResult with query results or error information.

        Note:
            This method does not raise exceptions for execution failures.
            Instead, it returns an ExecutionResult with success=False and
            an appropriate error_message. This allows callers to handle
            errors gracefully.
        """
        start_time = time.time()

        try:
            response = self.client.execute_rendered_sql(
                sql=sql,
                parameters=params,
                execution_timeout=self.timeout,
            )

            execution_time_ms = int((time.time() - start_time) * 1000)

            if response.success and response.data:
                data = response.data
                # Type guard: ensure data is a dict, not a list
                if isinstance(data, dict):
                    rows: list[dict[str, Any]] = data.get("rows", [])
                    columns = list(rows[0].keys()) if rows else []

                    return ExecutionResult(
                        dataset_name="",
                        phase="main",
                        success=True,
                        row_count=data.get("row_count", len(rows)),
                        columns=columns,
                        data=rows,
                        rendered_sql=data.get("rendered_sql", sql),
                        execution_time_ms=execution_time_ms,
                        error_message=None,
                    )
                # Unexpected response format (data is a list, not dict)
                return ExecutionResult(
                    dataset_name="",
                    phase="main",
                    success=False,
                    row_count=0,
                    columns=[],
                    data=[],
                    rendered_sql=sql,
                    execution_time_ms=execution_time_ms,
                    error_message="Unexpected server response format",
                )
            # Server returned an error
            error_msg = response.error or "Unknown server error"
            return ExecutionResult(
                dataset_name="",
                phase="main",
                success=False,
                row_count=0,
                columns=[],
                data=[],
                rendered_sql=sql,
                execution_time_ms=execution_time_ms,
                error_message=f"Server execution failed: {error_msg}",
            )

        except Exception as e:
            # Handle connection errors, timeouts, etc.
            execution_time_ms = int((time.time() - start_time) * 1000)
            error_msg = str(e) if str(e) else type(e).__name__

            return ExecutionResult(
                dataset_name="",
                phase="main",
                success=False,
                row_count=0,
                columns=[],
                data=[],
                rendered_sql=sql,
                execution_time_ms=execution_time_ms,
                error_message=f"Server connection error: {error_msg}",
            )

    def execute_sql(self, sql: str, timeout: int = 300) -> ExecutionResult:
        """Execute SQL query with explicit timeout.

        This method provides compatibility with the BaseExecutor interface,
        allowing ServerExecutor to be used in contexts that expect BaseExecutor.

        Args:
            sql: SQL query to execute.
            timeout: Execution timeout in seconds.

        Returns:
            ExecutionResult with query results.
        """
        # Temporarily override timeout for this execution
        original_timeout = self.timeout
        self.timeout = timeout
        try:
            return self.execute(sql)
        finally:
            self.timeout = original_timeout

    def test_connection(self) -> bool:
        """Test server connection by performing a health check.

        Returns:
            True if the server is reachable and healthy, False otherwise.
        """
        if not self.server_url:
            return False

        try:
            response = self.client.health_check()
        except Exception:
            return False
        return response.success


class ExecutorFactory:
    """Factory for creating executors based on ExecutionMode.

    This factory provides a centralized way to create the appropriate
    executor based on the execution mode specified in the context.

    Example:
        >>> from dli.models.common import ExecutionContext, ExecutionMode
        >>> ctx = ExecutionContext(execution_mode=ExecutionMode.LOCAL, dialect="bigquery")
        >>> executor = ExecutorFactory.create(ctx.execution_mode, ctx)
    """

    @staticmethod
    def create(
        mode: ExecutionMode,
        context: ExecutionContext,
    ) -> BaseExecutor | ServerExecutor | MockExecutor:
        """Create an executor based on the execution mode.

        Args:
            mode: Execution mode (LOCAL, SERVER, or MOCK).
            context: Execution context with configuration.

        Returns:
            Appropriate executor instance.

        Raises:
            ValueError: If the engine/mode combination is not supported.
        """
        # Import here to avoid circular imports
        from dli.models.common import ExecutionMode

        match mode:
            case ExecutionMode.LOCAL:
                engine = context.dialect
                if engine == "bigquery":
                    from dli.adapters.bigquery import BigQueryExecutor

                    return BigQueryExecutor(
                        project=context.parameters.get("project", ""),
                        location=context.parameters.get("location", "US"),
                    )
                if engine == "trino":
                    from dli.adapters.trino import TrinoExecutor

                    return TrinoExecutor(
                        host=context.parameters.get("host", "localhost"),
                        port=context.parameters.get("port", 8080),
                        user=context.parameters.get("user", "trino"),
                        catalog=context.parameters.get("catalog"),
                        schema=context.parameters.get("schema"),
                        ssl=context.parameters.get("ssl", True),
                        ssl_verify=context.parameters.get("ssl_verify", True),
                        auth_type=context.parameters.get("auth_type", "none"),
                        auth_token=context.parameters.get("auth_token"),
                        password=context.parameters.get("password"),
                    )
                # Add more engines here (snowflake, etc.)
                raise ValueError(f"Unsupported engine for LOCAL mode: {engine}")

            case ExecutionMode.SERVER:
                return ServerExecutor(
                    server_url=context.server_url,
                    api_token=context.api_token,
                )

            case ExecutionMode.MOCK:
                return MockExecutor()

            case _:
                raise ValueError(f"Unknown execution mode: {mode}")
