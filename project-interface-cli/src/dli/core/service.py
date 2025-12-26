"""Dataset Service for the DLI Core Engine.

This module provides the DatasetService class which integrates
all core components into a unified service layer.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from dli.core.discovery import load_project
from dli.core.executor import BaseExecutor, DatasetExecutor
from dli.core.models import (
    DatasetExecutionResult,
    DatasetSpec,
    ValidationResult,
)
from dli.core.registry import DatasetRegistry
from dli.core.renderer import SQLRenderer
from dli.core.validator import SQLValidator


class DatasetService:
    """Unified service for the DLI Core Engine.

    This service integrates all core components and provides a simple
    interface for CLI, Web, and Airflow integrations.

    Attributes:
        config: Project configuration
        registry: Dataset registry
        renderer: SQL renderer
        validator: SQL validator
    """

    def __init__(
        self,
        project_path: Path | None = None,
        executor: BaseExecutor | None = None,
    ):
        """Initialize the Dataset service.

        Args:
            project_path: Path to the project directory (defaults to DLI_HOME)
            executor: Optional executor for running queries
        """
        self.config = load_project(project_path)
        self.registry = DatasetRegistry(self.config)
        self.renderer = SQLRenderer()
        self.validator = SQLValidator(self.config.defaults.get("dialect", "trino"))
        self._executor = executor
        self._dataset_executor = DatasetExecutor(executor) if executor else None

    def list_datasets(
        self,
        *,
        tag: str | None = None,
        domain: str | None = None,
        catalog: str | None = None,
        schema: str | None = None,
        owner: str | None = None,
    ) -> list[DatasetSpec]:
        """List datasets with optional filtering.

        Args:
            tag: Filter by tag
            domain: Filter by domain
            catalog: Filter by catalog
            schema: Filter by schema
            owner: Filter by owner

        Returns:
            List of matching DatasetSpec objects
        """
        return self.registry.search(
            tag=tag,
            domain=domain,
            catalog=catalog,
            schema=schema,
            owner=owner,
        )

    def get_dataset(self, name: str) -> DatasetSpec | None:
        """Get a dataset by name.

        Args:
            name: Fully qualified dataset name

        Returns:
            DatasetSpec if found, None otherwise
        """
        return self.registry.get(name)

    def validate(
        self,
        dataset_name: str,
        params: dict[str, Any],
    ) -> list[ValidationResult]:
        """Validate all SQL for a dataset (Pre, Main, Post).

        Args:
            dataset_name: Fully qualified dataset name
            params: Parameter values for SQL rendering

        Returns:
            List of ValidationResult objects for each SQL statement
        """
        spec = self.registry.get(dataset_name)
        if not spec:
            return [
                ValidationResult(
                    is_valid=False,
                    errors=[f"Dataset '{dataset_name}' not found"],
                )
            ]

        results: list[ValidationResult] = []

        # Pre Statements
        for stmt in spec.pre_statements:
            try:
                sql = stmt.get_sql(spec.base_dir or Path.cwd())
                rendered = self.renderer.render(sql, spec.parameters, params)
                result = self.validator.validate(rendered, phase="pre")
                results.append(result)
            except (OSError, ValueError) as e:
                results.append(
                    ValidationResult(
                        is_valid=False,
                        errors=[f"Pre statement '{stmt.name}': {e}"],
                        phase="pre",
                    )
                )

        # Main Query
        try:
            main_sql = spec.get_main_sql()
            rendered = self.renderer.render(main_sql, spec.parameters, params)
            result = self.validator.validate(rendered, phase="main")
            results.append(result)
        except (OSError, ValueError) as e:
            results.append(
                ValidationResult(
                    is_valid=False,
                    errors=[f"Main query: {e}"],
                    phase="main",
                )
            )

        # Post Statements
        for stmt in spec.post_statements:
            try:
                sql = stmt.get_sql(spec.base_dir or Path.cwd())
                rendered = self.renderer.render(sql, spec.parameters, params)
                result = self.validator.validate(rendered, phase="post")
                results.append(result)
            except (OSError, ValueError) as e:
                results.append(
                    ValidationResult(
                        is_valid=False,
                        errors=[f"Post statement '{stmt.name}': {e}"],
                        phase="post",
                    )
                )

        return results

    def render_sql(
        self,
        dataset_name: str,
        params: dict[str, Any],
    ) -> dict[str, str | list[str]] | None:
        """Render all SQL for a dataset.

        Args:
            dataset_name: Fully qualified dataset name
            params: Parameter values for SQL rendering

        Returns:
            Dictionary with rendered SQL:
                - "pre": List of pre-statement SQL strings
                - "main": Main query SQL string
                - "post": List of post-statement SQL strings
            Returns None if dataset not found
        """
        spec = self.registry.get(dataset_name)
        if not spec:
            return None

        rendered_sqls: dict[str, str | list[str]] = {}

        # Pre Statements
        if spec.pre_statements:
            base_dir = spec.base_dir or Path.cwd()
            rendered_sqls["pre"] = [
                self.renderer.render(
                    stmt.get_sql(base_dir), spec.parameters, params
                )
                for stmt in spec.pre_statements
            ]

        # Main Query
        rendered_sqls["main"] = self.renderer.render(
            spec.get_main_sql(), spec.parameters, params
        )

        # Post Statements
        if spec.post_statements:
            base_dir = spec.base_dir or Path.cwd()
            rendered_sqls["post"] = [
                self.renderer.render(
                    stmt.get_sql(base_dir), spec.parameters, params
                )
                for stmt in spec.post_statements
            ]

        return rendered_sqls

    def execute(  # noqa: PLR0911 - Multiple early returns for validation clarity
        self,
        dataset_name: str,
        params: dict[str, Any],
        *,
        skip_pre: bool = False,
        skip_post: bool = False,
        dry_run: bool = False,
    ) -> DatasetExecutionResult:
        """Execute a dataset.

        Args:
            dataset_name: Fully qualified dataset name
            params: Parameter values for SQL rendering
            skip_pre: Skip pre-statements
            skip_post: Skip post-statements
            dry_run: Perform validation only without execution

        Returns:
            DatasetExecutionResult with execution results
        """
        if not self._dataset_executor:
            return DatasetExecutionResult(
                dataset_name=dataset_name,
                success=False,
                error_message="Executor not configured",
            )

        spec = self.registry.get(dataset_name)
        if not spec:
            return DatasetExecutionResult(
                dataset_name=dataset_name,
                success=False,
                error_message=f"Dataset '{dataset_name}' not found",
            )

        # Render SQL
        try:
            rendered_sqls = self.render_sql(dataset_name, params)
            if not rendered_sqls:
                return DatasetExecutionResult(
                    dataset_name=dataset_name,
                    success=False,
                    error_message="Failed to render SQL",
                )
        except (OSError, ValueError) as e:
            return DatasetExecutionResult(
                dataset_name=dataset_name,
                success=False,
                error_message=f"SQL rendering failed: {e}",
            )

        # Validate all SQL
        for phase, sqls in rendered_sqls.items():
            sql_list = sqls if isinstance(sqls, list) else [sqls]
            for sql in sql_list:
                result = self.validator.validate(sql, phase)
                if not result.is_valid:
                    return DatasetExecutionResult(
                        dataset_name=dataset_name,
                        success=False,
                        error_message=f"Validation failed in {phase}: {result.errors}",
                    )

        # Dry run - return success without execution
        if dry_run:
            return DatasetExecutionResult(
                dataset_name=dataset_name,
                success=True,
                error_message="Dry run completed (no execution)",
            )

        # Execute
        return self._dataset_executor.execute(
            spec,
            rendered_sqls,
            skip_pre=skip_pre,
            skip_post=skip_post,
        )

    def get_tables(self, dataset_name: str, params: dict[str, Any]) -> list[str]:
        """Extract tables referenced in a dataset.

        Args:
            dataset_name: Fully qualified dataset name
            params: Parameter values for SQL rendering

        Returns:
            List of table names
        """
        rendered = self.render_sql(dataset_name, params)
        if not rendered or "main" not in rendered:
            return []

        main_sql = rendered["main"]
        if isinstance(main_sql, list):
            main_sql = main_sql[0]

        return self.validator.extract_tables(main_sql)

    def get_columns(self, dataset_name: str, params: dict[str, Any]) -> list[str]:
        """Extract columns from a dataset's SELECT clause.

        Args:
            dataset_name: Fully qualified dataset name
            params: Parameter values for SQL rendering

        Returns:
            List of column names
        """
        rendered = self.render_sql(dataset_name, params)
        if not rendered or "main" not in rendered:
            return []

        main_sql = rendered["main"]
        if isinstance(main_sql, list):
            main_sql = main_sql[0]

        return self.validator.extract_columns(main_sql)

    def format_sql(
        self,
        dataset_name: str,
        params: dict[str, Any],
        pretty: bool = True,
    ) -> dict[str, str | list[str]] | None:
        """Format all SQL for a dataset.

        Args:
            dataset_name: Fully qualified dataset name
            params: Parameter values for SQL rendering
            pretty: Whether to use multi-line formatting

        Returns:
            Dictionary with formatted SQL
        """
        rendered = self.render_sql(dataset_name, params)
        if not rendered:
            return None

        formatted: dict[str, str | list[str]] = {}

        for phase, sqls in rendered.items():
            if isinstance(sqls, list):
                formatted[phase] = [
                    self.validator.format_sql(sql, pretty=pretty) for sql in sqls
                ]
            else:
                formatted[phase] = self.validator.format_sql(sqls, pretty=pretty)

        return formatted

    def reload(self) -> None:
        """Reload dataset specs from disk."""
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
