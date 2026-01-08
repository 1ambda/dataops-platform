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
    FormatError,
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
from dli.models.format import (
    FileFormatResult,
    FileFormatStatus,
    FormatResult,
    FormatStatus,
    LintViolation,
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
        limit: int | None = None,
    ) -> MetricResult:
        """Execute a metric (SELECT query).

        Both LOCAL and SERVER modes use the Execution API for consistency.
        The Execution API handles query execution via the server.

        Args:
            name: Fully qualified metric name.
            parameters: Runtime parameters (merged with context.parameters).
            dry_run: If True, validate and render SQL without execution.
            show_sql: If True, include rendered SQL in result.
            limit: Maximum number of rows to return.

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

            # Render SQL locally
            rendered_sql = service.render_sql(name, merged_params)
            if not rendered_sql:
                raise ExecutionError(message="Failed to render SQL")

            if actual_dry_run:
                # Dry run: just validate and return
                ended_at = datetime.now(tz=UTC)
                return MetricResult(
                    name=name,
                    status=ResultStatus.SUCCESS,
                    started_at=started_at,
                    ended_at=ended_at,
                    duration_ms=int((ended_at - started_at).total_seconds() * 1000),
                    sql=rendered_sql if show_sql else None,
                )

            # Execute via Execution API (LOCAL and SERVER both use this)
            from dli.core.client import create_client

            # Create client for API calls
            # Note: use mock_mode when server_url is not configured (test environment)
            use_mock = self.context.server_url is None
            client = create_client(
                url=self.context.server_url,
                timeout=self.context.timeout,
                api_key=self.context.api_token,
                mock_mode=use_mock,
            )

            response = client.execute_rendered_metric(
                rendered_sql=rendered_sql,
                resource_name=name,
                parameters=merged_params,
                execution_timeout=self.context.timeout,
                execution_limit=limit,
                transpile_source_dialect=self.context.dialect,
                transpile_target_dialect=None,
            )

            ended_at = datetime.now(tz=UTC)
            duration_ms = int((ended_at - started_at).total_seconds() * 1000)

            if response.success:
                # Extract row_count and rows from response data
                row_count = 0
                rows = None
                if response.data and isinstance(response.data, dict):
                    row_count = response.data.get("row_count", 0)
                    rows = response.data.get("rows")

                return MetricResult(
                    name=name,
                    status=ResultStatus.SUCCESS,
                    started_at=started_at,
                    ended_at=ended_at,
                    duration_ms=duration_ms,
                    sql=rendered_sql if show_sql else None,
                    data=rows,
                    row_count=row_count,
                )
            else:
                return MetricResult(
                    name=name,
                    status=ResultStatus.FAILURE,
                    started_at=started_at,
                    ended_at=ended_at,
                    duration_ms=duration_ms,
                    sql=rendered_sql if show_sql else None,
                    error_message=response.error or "Execution failed",
                )

        except MetricNotFoundError:
            raise
        except Exception as e:
            ended_at = datetime.now(tz=UTC)

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

    # === Formatting ===

    def format(
        self,
        name: str,
        *,
        check_only: bool = False,
        sql_only: bool = False,
        yaml_only: bool = False,
        dialect: str | None = None,
        lint: bool = False,
        fix: bool = False,
    ) -> FormatResult:
        """Format metric SQL and YAML files.

        Formats metric spec files using sqlfluff for SQL and ruamel.yaml for YAML.
        Supports Jinja template preservation and multiple SQL dialects.

        Args:
            name: Fully qualified metric name (catalog.schema.name).
            check_only: If True, only check without modifying files.
            sql_only: Format SQL file only (skip YAML).
            yaml_only: Format YAML file only (skip SQL).
            dialect: SQL dialect (bigquery, trino, snowflake, etc.).
                    If None, uses project configuration or defaults to bigquery.
            lint: Apply lint rules (requires sqlfluff).
            fix: Auto-fix lint violations (requires lint=True).

        Returns:
            FormatResult with status and file changes.

        Raises:
            MetricNotFoundError: If metric not found.
            FormatError: If formatting fails.

        Example:
            >>> api = MetricAPI(context=ctx)
            >>> result = api.format("catalog.schema.my_metric", check_only=True)
            >>> if result.has_changes:
            ...     print(f"Would change {result.changed_count} files")
            >>> else:
            ...     print("No changes needed")
        """
        if self._is_mock_mode:
            return FormatResult(
                name=name,
                resource_type="metric",
                status=FormatStatus.SUCCESS,
                check_mode=check_only,
                lint_enabled=lint,
                message="Mock mode - no formatting performed",
            )

        # Validate mutual exclusivity
        if sql_only and yaml_only:
            raise FormatError(
                message="Cannot use both --sql-only and --yaml-only",
                resource_name=name,
            )

        # Get metric spec
        service = self._get_service()
        spec = service.get_metric(name)
        if spec is None:
            raise MetricNotFoundError(
                message=f"Metric '{name}' not found",
                name=name,
            )

        # Perform formatting
        file_results: list[FileFormatResult] = []
        overall_status = FormatStatus.SUCCESS

        try:
            # Import formatters
            from dli.core.format import (
                SqlFormatter,
                YamlFormatter,
                load_format_config,
            )

            # Load format configuration
            project_path = self.context.project_path
            config = load_format_config(project_path) if project_path else None

            # Determine effective dialect
            effective_dialect = dialect or (config.sql.dialect if config else "bigquery")

            # Format SQL if not yaml_only (metrics use query_statement or query_file)
            sql_file = getattr(spec, "query_file", None)
            if not yaml_only and sql_file:
                sql_path = Path(sql_file)
                if project_path and not sql_path.is_absolute():
                    sql_path = project_path / sql_path

                if sql_path.exists():
                    sql_formatter = SqlFormatter(
                        dialect=effective_dialect,
                        config=config,
                        project_path=project_path,
                    )
                    sql_result = sql_formatter.format_file(
                        sql_path,
                        check_only=check_only,
                        lint=lint,
                    )

                    # Convert to FileFormatResult
                    lint_violations = [
                        LintViolation(
                            rule=v.get("rule", "unknown"),
                            line=int(v.get("line", 0)),
                            column=int(v.get("column", 1)),
                            description=v.get("description", ""),
                            severity="warning",
                        )
                        for v in sql_result.violations
                    ]

                    file_results.append(
                        FileFormatResult(
                            path=str(sql_path.relative_to(project_path) if project_path else sql_path),
                            status=FileFormatStatus.CHANGED if sql_result.changed else FileFormatStatus.UNCHANGED,
                            original=sql_result.original if sql_result.changed else None,
                            formatted=sql_result.formatted if sql_result.changed else None,
                            changes=sql_result.get_diff(),
                            lint_violations=lint_violations,
                            error_message=sql_result.error,
                        )
                    )

                    if sql_result.error:
                        overall_status = FormatStatus.FAILED
                    elif sql_result.changed and overall_status != FormatStatus.FAILED:
                        overall_status = FormatStatus.CHANGED

            # Format YAML if not sql_only
            if not sql_only and hasattr(spec, "spec_path") and spec.spec_path:
                yaml_path = Path(spec.spec_path)
                if yaml_path.exists():
                    yaml_formatter = YamlFormatter(config=config)
                    yaml_result = yaml_formatter.format_file(
                        yaml_path,
                        check_only=check_only,
                        reorder_keys=True,
                    )

                    file_results.append(
                        FileFormatResult(
                            path=str(yaml_path.relative_to(project_path) if project_path else yaml_path),
                            status=FileFormatStatus.CHANGED if yaml_result.changed else FileFormatStatus.UNCHANGED,
                            original=yaml_result.original if yaml_result.changed else None,
                            formatted=yaml_result.formatted if yaml_result.changed else None,
                            changes=yaml_result.get_diff(),
                            error_message=yaml_result.error,
                        )
                    )

                    if yaml_result.error:
                        overall_status = FormatStatus.FAILED
                    elif yaml_result.changed and overall_status != FormatStatus.FAILED:
                        overall_status = FormatStatus.CHANGED

            return FormatResult(
                name=name,
                resource_type="metric",
                status=overall_status,
                files=file_results,
                check_mode=check_only,
                lint_enabled=lint,
            )

        except ImportError as e:
            # Formatters not installed
            return FormatResult(
                name=name,
                resource_type="metric",
                status=FormatStatus.FAILED,
                check_mode=check_only,
                lint_enabled=lint,
                message=f"Format dependencies not installed: {e}",
            )
        except Exception as e:
            raise FormatError(
                message=f"Formatting failed: {e}",
                resource_name=name,
            ) from e


__all__ = ["MetricAPI"]
