"""Jinja2-based SQL template renderer for the DLI Core Engine.

This module provides the SQLRenderer class which renders SQL templates
with parameter validation and custom SQL-safe filters.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from jinja2 import Environment, FileSystemLoader, StrictUndefined

from dli.core.models import QueryParameter


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
        """Register custom Jinja2 filters for SQL rendering."""
        # SQL string escaping (escape single quotes by doubling)
        self.env.filters["sql_string"] = self._sql_string_filter
        # List to IN clause
        self.env.filters["sql_list"] = self._sql_list_filter
        # SQL date formatting
        self.env.filters["sql_date"] = self._sql_date_filter
        # SQL identifier quoting
        self.env.filters["sql_identifier"] = self._sql_identifier_filter

    @staticmethod
    def _sql_string_filter(value: Any) -> str:
        """Escape a value for safe use in SQL strings.

        Args:
            value: The value to escape

        Returns:
            SQL-safe escaped string with single quotes
        """
        if value is None:
            return "NULL"
        escaped = str(value).replace("'", "''")
        return f"'{escaped}'"

    @staticmethod
    def _sql_list_filter(values: list[Any]) -> str:
        """Convert a list to a SQL IN clause.

        Args:
            values: List of values

        Returns:
            Comma-separated values in parentheses
        """
        if not values:
            return "(NULL)"

        formatted = []
        for v in values:
            if isinstance(v, str):
                escaped = v.replace("'", "''")
                formatted.append(f"'{escaped}'")
            elif v is None:
                formatted.append("NULL")
            else:
                formatted.append(str(v))

        return f"({', '.join(formatted)})"

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

    @staticmethod
    def _sql_identifier_filter(value: Any) -> str:
        """Quote a SQL identifier.

        Args:
            value: Identifier to quote (string or None)

        Returns:
            Double-quoted identifier, or empty quotes for None
        """
        if value is None:
            return '""'
        # Escape double quotes by doubling them
        escaped = str(value).replace('"', '""')
        return f'"{escaped}"'

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
