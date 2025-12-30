"""MetricAPI - Library API for Metric operations.

This module provides the MetricAPI class which wraps the MetricService
for programmatic access to metric (SELECT query) operations.

Example:
    >>> from dli import MetricAPI, ExecutionContext, ExecutionMode
    >>> ctx = ExecutionContext(project_path="/path/to/project")
    >>> api = MetricAPI(context=ctx)
    >>> result = api.run("catalog.schema.metric", parameters={"date": "2025-01-01"})
    >>> print(result.data)

    >>> # With mock executor injection (for testing)
    >>> from dli.core.executor import MockExecutor
    >>> mock_executor = MockExecutor(mock_data=[{"id": 1}])
    >>> api = MetricAPI(context=ctx, executor=mock_executor)
"""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING, Any

from dli.exceptions import (
    ConfigurationError,
    ErrorCode,
    ExecutionError,
    MetricNotFoundError,
)
from dli.models.common import (
    DataSource,
    ExecutionContext,
    ExecutionMode,
    MetricResult,
    ResultStatus,
    ValidationResult,
)

if TYPE_CHECKING:
    from dli.core.executor import QueryExecutor
    from dli.core.metric_service import MetricService
    from dli.core.models.metric import MetricSpec


class MetricAPI:
    """Metric management Library API.

    Provides programmatic access to metric operations including:
    - Listing and searching metrics
    - Validating metric specs and SQL
    - Rendering SQL with Jinja templates
    - Executing metrics (SELECT queries)
    - Registering metrics to server

    Metrics are read-only SELECT queries, unlike Datasets which can
    perform INSERT/UPDATE operations.

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations.

    Example:
        >>> from dli import MetricAPI, ExecutionContext
        >>> ctx = ExecutionContext(
        ...     project_path="/opt/airflow/dags/models",
        ...     parameters={"date": "2025-01-01"},
        ... )
        >>> api = MetricAPI(context=ctx)
        >>> result = api.run("catalog.schema.metric")
        >>> print(result.data)
    """

    def __init__(
        self,
        context: ExecutionContext | None = None,
        executor: QueryExecutor | None = None,
    ) -> None:
        """Initialize MetricAPI.

        Args:
            context: Execution context with settings. If None, creates
                     default context from environment variables.
            executor: Optional query executor for DI (dependency injection).
                     If provided, this executor will be used instead of
                     creating one based on execution_mode.

        Example:
            >>> # Normal usage
            >>> api = MetricAPI(context=ExecutionContext())

            >>> # With injected mock executor (for testing)
            >>> from dli.core.executor import MockExecutor
            >>> mock = MockExecutor(mock_data=[{"id": 1}])
            >>> api = MetricAPI(context=ctx, executor=mock)
        """
        self.context = context or ExecutionContext()
        # TODO(Phase 2): Use _executor for actual query execution
        # When execution_mode is LOCAL/SERVER and executor is provided,
        # use the injected executor instead of creating one via ExecutorFactory.
        self._executor = executor
        self._service: MetricService | None = None

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"MetricAPI(context={self.context!r})"

    @property
    def _is_mock_mode(self) -> bool:
        """Check if running in mock mode."""
        return self.context.execution_mode == ExecutionMode.MOCK

    def _get_service(self) -> MetricService:
        """Get or create MetricService instance (lazy initialization).

        Returns:
            MetricService instance.

        Raises:
            ConfigurationError: If project_path is required but not set.
        """
        if self._service is None:
            from dli.core.metric_service import MetricService as MetricServiceImpl

            project_path = self.context.project_path
            if project_path is None and not self._is_mock_mode:
                msg = "project_path is required for MetricAPI"
                raise ConfigurationError(message=msg, code=ErrorCode.CONFIG_INVALID)

            self._service = MetricServiceImpl(project_path=project_path)

        return self._service

    # === CRUD Operations ===

    def list_metrics(
        self,
        path: Path | None = None,
        *,
        source: DataSource = "local",
        domain: str | None = None,
        owner: str | None = None,
        tags: list[str] | None = None,
        catalog: str | None = None,
        schema: str | None = None,
    ) -> list[MetricSpec]:
        """List metrics with optional filtering.

        Args:
            path: Override project path for discovery (not yet implemented).
            source: Data source ("local" for disk, "server" for API).
                Note: "server" source is not yet implemented.
            domain: Filter by domain.
            owner: Filter by owner.
            tags: Filter by tags (any match).
            catalog: Filter by catalog.
            schema: Filter by schema.

        Returns:
            List of MetricSpec objects.
        """
        # TODO: Implement path override and server source
        _ = path, source  # Suppress unused variable warnings
        if self._is_mock_mode:
            return []

        service = self._get_service()

        # Apply tag filter if provided
        tag = tags[0] if tags else None

        return service.list_metrics(
            tag=tag,
            domain=domain,
            catalog=catalog,
            schema=schema,
            owner=owner,
        )

    def get(self, name: str) -> MetricSpec | None:
        """Get metric by name.

        Args:
            name: Fully qualified metric name (catalog.schema.name).

        Returns:
            MetricSpec if found, None otherwise.
        """
        if self._is_mock_mode:
            return None

        service = self._get_service()
        return service.get_metric(name)

    # === Execution ===

    def run(
        self,
        name: str,
        *,
        parameters: dict[str, Any] | None = None,
        dry_run: bool = False,
        show_sql: bool = False,
    ) -> MetricResult:
        """Execute a metric (SELECT query).

        Args:
            name: Fully qualified metric name.
            parameters: Runtime parameters (merged with context.parameters).
            dry_run: If True, validate and render SQL without execution.
            show_sql: If True, include rendered SQL in result.

        Returns:
            MetricResult with query results.

        Raises:
            MetricNotFoundError: If metric not found.
            ExecutionError: If execution fails.
        """
        started_at = datetime.now(tz=UTC)

        # Merge parameters
        merged_params = {**self.context.parameters, **(parameters or {})}

        if self._is_mock_mode:
            return MetricResult(
                name=name,
                status=ResultStatus.SUCCESS,
                started_at=started_at,
                ended_at=datetime.now(tz=UTC),
                duration_ms=0,
                sql="-- Mock SQL" if show_sql else None,
                data=[],
                row_count=0,
            )

        try:
            service = self._get_service()

            # Check if metric exists
            spec = service.get_metric(name)
            if spec is None:
                raise MetricNotFoundError(
                    message=f"Metric '{name}' not found",
                    name=name,
                )

            # Use dry_run from context if not explicitly set
            actual_dry_run = dry_run or self.context.dry_run

            # Execute
            result = service.execute(
                name,
                merged_params,
                dry_run=actual_dry_run,
            )

            ended_at = datetime.now(tz=UTC)
            duration_ms = int((ended_at - started_at).total_seconds() * 1000)

            # Get SQL if requested
            sql = None
            if show_sql:
                sql = service.render_sql(name, merged_params)

            return MetricResult(
                name=name,
                status=ResultStatus.SUCCESS if result.success else ResultStatus.FAILURE,
                started_at=started_at,
                ended_at=ended_at,
                duration_ms=duration_ms,
                sql=sql,
                data=result.rows,
                row_count=result.row_count,
                columns=result.columns,
                error_message=result.error_message,
            )

        except MetricNotFoundError:
            raise
        except Exception as e:
            ended_at = datetime.now(tz=UTC)
            duration_ms = int((ended_at - started_at).total_seconds() * 1000)

            raise ExecutionError(
                message=f"Metric execution failed: {e}",
                cause=e,
            ) from e

    # === Validation ===

    def validate(
        self,
        name: str,
        *,
        strict: bool = False,
        check_deps: bool = False,
    ) -> ValidationResult:
        """Validate metric spec and SQL.

        Args:
            name: Fully qualified metric name.
            strict: If True, treat warnings as errors.
            check_deps: If True, validate dependencies exist.

        Returns:
            ValidationResult with validation status.
        """
        if self._is_mock_mode:
            return ValidationResult(valid=True)

        try:
            service = self._get_service()

            # Check if metric exists
            spec = service.get_metric(name)
            if spec is None:
                return ValidationResult(
                    valid=False,
                    errors=[f"Metric '{name}' not found"],
                )

            # Validate
            results = service.validate(name, self.context.parameters)

            errors: list[str] = []
            warnings: list[str] = []

            for result in results:
                errors.extend(result.errors)
                warnings.extend(result.warnings)

            # In strict mode, warnings become errors
            if strict and warnings:
                errors.extend(warnings)
                warnings = []

            return ValidationResult(
                valid=len(errors) == 0,
                errors=errors,
                warnings=warnings,
            )

        except Exception as e:
            return ValidationResult(
                valid=False,
                errors=[str(e)],
            )

    # === Registration ===

    def register(
        self,
        name: str,
        *,
        force: bool = False,
    ) -> None:
        """Register metric to Basecamp server.

        Args:
            name: Fully qualified metric name.
            force: If True, overwrite existing registration.

        Raises:
            MetricNotFoundError: If metric not found locally.
            ServerError: If server registration fails.
        """
        if self._is_mock_mode:
            return

        raise NotImplementedError("Server registration not implemented")

    # === SQL Rendering ===

    def render_sql(
        self,
        name: str,
        *,
        parameters: dict[str, Any] | None = None,
        format_sql: bool = True,
    ) -> str:
        """Render metric SQL with Jinja and optional formatting.

        Args:
            name: Fully qualified metric name.
            parameters: Parameters for Jinja rendering.
            format_sql: If True, format SQL for readability.

        Returns:
            Rendered SQL string.

        Raises:
            MetricNotFoundError: If metric not found.
        """
        if self._is_mock_mode:
            return f"-- Mock SQL for {name}"

        service = self._get_service()

        # Merge parameters
        merged_params = {**self.context.parameters, **(parameters or {})}

        if format_sql:
            result = service.format_sql(name, merged_params)
        else:
            result = service.render_sql(name, merged_params)

        if result is None:
            raise MetricNotFoundError(
                message=f"Metric '{name}' not found",
                name=name,
            )

        return result

    # === Introspection ===

    def get_tables(self, name: str) -> list[str]:
        """Extract tables referenced in metric SQL.

        Args:
            name: Fully qualified metric name.

        Returns:
            List of table names.
        """
        if self._is_mock_mode:
            return []

        service = self._get_service()
        return service.get_tables(name, self.context.parameters)

    def get_columns(self, name: str) -> list[str]:
        """Extract columns from metric's SELECT clause.

        Args:
            name: Fully qualified metric name.

        Returns:
            List of column names.
        """
        if self._is_mock_mode:
            return []

        service = self._get_service()
        return service.get_columns(name, self.context.parameters)

    def test_connection(self) -> bool:
        """Test database connection.

        Returns:
            True if connection is successful.
        """
        if self._is_mock_mode:
            return True

        service = self._get_service()
        return service.test_connection()


__all__ = ["MetricAPI"]
