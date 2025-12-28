"""Metric Service for the DLI Core Engine.

This module provides the MetricService class which integrates
all core components for metric (SELECT query) operations.
Designed for library usage in Airflow, scripts, or applications.

Example:
    >>> from dli.core import MetricService
    >>> service = MetricService(project_path="/path/to/project")
    >>> metrics = service.list_metrics(domain="analytics")
    >>> result = service.execute("iceberg.analytics.user_engagement", {"date": "2025-01-01"})
    >>> print(result.rows)
"""

from __future__ import annotations

from pathlib import Path
import time
from typing import Any

from dli.core.config import load_project
from dli.core.executor import BaseExecutor
from dli.core.models import (
    MetricExecutionResult,
    MetricSpec,
    ValidationResult,
)
from dli.core.registry import MetricRegistry
from dli.core.renderer import SQLRenderer
from dli.core.validator import SQLValidator


class MetricService:
    """Service for executing metrics (SELECT queries).

    Similar to DatasetService but specialized for MetricSpec (read-only queries).
    Designed for library usage in Airflow, scripts, or applications.

    Unlike DatasetService which handles 3-stage execution (Pre -> Main -> Post),
    MetricService executes a single SELECT query and returns result rows.

    Attributes:
        config: Project configuration
        registry: Metric registry
        renderer: SQL renderer
        validator: SQL validator

    Example:
        >>> from dli.core import MetricService
        >>> service = MetricService(project_path="/path/to/project")
        >>> metrics = service.list_metrics(domain="reporting")
        >>> result = service.execute("iceberg.reporting.user_summary", {"date": "2025-01-01"})
        >>> print(result.rows)
    """

    def __init__(
        self,
        project_path: Path | None = None,
        executor: BaseExecutor | None = None,
    ):
        """Initialize the Metric service.

        Args:
            project_path: Path to the project directory (defaults to DLI_HOME)
            executor: Optional executor for running queries
        """
        self.config = load_project(project_path)
        self.registry = MetricRegistry(self.config)
        self.renderer = SQLRenderer()
        self.validator = SQLValidator(self.config.defaults.get("dialect", "trino"))
        self._executor = executor

    def list_metrics(
        self,
        *,
        tag: str | None = None,
        domain: str | None = None,
        catalog: str | None = None,
        schema: str | None = None,
        owner: str | None = None,
    ) -> list[MetricSpec]:
        """List metrics with optional filtering.

        Args:
            tag: Filter by tag
            domain: Filter by domain
            catalog: Filter by catalog
            schema: Filter by schema
            owner: Filter by owner

        Returns:
            List of matching MetricSpec objects
        """
        return self.registry.search(
            tag=tag,
            domain=domain,
            catalog=catalog,
            schema=schema,
            owner=owner,
        )

    def get_metric(self, name: str) -> MetricSpec | None:
        """Get a metric by name.

        Args:
            name: Fully qualified metric name

        Returns:
            MetricSpec if found, None otherwise
        """
        return self.registry.get(name)

    def validate(
        self,
        metric_name: str,
        params: dict[str, Any],
    ) -> list[ValidationResult]:
        """Validate the SQL for a metric.

        Args:
            metric_name: Fully qualified metric name
            params: Parameter values for SQL rendering

        Returns:
            List containing a single ValidationResult
        """
        spec = self.registry.get(metric_name)
        if not spec:
            return [
                ValidationResult(
                    is_valid=False,
                    errors=[f"Metric '{metric_name}' not found"],
                )
            ]

        try:
            rendered = self.render_sql(metric_name, params)
            if not rendered:
                return [
                    ValidationResult(
                        is_valid=False,
                        errors=["Failed to render SQL"],
                        phase="main",
                    )
                ]
            result = self.validator.validate(rendered, phase="main")
        except (OSError, ValueError) as e:
            return [
                ValidationResult(
                    is_valid=False,
                    errors=[f"Main query: {e}"],
                    phase="main",
                )
            ]
        else:
            return [result]

    def render_sql(
        self,
        metric_name: str,
        params: dict[str, Any],
    ) -> str | None:
        """Render the SQL for a metric.

        Supports dbt/SQLMesh-compatible template functions including ref(),
        using the depends_on field to resolve references.

        Args:
            metric_name: Fully qualified metric name
            params: Parameter values for SQL rendering

        Returns:
            Rendered SQL string, or None if metric not found
        """
        spec = self.registry.get(metric_name)
        if not spec:
            return None

        main_sql = spec.get_main_sql()

        # Build refs dictionary from depends_on
        refs = self._build_refs(spec.depends_on)

        # Validate parameters first
        validated_params: dict[str, Any] = {}
        for param_def in spec.parameters:
            value = params.get(param_def.name)
            validated_params[param_def.name] = param_def.validate_value(value)

        # Include any extra parameters
        for key, value in params.items():
            if key not in validated_params:
                validated_params[key] = value

        # Use render_with_template_context for ref() support
        return self.renderer.render_with_template_context(
            main_sql,
            refs=refs,
            extra_params=validated_params,
        )

    def _build_refs(self, depends_on: list[str]) -> dict[str, str]:
        """Build a refs dictionary from depends_on list.

        For each dependency, maps the full name to itself.
        This allows `{{ ref('iceberg.raw.user_events') }}` to resolve
        to `iceberg.raw.user_events`.

        Args:
            depends_on: List of dependency names

        Returns:
            Dictionary mapping ref names to their values
        """
        return {dep: dep for dep in depends_on}

    def format_sql(
        self,
        metric_name: str,
        params: dict[str, Any],
        pretty: bool = True,
    ) -> str | None:
        """Format the SQL for a metric.

        Args:
            metric_name: Fully qualified metric name
            params: Parameter values for SQL rendering
            pretty: Whether to use multi-line formatting

        Returns:
            Formatted SQL string, or None if metric not found
        """
        rendered = self.render_sql(metric_name, params)
        if not rendered:
            return None

        return self.validator.format_sql(rendered, pretty=pretty)

    def execute(  # noqa: PLR0911 - Multiple early returns for validation clarity
        self,
        metric_name: str,
        params: dict[str, Any],
        *,
        dry_run: bool = False,
    ) -> MetricExecutionResult:
        """Execute a metric (SELECT query).

        Args:
            metric_name: Fully qualified metric name
            params: Parameter values for SQL rendering
            dry_run: Perform validation only without execution

        Returns:
            MetricExecutionResult with query results
        """
        if not self._executor:
            return MetricExecutionResult(
                metric_name=metric_name,
                success=False,
                error_message="Executor not configured",
            )

        spec = self.registry.get(metric_name)
        if not spec:
            return MetricExecutionResult(
                metric_name=metric_name,
                success=False,
                error_message=f"Metric '{metric_name}' not found",
            )

        # Render SQL
        try:
            rendered_sql = self.render_sql(metric_name, params)
            if not rendered_sql:
                return MetricExecutionResult(
                    metric_name=metric_name,
                    success=False,
                    error_message="Failed to render SQL",
                )
        except (OSError, ValueError) as e:
            return MetricExecutionResult(
                metric_name=metric_name,
                success=False,
                error_message=f"SQL rendering failed: {e}",
            )

        # Validate SQL
        validation_result = self.validator.validate(rendered_sql, phase="main")
        if not validation_result.is_valid:
            return MetricExecutionResult(
                metric_name=metric_name,
                success=False,
                error_message=f"Validation failed: {validation_result.errors}",
                rendered_sql=rendered_sql,
            )

        # Dry run - return success without execution
        if dry_run:
            return MetricExecutionResult(
                metric_name=metric_name,
                success=True,
                error_message="Dry run completed (no execution)",
                rendered_sql=rendered_sql,
            )

        # Execute
        start_time = time.time()
        timeout = spec.execution.timeout_seconds
        exec_result = self._executor.execute_sql(rendered_sql, timeout)
        execution_time_ms = (time.time() - start_time) * 1000

        if not exec_result.success:
            return MetricExecutionResult(
                metric_name=metric_name,
                success=False,
                error_message=exec_result.error_message,
                rendered_sql=rendered_sql,
                execution_time_ms=execution_time_ms,
            )

        return MetricExecutionResult(
            metric_name=metric_name,
            success=True,
            rows=exec_result.data,
            row_count=exec_result.row_count or len(exec_result.data),
            columns=exec_result.columns,
            rendered_sql=rendered_sql,
            execution_time_ms=execution_time_ms,
        )

    def get_tables(self, metric_name: str, params: dict[str, Any]) -> list[str]:
        """Extract tables referenced in a metric.

        Args:
            metric_name: Fully qualified metric name
            params: Parameter values for SQL rendering

        Returns:
            List of table names
        """
        rendered = self.render_sql(metric_name, params)
        if not rendered:
            return []

        return self.validator.extract_tables(rendered)

    def get_columns(self, metric_name: str, params: dict[str, Any]) -> list[str]:
        """Extract columns from a metric's SELECT clause.

        Args:
            metric_name: Fully qualified metric name
            params: Parameter values for SQL rendering

        Returns:
            List of column names
        """
        rendered = self.render_sql(metric_name, params)
        if not rendered:
            return []

        return self.validator.extract_columns(rendered)

    def reload(self) -> None:
        """Reload metric specs from disk."""
        self.registry.reload()

    def test_connection(self) -> bool:
        """Test the executor connection.

        Returns:
            True if connection is successful, False otherwise
        """
        if not self._executor:
            return False
        return self._executor.test_connection()

    @property
    def project_name(self) -> str:
        """Get the project name."""
        return self.config.project_name

    @property
    def default_dialect(self) -> str:
        """Get the default SQL dialect."""
        return self.config.default_dialect

    def get_catalogs(self) -> list[str]:
        """Get all unique catalog names."""
        return self.registry.get_catalogs()

    def get_schemas(self, catalog: str | None = None) -> list[str]:
        """Get all unique schema names."""
        return self.registry.get_schemas(catalog)

    def get_domains(self) -> list[str]:
        """Get all unique domain names."""
        return self.registry.get_domains()

    def get_tags(self) -> list[str]:
        """Get all unique tag names."""
        return self.registry.get_tags()

    def get_owners(self) -> list[str]:
        """Get all unique owners."""
        return self.registry.get_owners()
