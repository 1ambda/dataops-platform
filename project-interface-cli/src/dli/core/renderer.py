"""Jinja2-based SQL template renderer for the DLI Core Engine.

This module provides the SQLRenderer class which renders SQL templates
with parameter validation and custom SQL-safe filters.

Integrates with TemplateContext to provide dbt/SQLMesh-compatible
template variables (ds, ds_nodash, var(), date_add(), etc.).

Note:
    This renderer uses a regular Jinja2 Environment (not sandboxed) for
    performance. It is intended for trusted templates loaded from the
    local filesystem. For untrusted/user-provided templates, use
    SafeTemplateRenderer from dli.core.templates instead.
"""

from __future__ import annotations

from datetime import date
from pathlib import Path
from typing import Any

from jinja2 import Environment, FileSystemLoader, StrictUndefined

from dli.core.models import QueryParameter
from dli.core.templates import (
    TemplateContext,
    sql_identifier_escape,
    sql_list_escape,
    sql_string_escape,
)


class SQLRenderer:
    """Jinja2-based SQL template renderer with custom SQL filters.

    The renderer provides:
    - Parameter validation before rendering
    - Custom filters for SQL-safe string escaping
    - Support for list parameters in IN clauses

    Attributes:
        env: Jinja2 environment for template rendering
    """

    def __init__(self, templates_dir: str | Path = "."):
        """Initialize the renderer with a templates directory.

        Args:
            templates_dir: Base directory for template loading
        """
        # Note: autoescape=False is intentional for SQL templates
        # HTML escaping would break SQL syntax (e.g., escaping < > characters)
        self.env = Environment(
            loader=FileSystemLoader(str(templates_dir)),
            trim_blocks=True,
            lstrip_blocks=True,
            undefined=StrictUndefined,
            autoescape=False,  # noqa: S701
        )
        self._register_filters()

    def _register_filters(self) -> None:
        """Register custom Jinja2 filters for SQL rendering.

        Uses shared filter functions from dli.core.templates for consistency
        with SafeJinjaEnvironment.
        """
        # SQL string escaping (shared with SafeJinjaEnvironment)
        self.env.filters["sql_string"] = sql_string_escape
        # List to IN clause (shared with SafeJinjaEnvironment)
        self.env.filters["sql_list"] = sql_list_escape
        # SQL identifier quoting (shared with SafeJinjaEnvironment)
        self.env.filters["sql_identifier"] = sql_identifier_escape
        # SQL date formatting (SQLRenderer-specific)
        self.env.filters["sql_date"] = self._sql_date_filter

    @staticmethod
    def _sql_date_filter(value: Any, fmt: str = "%Y-%m-%d") -> str:
        """Format a date value for SQL.

        Args:
            value: Date value (string or date object)
            fmt: Date format string

        Returns:
            SQL-safe date string with single quotes
        """
        if value is None:
            return "NULL"
        # If it's already a string, use it directly
        if isinstance(value, str):
            return f"'{value}'"
        # Otherwise, try to format it
        try:
            return f"'{value.strftime(fmt)}'"
        except AttributeError:
            return f"'{value}'"

    def render(
        self,
        template_str: str,
        parameters: list[QueryParameter],
        params: dict[str, Any],
    ) -> str:
        """Render a SQL template with validated parameters.

        Args:
            template_str: SQL template string with Jinja2 placeholders
            parameters: List of parameter definitions for validation
            params: Dictionary of parameter values

        Returns:
            Rendered SQL string

        Raises:
            ValueError: If parameter validation fails
            jinja2.exceptions.TemplateError: If template rendering fails
        """
        # Validate and convert parameters
        validated: dict[str, Any] = {}
        for param_def in parameters:
            value = params.get(param_def.name)
            validated[param_def.name] = param_def.validate_value(value)

        # Also include any extra parameters not in the definition
        # (for flexibility in templates)
        for key, value in params.items():
            if key not in validated:
                validated[key] = value

        # Render the template
        template = self.env.from_string(template_str)
        return template.render(**validated)

    def render_string(self, template_str: str, params: dict[str, Any]) -> str:
        """Render a SQL template string directly without validation.

        Args:
            template_str: SQL template string with Jinja2 placeholders
            params: Dictionary of parameter values

        Returns:
            Rendered SQL string
        """
        template = self.env.from_string(template_str)
        return template.render(**params)

    def render_file(
        self,
        file_path: Path,
        parameters: list[QueryParameter],
        params: dict[str, Any],
    ) -> str:
        """Render a SQL template file with validated parameters.

        Args:
            file_path: Path to the SQL template file
            parameters: List of parameter definitions for validation
            params: Dictionary of parameter values

        Returns:
            Rendered SQL string

        Raises:
            ValueError: If parameter validation fails
            FileNotFoundError: If the template file is not found
            jinja2.exceptions.TemplateError: If template rendering fails
        """
        template_str = file_path.read_text(encoding="utf-8")
        return self.render(template_str, parameters, params)

    def render_with_template_context(
        self,
        template_str: str,
        execution_date: date | None = None,
        variables: dict[str, Any] | None = None,
        refs: dict[str, str] | None = None,
        extra_params: dict[str, Any] | None = None,
    ) -> str:
        """Render a SQL template with TemplateContext (dbt/SQLMesh compatible).

        Provides access to template variables like ds, ds_nodash, and functions
        like var(), date_add(), date_sub(), ref(), source().

        Args:
            template_str: SQL template string with Jinja2 placeholders
            execution_date: Execution date for template context (defaults to today)
            variables: Project variables accessible via var()
            refs: Dataset references accessible via ref()
            extra_params: Additional parameters to merge with context

        Returns:
            Rendered SQL string

        Example:
            >>> renderer = SQLRenderer()
            >>> sql = renderer.render_with_template_context(
            ...     "SELECT * FROM {{ ref('users') }} WHERE dt = '{{ ds }}'",
            ...     execution_date=date(2025, 1, 15),
            ...     refs={"users": "prod.analytics.users"},
            ... )
            >>> print(sql)
            SELECT * FROM prod.analytics.users WHERE dt = '2025-01-15'
        """
        context = TemplateContext(
            execution_date=execution_date,
            variables=variables,
            refs=refs,
        )

        render_context = context.to_dict()
        if extra_params:
            render_context.update(extra_params)

        template = self.env.from_string(template_str)
        return template.render(**render_context)
