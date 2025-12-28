"""Safe templating context for DLI SQL templates.

Provides controlled Jinja2 environment with:
- Predefined variables (execution_date, ds, ds_nodash, etc.)
- Safe functions (date_add, date_sub, var, env_var, ref)
- Blocked dangerous features (no arbitrary Python execution)

Based on dbt/SQLMesh patterns (2025).
"""

from __future__ import annotations

import os
from datetime import date, timedelta
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from jinja2 import Environment


# =============================================================================
# Shared SQL Filters (used by both SafeJinjaEnvironment and SQLRenderer)
# =============================================================================


def sql_string_escape(value: Any) -> str:
    """Escape a value for safe use in SQL strings.

    Args:
        value: The value to escape.

    Returns:
        SQL-safe escaped string with single quotes, or NULL for None.

    Example:
        >>> sql_string_escape("O'Brien")
        "'O''Brien'"
        >>> sql_string_escape(None)
        'NULL'
    """
    if value is None:
        return "NULL"
    escaped = str(value).replace("'", "''")
    return f"'{escaped}'"


def sql_list_escape(values: list[Any]) -> str:
    """Convert a list to a SQL IN clause format.

    Args:
        values: List of values to format.

    Returns:
        Comma-separated values in parentheses, suitable for IN clauses.

    Example:
        >>> sql_list_escape([1, 2, 3])
        '(1, 2, 3)'
        >>> sql_list_escape(["a", "b"])
        "('a', 'b')"
        >>> sql_list_escape([])
        '(NULL)'
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


def sql_identifier_escape(value: Any) -> str:
    """Quote a SQL identifier with double quotes.

    Args:
        value: Identifier to quote.

    Returns:
        Double-quoted identifier.

    Example:
        >>> sql_identifier_escape("users")
        '"users"'
        >>> sql_identifier_escape('my"table')
        '"my""table"'
    """
    if value is None:
        return '""'
    escaped = str(value).replace('"', '""')
    return f'"{escaped}"'


class TemplateContext:
    """Safe template context with predefined variables and functions.

    Provides dbt/SQLMesh-compatible template variables and functions:
    - ds: Date string in YYYY-MM-DD format
    - ds_nodash: Date string in YYYYMMDD format
    - yesterday_ds: Yesterday's date
    - var(): Get project variable
    - env_var(): Get environment variable
    - date_add/date_sub(): Date arithmetic
    - ref(): Reference another dataset

    Example:
        >>> ctx = TemplateContext(execution_date=date(2025, 1, 15))
        >>> ctx.ds
        '2025-01-15'
        >>> ctx.ds_nodash
        '20250115'
        >>> ctx.date_add('2025-01-15', 7)
        '2025-01-22'
    """

    def __init__(
        self,
        execution_date: date | None = None,
        variables: dict[str, Any] | None = None,
        refs: dict[str, str] | None = None,
    ):
        """Initialize template context.

        Args:
            execution_date: The execution date for the template.
                Defaults to today if not provided.
            variables: Dictionary of project variables accessible via var().
            refs: Dictionary mapping dataset names to their resolved references.
        """
        self.execution_date = execution_date or date.today()
        self._variables = variables or {}
        self._refs: dict[str, str] = refs or {}

    # Predefined variables
    @property
    def ds(self) -> str:
        """Date string in YYYY-MM-DD format (dbt compatible)."""
        return self.execution_date.isoformat()

    @property
    def ds_nodash(self) -> str:
        """Date string in YYYYMMDD format."""
        return self.execution_date.strftime("%Y%m%d")

    @property
    def yesterday_ds(self) -> str:
        """Yesterday's date in YYYY-MM-DD format."""
        return (self.execution_date - timedelta(days=1)).isoformat()

    @property
    def tomorrow_ds(self) -> str:
        """Tomorrow's date in YYYY-MM-DD format."""
        return (self.execution_date + timedelta(days=1)).isoformat()

    @property
    def week_start_ds(self) -> str:
        """Monday of the execution week in YYYY-MM-DD format."""
        days_since_monday = self.execution_date.weekday()
        week_start = self.execution_date - timedelta(days=days_since_monday)
        return week_start.isoformat()

    @property
    def month_start_ds(self) -> str:
        """First day of the execution month in YYYY-MM-DD format."""
        return self.execution_date.replace(day=1).isoformat()

    @property
    def year(self) -> int:
        """Execution year as integer."""
        return self.execution_date.year

    @property
    def month(self) -> int:
        """Execution month as integer (1-12)."""
        return self.execution_date.month

    @property
    def day(self) -> int:
        """Execution day of month as integer (1-31)."""
        return self.execution_date.day

    # Safe functions
    def var(self, name: str, default: Any = None) -> Any:
        """Get project variable (dbt compatible).

        Args:
            name: Variable name to look up.
            default: Default value if variable is not found.

        Returns:
            Variable value or default.

        Example:
            >>> ctx = TemplateContext(variables={"schema": "prod"})
            >>> ctx.var("schema")
            'prod'
            >>> ctx.var("missing", "default")
            'default'
        """
        return self._variables.get(name, default)

    def env_var(self, name: str, default: str = "") -> str:
        """Get environment variable (dbt compatible).

        Args:
            name: Environment variable name.
            default: Default value if not found.

        Returns:
            Environment variable value or default.

        Example:
            >>> import os
            >>> os.environ["MY_VAR"] = "test"
            >>> ctx = TemplateContext()
            >>> ctx.env_var("MY_VAR")
            'test'
        """
        return os.environ.get(name, default)

    def date_add(self, date_str: str, days: int) -> str:
        """Add days to date string.

        Args:
            date_str: Date string in YYYY-MM-DD format.
            days: Number of days to add (can be negative).

        Returns:
            New date string in YYYY-MM-DD format.

        Example:
            >>> ctx = TemplateContext()
            >>> ctx.date_add('2025-01-15', 7)
            '2025-01-22'
            >>> ctx.date_add('2025-01-15', -1)
            '2025-01-14'
        """
        d = date.fromisoformat(date_str)
        return (d + timedelta(days=days)).isoformat()

    def date_sub(self, date_str: str, days: int) -> str:
        """Subtract days from date string.

        Args:
            date_str: Date string in YYYY-MM-DD format.
            days: Number of days to subtract.

        Returns:
            New date string in YYYY-MM-DD format.

        Example:
            >>> ctx = TemplateContext()
            >>> ctx.date_sub('2025-01-15', 7)
            '2025-01-08'
        """
        return self.date_add(date_str, -days)

    def ref(self, dataset_name: str) -> str:
        """Reference another dataset (dbt compatible).

        Args:
            dataset_name: Name of the dataset to reference.

        Returns:
            Resolved dataset reference, or the original name if not found.

        Example:
            >>> ctx = TemplateContext(refs={"users": "prod.analytics.users"})
            >>> ctx.ref("users")
            'prod.analytics.users'
        """
        return self._refs.get(dataset_name, dataset_name)

    def source(self, source_name: str, table_name: str) -> str:
        """Reference a source table (dbt compatible).

        Args:
            source_name: Name of the source.
            table_name: Name of the table within the source.

        Returns:
            Fully qualified source reference.

        Example:
            >>> ctx = TemplateContext()
            >>> ctx.source("raw", "events")
            'raw.events'
        """
        key = f"{source_name}.{table_name}"
        return self._refs.get(key, key)

    def to_dict(self) -> dict[str, Any]:
        """Export context as dictionary for Jinja2.

        Returns:
            Dictionary with all variables and functions for template rendering.
        """
        return {
            # Date variables
            "execution_date": self.ds,
            "ds": self.ds,
            "ds_nodash": self.ds_nodash,
            "yesterday_ds": self.yesterday_ds,
            "tomorrow_ds": self.tomorrow_ds,
            "week_start_ds": self.week_start_ds,
            "month_start_ds": self.month_start_ds,
            "year": self.year,
            "month": self.month,
            "day": self.day,
            # Functions
            "var": self.var,
            "env_var": self.env_var,
            "date_add": self.date_add,
            "date_sub": self.date_sub,
            "ref": self.ref,
            "source": self.source,
        }


class SafeJinjaEnvironment:
    """Restricted Jinja2 environment that blocks dangerous features.

    Creates a sandboxed Jinja2 environment that:
    - Blocks access to dangerous Python attributes (__class__, __globals__, etc.)
    - Removes all global functions
    - Uses strict undefined handling

    This prevents template injection attacks while still allowing
    useful SQL templating features.

    Example:
        >>> env = SafeJinjaEnvironment.create_environment()
        >>> template = env.from_string("SELECT * FROM {{ table }}")
        >>> template.render(table="users")
        'SELECT * FROM users'
    """

    # Attributes that could be used for sandbox escape
    BLOCKED_ATTRIBUTES: frozenset[str] = frozenset({
        "__class__",
        "__mro__",
        "__subclasses__",
        "__globals__",
        "__builtins__",
        "__import__",
        "__code__",
        "__func__",
        "__self__",
        "__dict__",
        "__init__",
        "__new__",
        "__reduce__",
        "__reduce_ex__",
        "__getattribute__",
        "__setattr__",
        "__delattr__",
        "eval",
        "exec",
        "compile",
        "open",
        "file",
        "input",
        "raw_input",
        "execfile",
        "reload",
        "__loader__",
        "__spec__",
    })

    # Callable names that should be blocked
    BLOCKED_CALLABLES: frozenset[str] = frozenset({
        "eval",
        "exec",
        "compile",
        "open",
        "input",
        "getattr",
        "setattr",
        "delattr",
        "hasattr",
        "vars",
        "dir",
        "globals",
        "locals",
        "type",
        "isinstance",
        "issubclass",
        "callable",
        "__import__",
    })

    @classmethod
    def create_environment(cls) -> "Environment":
        """Create safe Jinja2 environment.

        Returns:
            A sandboxed Jinja2 Environment configured for safe SQL templating.

        Raises:
            ImportError: If jinja2 is not installed.
        """
        from jinja2 import StrictUndefined
        from jinja2.sandbox import SandboxedEnvironment

        env = SandboxedEnvironment(
            autoescape=False,  # SQL doesn't need HTML escaping
            undefined=StrictUndefined,
            trim_blocks=True,
            lstrip_blocks=True,
        )

        # Clear all global functions to prevent access to Python internals
        env.globals.clear()

        # Register safe filters
        cls._register_safe_filters(env)

        return env

    @classmethod
    def _register_safe_filters(cls, env: "Environment") -> None:
        """Register safe Jinja2 filters for SQL rendering.

        Args:
            env: Jinja2 environment to register filters on.
        """
        # String manipulation (safe)
        env.filters["upper"] = str.upper
        env.filters["lower"] = str.lower
        env.filters["strip"] = str.strip
        env.filters["replace"] = lambda s, old, new: str(s).replace(old, new)

        # SQL-specific filters (using shared module-level functions)
        env.filters["sql_string"] = sql_string_escape
        env.filters["sql_list"] = sql_list_escape
        env.filters["sql_identifier"] = sql_identifier_escape

    @classmethod
    def is_safe_attribute(cls, name: str) -> bool:
        """Check if an attribute name is safe to access.

        Args:
            name: Attribute name to check.

        Returns:
            True if the attribute is safe to access.
        """
        return name not in cls.BLOCKED_ATTRIBUTES

    @classmethod
    def is_safe_callable(cls, name: str) -> bool:
        """Check if a callable name is safe to call.

        Args:
            name: Callable name to check.

        Returns:
            True if the callable is safe to use.
        """
        return name not in cls.BLOCKED_CALLABLES


class SafeTemplateRenderer:
    """Safe SQL template renderer combining TemplateContext and SafeJinjaEnvironment.

    Provides a unified interface for rendering SQL templates with:
    - Predefined execution context (dates, variables)
    - Safe Jinja2 environment
    - SQL-specific filters

    Example:
        >>> renderer = SafeTemplateRenderer(
        ...     variables={"schema": "prod"},
        ...     refs={"users": "prod.analytics.users"}
        ... )
        >>> sql = renderer.render(
        ...     "SELECT * FROM {{ ref('users') }} WHERE dt = '{{ ds }}'",
        ...     execution_date=date(2025, 1, 15)
        ... )
        >>> print(sql)
        SELECT * FROM prod.analytics.users WHERE dt = '2025-01-15'
    """

    def __init__(
        self,
        variables: dict[str, Any] | None = None,
        refs: dict[str, str] | None = None,
    ):
        """Initialize the safe template renderer.

        Args:
            variables: Project variables accessible via var().
            refs: Dataset references accessible via ref().
        """
        self._env = SafeJinjaEnvironment.create_environment()
        self._global_variables = variables or {}
        self._global_refs = refs or {}

    def render(
        self,
        template_str: str,
        execution_date: date | None = None,
        extra_params: dict[str, Any] | None = None,
        extra_refs: dict[str, str] | None = None,
    ) -> str:
        """Render a SQL template with safe context.

        Args:
            template_str: SQL template string with Jinja2 placeholders.
            execution_date: Execution date for template context.
            extra_params: Additional parameters to merge with context.
            extra_refs: Additional dataset references to merge.

        Returns:
            Rendered SQL string.

        Raises:
            jinja2.exceptions.TemplateError: If template rendering fails.
            jinja2.exceptions.UndefinedError: If undefined variable is used.
        """
        # Merge refs
        all_refs = {**self._global_refs, **(extra_refs or {})}

        # Create context
        context = TemplateContext(
            execution_date=execution_date,
            variables=self._global_variables,
            refs=all_refs,
        )

        # Merge context with extra params
        render_context = context.to_dict()
        if extra_params:
            render_context.update(extra_params)

        # Render template
        template = self._env.from_string(template_str)
        return template.render(**render_context)

    def render_with_context(
        self,
        template_str: str,
        context: TemplateContext,
        extra_params: dict[str, Any] | None = None,
    ) -> str:
        """Render a SQL template with a pre-configured context.

        Args:
            template_str: SQL template string with Jinja2 placeholders.
            context: Pre-configured TemplateContext.
            extra_params: Additional parameters to merge.

        Returns:
            Rendered SQL string.
        """
        render_context = context.to_dict()
        if extra_params:
            render_context.update(extra_params)

        template = self._env.from_string(template_str)
        return template.render(**render_context)
