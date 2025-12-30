"""
METRIC() function parsing and expansion.

This module provides regex-based parsing of METRIC(name) functions
in SQL and expansion to actual SQL expressions.

MVP Limitations:
- Only 1 METRIC() function per SQL allowed
- String literals and comments are not specially handled (Phase 2)
"""

from __future__ import annotations

from collections.abc import Callable
import re

from dli.core.transpile.models import MetricMatch

__all__ = [
    "METRIC_PATTERN",
    "expand_metrics",
    "find_metric_functions",
]


# Regex pattern for METRIC() function matching
# Supports: METRIC(name), METRIC('name'), METRIC("name"), METRIC( name )
# Case-insensitive for the function name, preserves metric name case
METRIC_PATTERN = re.compile(
    r"METRIC\s*\(\s*(?:'([^']+)'|\"([^\"]+)\"|([a-zA-Z_][a-zA-Z0-9_]*))\s*\)",
    re.IGNORECASE,
)


def find_metric_functions(sql: str) -> list[MetricMatch]:
    """Find all METRIC() functions in SQL.

    Scans the SQL string for METRIC(name) patterns and returns
    match information including positions for replacement.

    Args:
        sql: SQL string to scan.

    Returns:
        List of MetricMatch tuples, each containing:
        - full_match: The complete matched text (e.g., "METRIC(revenue)")
        - metric_name: The extracted metric name (e.g., "revenue")
        - start_pos: Start position in the SQL string
        - end_pos: End position in the SQL string

    Example:
        >>> sql = "SELECT METRIC(revenue), METRIC('users') FROM t"
        >>> matches = find_metric_functions(sql)
        >>> len(matches)
        2
        >>> matches[0].metric_name
        'revenue'
        >>> matches[1].metric_name
        'users'
    """
    matches: list[MetricMatch] = []

    for match in METRIC_PATTERN.finditer(sql):
        # Extract metric name from one of three capture groups:
        # Group 1: single-quoted name 'name'
        # Group 2: double-quoted name "name"
        # Group 3: unquoted identifier name
        metric_name = match.group(1) or match.group(2) or match.group(3)

        if metric_name:  # Defensive check
            matches.append(
                MetricMatch(
                    full_match=match.group(0),
                    metric_name=metric_name,
                    start_pos=match.start(),
                    end_pos=match.end(),
                )
            )

    return matches


def expand_metrics(
    sql: str,
    metric_resolver: Callable[[str], str | None],
    *,
    max_metrics: int = 1,
) -> tuple[str, list[str]]:
    """Expand METRIC() functions to SQL expressions.

    Replaces METRIC(name) occurrences with actual SQL expressions
    using the provided resolver function.

    Args:
        sql: SQL string containing METRIC() functions.
        metric_resolver: Function that takes a metric name and returns
            the SQL expression, or None if not found.
        max_metrics: Maximum number of METRIC() functions allowed.
            Default is 1 for MVP. Set to 0 for unlimited.

    Returns:
        Tuple of (expanded_sql, errors):
        - expanded_sql: SQL with METRIC() replaced (or original on error)
        - errors: List of error messages (empty if successful)

    Example:
        >>> def resolver(name: str) -> str | None:
        ...     return "SUM(amount)" if name == "revenue" else None
        >>> sql = "SELECT METRIC(revenue) FROM orders"
        >>> expanded, errors = expand_metrics(sql, resolver)
        >>> expanded
        'SELECT SUM(amount) FROM orders'
        >>> errors
        []
    """
    matches = find_metric_functions(sql)
    errors: list[str] = []

    if not matches:
        return sql, errors

    # MVP limitation check
    if max_metrics > 0 and len(matches) > max_metrics:
        metric_list = ", ".join(m.full_match for m in matches)
        errors.append(
            f"MVP limitation: Only {max_metrics} METRIC() per SQL allowed, "
            f"found {len(matches)}: {metric_list}"
        )
        return sql, errors

    # Process matches in reverse order to preserve positions
    result = sql
    for match in reversed(matches):
        expression = metric_resolver(match.metric_name)
        if expression is None:
            errors.append(f"Metric '{match.metric_name}' not found")
            continue

        # Replace METRIC(name) with the expression
        # Wrap in parentheses to preserve operator precedence
        wrapped_expression = f"({expression})"
        result = (
            result[: match.start_pos] + wrapped_expression + result[match.end_pos :]
        )

    return result, errors
