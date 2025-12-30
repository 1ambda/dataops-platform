"""DatasetAPI - Library API for Dataset operations.

This module provides the DatasetAPI class which wraps the DatasetService
for programmatic access to dataset operations.

Example:
    >>> from dli import DatasetAPI, ExecutionContext
    >>> ctx = ExecutionContext(project_path="/path/to/project")
    >>> api = DatasetAPI(context=ctx)
    >>> datasets = api.list_datasets()
    >>> result = api.run("catalog.schema.my_dataset", dry_run=True)
"""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING, Any

from dli.exceptions import (
    ConfigurationError,
    DatasetNotFoundError,
    ErrorCode,
    ExecutionError,
)
from dli.models.common import (
    DatasetResult,
    DataSource,
    ExecutionContext,
    ResultStatus,
    SQLDialect,
    ValidationResult,
)

if TYPE_CHECKING:
    from dli.core.models.dataset import DatasetSpec
    from dli.core.service import DatasetService


class DatasetAPI:
    """Dataset management Library API.

    Provides programmatic access to dataset operations including:
    - Listing and searching datasets
    - Validating dataset specs and SQL
    - Rendering SQL with Jinja templates
    - Executing datasets (with executor)
    - Registering datasets to server

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations (standard pattern for Kubernetes Airflow).

    Example:
        >>> from dli import DatasetAPI, ExecutionContext
        >>> ctx = ExecutionContext(
        ...     project_path="/opt/airflow/dags/models",
        ...     parameters={"execution_date": "2025-01-01"},
        ... )
        >>> api = DatasetAPI(context=ctx)
        >>> result = api.run("catalog.schema.dataset", dry_run=True)
        >>> print(result.status)
    """

    def __init__(self, context: ExecutionContext | None = None) -> None:
        """Initialize DatasetAPI.

        Args:
            context: Execution context with settings. If None, creates
                     default context from environment variables.
        """
        self.context = context or ExecutionContext()
        self._service: DatasetService | None = None

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"DatasetAPI(context={self.context!r})"

    def _get_service(self) -> DatasetService:
        """Get or create DatasetService instance (lazy initialization).

        Returns:
            DatasetService instance.

        Raises:
            ConfigurationError: If project_path is required but not set.
        """
        if self._service is None:
            # Import here to avoid circular imports and allow mock mode
            from dli.core.service import DatasetService as DatasetServiceImpl

            project_path = self.context.project_path
            if project_path is None and not self.context.mock_mode:
                msg = "project_path is required for DatasetAPI"
                raise ConfigurationError(message=msg, code=ErrorCode.CONFIG_INVALID)

            self._service = DatasetServiceImpl(project_path=project_path)

        return self._service

    # === CRUD Operations ===

    def list_datasets(
        self,
        path: Path | None = None,
        *,
        source: DataSource = "local",
        domain: str | None = None,
        owner: str | None = None,
        tags: list[str] | None = None,
        catalog: str | None = None,
        schema: str | None = None,
    ) -> list[DatasetSpec]:
        """List datasets with optional filtering.

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
            List of DatasetSpec objects.

        Raises:
            ConfigurationError: If configuration is invalid.
        """
        # TODO: Implement path override and server source
        _ = path, source  # Suppress unused variable warnings
        if self.context.mock_mode:
            return []

        service = self._get_service()

        # Apply tag filter if provided (service doesn't support multiple tags)
        tag = tags[0] if tags else None

        return service.list_datasets(
            tag=tag,
            domain=domain,
            catalog=catalog,
            schema=schema,
            owner=owner,
        )

    def get(self, name: str) -> DatasetSpec | None:
        """Get dataset by name.

        Args:
            name: Fully qualified dataset name (catalog.schema.name).

        Returns:
            DatasetSpec if found, None otherwise.
        """
        if self.context.mock_mode:
            return None

        service = self._get_service()
        return service.get_dataset(name)

    # === Execution ===

    def run(
        self,
        name: str,
        *,
        parameters: dict[str, Any] | None = None,
        dry_run: bool = False,
        show_sql: bool = False,
    ) -> DatasetResult:
        """Execute a dataset.

        Args:
            name: Fully qualified dataset name.
            parameters: Runtime parameters (merged with context.parameters).
            dry_run: If True, validate and render SQL without execution.
            show_sql: If True, include rendered SQL in result.

        Returns:
            DatasetResult with execution status and details.

        Raises:
            DatasetNotFoundError: If dataset not found.
            DLIValidationError: If validation fails.
            ExecutionError: If execution fails.
        """
        started_at = datetime.now(tz=UTC)

        # Merge parameters
        merged_params = {**self.context.parameters, **(parameters or {})}

        if self.context.mock_mode:
            return DatasetResult(
                name=name,
                status=ResultStatus.SUCCESS,
                started_at=started_at,
                ended_at=datetime.now(tz=UTC),
                duration_ms=0,
                sql="-- Mock SQL" if show_sql else None,
            )

        try:
            service = self._get_service()

            # Check if dataset exists
            spec = service.get_dataset(name)
            if spec is None:
                raise DatasetNotFoundError(
                    message=f"Dataset '{name}' not found",
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
                rendered = service.render_sql(name, merged_params)
                if rendered:
                    sql = rendered.get("main", "")
                    if isinstance(sql, list):
                        sql = sql[0] if sql else ""

            return DatasetResult(
                name=name,
                status=ResultStatus.SUCCESS if result.success else ResultStatus.FAILURE,
                started_at=started_at,
                ended_at=ended_at,
                duration_ms=duration_ms,
                sql=sql,
                error_message=result.error_message,
            )

        except DatasetNotFoundError:
            raise
        except Exception as e:
            ended_at = datetime.now(tz=UTC)
            duration_ms = int((ended_at - started_at).total_seconds() * 1000)

            raise ExecutionError(
                message=f"Dataset execution failed: {e}",
                cause=e,
            ) from e

    def run_sql(
        self,
        sql: str,
        *,
        parameters: dict[str, Any] | None = None,
        transpile: bool = True,
        dialect: SQLDialect | None = None,
    ) -> DatasetResult:
        """Execute arbitrary SQL directly.

        Useful for Airflow inline SQL execution.

        Args:
            sql: SQL string to execute.
            parameters: Parameters for Jinja rendering.
            transpile: Whether to apply transpile rules.
            dialect: SQL dialect (defaults to context.dialect).

        Returns:
            DatasetResult with execution status.

        Raises:
            ExecutionError: If execution fails.
        """
        started_at = datetime.now(tz=UTC)

        if self.context.mock_mode:
            return DatasetResult(
                name="<inline>",
                status=ResultStatus.SUCCESS,
                started_at=started_at,
                ended_at=datetime.now(tz=UTC),
                duration_ms=0,
                sql=sql,
            )

        # For now, return not implemented
        return DatasetResult(
            name="<inline>",
            status=ResultStatus.FAILURE,
            started_at=started_at,
            ended_at=datetime.now(tz=UTC),
            duration_ms=0,
            error_message="Direct SQL execution not implemented",
        )

    # === Validation ===

    def validate(
        self,
        name: str,
        *,
        strict: bool = False,
        check_deps: bool = False,
    ) -> ValidationResult:
        """Validate dataset spec and SQL.

        Args:
            name: Fully qualified dataset name.
            strict: If True, treat warnings as errors.
            check_deps: If True, validate dependencies exist.

        Returns:
            ValidationResult with validation status.
        """
        if self.context.mock_mode:
            return ValidationResult(valid=True)

        try:
            service = self._get_service()

            # Check if dataset exists
            spec = service.get_dataset(name)
            if spec is None:
                return ValidationResult(
                    valid=False,
                    errors=[f"Dataset '{name}' not found"],
                )

            # Validate with empty params (spec validation)
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
        """Register dataset to Basecamp server.

        Args:
            name: Fully qualified dataset name.
            force: If True, overwrite existing registration.

        Raises:
            DatasetNotFoundError: If dataset not found locally.
            ServerError: If server registration fails.
        """
        if self.context.mock_mode:
            return

        # Implementation would call BasecampClient
        # For now, raise not implemented
        raise NotImplementedError("Server registration not implemented")

    # === SQL Rendering ===

    def render_sql(
        self,
        name: str,
        *,
        parameters: dict[str, Any] | None = None,
        format_sql: bool = True,
    ) -> str:
        """Render dataset SQL with Jinja and optional formatting.

        Args:
            name: Fully qualified dataset name.
            parameters: Parameters for Jinja rendering.
            format_sql: If True, format SQL for readability.

        Returns:
            Rendered SQL string.

        Raises:
            DatasetNotFoundError: If dataset not found.
        """
        if self.context.mock_mode:
            return f"-- Mock SQL for {name}"

        service = self._get_service()

        # Merge parameters
        merged_params = {**self.context.parameters, **(parameters or {})}

        if format_sql:
            result = service.format_sql(name, merged_params)
        else:
            result = service.render_sql(name, merged_params)

        if result is None:
            raise DatasetNotFoundError(
                message=f"Dataset '{name}' not found",
                name=name,
            )

        # Extract main SQL
        main = result.get("main", "")
        if isinstance(main, list):
            return main[0] if main else ""
        return main

    # === Introspection ===

    def get_tables(self, name: str) -> list[str]:
        """Extract tables referenced in dataset SQL.

        Args:
            name: Fully qualified dataset name.

        Returns:
            List of table names.
        """
        if self.context.mock_mode:
            return []

        service = self._get_service()
        return service.get_tables(name, self.context.parameters)

    def get_columns(self, name: str) -> list[str]:
        """Extract columns from dataset's SELECT clause.

        Args:
            name: Fully qualified dataset name.

        Returns:
            List of column names.
        """
        if self.context.mock_mode:
            return []

        service = self._get_service()
        return service.get_columns(name, self.context.parameters)

    def test_connection(self) -> bool:
        """Test database connection.

        Returns:
            True if connection is successful.
        """
        if self.context.mock_mode:
            return True

        service = self._get_service()
        return service.test_connection()


__all__ = ["DatasetAPI"]
