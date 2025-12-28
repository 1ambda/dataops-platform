"""SQL escaping filters for safe template rendering.

This module provides SQL-safe escaping functions for use in Jinja2 templates:
- sql_string_escape: Escape strings for SQL with single quotes
- sql_list_escape: Format lists for SQL IN clauses
- sql_identifier_escape: Quote SQL identifiers with double quotes

These filters are used by both SafeJinjaEnvironment (templates.py)
and SQLRenderer (renderer.py) for consistent SQL escaping.
"""

from __future__ import annotations

from typing import Any


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
