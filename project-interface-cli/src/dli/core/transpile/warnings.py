"""
SQL pattern analysis and warning detection.

This module analyzes SQL for common patterns that may indicate
potential issues or optimization opportunities. Warnings are
advisory only and do not prevent SQL execution.
"""

from __future__ import annotations

import sqlglot
from sqlglot import exp
from sqlglot.errors import ParseError

from dli.core.transpile.models import (
    DIALECT_MAP,
    Dialect,
    TranspileWarning,
    WarningType,
)

__all__ = [
    "detect_warnings",
]

# Warning messages
_WARNING_MESSAGES: dict[WarningType, str] = {
    WarningType.NO_LIMIT: "No LIMIT clause detected. Consider adding LIMIT for safety.",
    WarningType.SELECT_STAR: "Consider specifying columns instead of SELECT *",
    WarningType.DUPLICATE_CTE: "Duplicate CTE name detected. Consider consolidating.",
    WarningType.CORRELATED_SUBQUERY: "Correlated subquery detected. Consider using JOIN.",
    WarningType.DANGEROUS_STATEMENT: "Potentially dangerous statement detected.",
}


def detect_warnings(
    sql: str,
    dialect: Dialect = Dialect.TRINO,
) -> list[TranspileWarning]:
    """Detect warning patterns in SQL.

    Analyzes the SQL for common anti-patterns and potential issues:
    - SELECT * usage
    - Missing LIMIT clause
    - Dangerous statements (DROP, TRUNCATE, DELETE)

    Args:
        sql: SQL string to analyze.
        dialect: SQL dialect for parsing.

    Returns:
        List of TranspileWarning objects for detected issues.
        Returns empty list if SQL cannot be parsed (fails silently).

    Example:
        >>> sql = "SELECT * FROM users"
        >>> warnings = detect_warnings(sql)
        >>> len(warnings)
        2  # SELECT_STAR and NO_LIMIT
    """
    if not sql.strip():
        return []

    sqlglot_dialect = DIALECT_MAP.get(dialect, "trino")

    try:
        parsed = sqlglot.parse_one(sql, dialect=sqlglot_dialect)
    except ParseError:
        # Fail silently for warnings - let the main transpile handle parse errors
        return []

    warnings: list[TranspileWarning] = []

    # Check for SELECT *
    warnings.extend(_detect_select_star(parsed))

    # Check for missing LIMIT
    warnings.extend(_detect_no_limit(parsed))

    # Check for dangerous statements
    warnings.extend(_detect_dangerous_statements(parsed))

    return warnings


def _detect_select_star(parsed: exp.Expression) -> list[TranspileWarning]:
    """Detect SELECT * usage.

    Args:
        parsed: Parsed SQL expression.

    Returns:
        List of warnings for SELECT * usage.
    """
    warnings: list[TranspileWarning] = []

    for _star in parsed.find_all(exp.Star):
        warnings.append(
            TranspileWarning(
                type=WarningType.SELECT_STAR,
                message=_WARNING_MESSAGES[WarningType.SELECT_STAR],
            )
        )
        # Only warn once per query
        break

    return warnings


def _detect_no_limit(parsed: exp.Expression) -> list[TranspileWarning]:
    """Detect missing LIMIT clause.

    Only warns for SELECT statements that don't have a LIMIT.
    Ignores INSERT/UPDATE/DELETE and CTEs.

    Args:
        parsed: Parsed SQL expression.

    Returns:
        List of warnings for missing LIMIT.
    """
    warnings: list[TranspileWarning] = []

    # Only check top-level SELECT statements
    if isinstance(parsed, exp.Select):
        # Check if there's a LIMIT clause
        has_limit = parsed.find(exp.Limit) is not None

        if not has_limit:
            # Also check for subqueries with LIMIT (e.g., UNION with LIMIT)
            parent = parsed.parent
            while parent:
                if isinstance(parent, exp.Limit):
                    has_limit = True
                    break
                parent = parent.parent

            if not has_limit:
                warnings.append(
                    TranspileWarning(
                        type=WarningType.NO_LIMIT,
                        message=_WARNING_MESSAGES[WarningType.NO_LIMIT],
                    )
                )

    return warnings


def _detect_dangerous_statements(parsed: exp.Expression) -> list[TranspileWarning]:
    """Detect potentially dangerous statements.

    Checks for DROP, TRUNCATE, and DELETE statements.
    These are advisory warnings only - execution is not blocked.

    Args:
        parsed: Parsed SQL expression.

    Returns:
        List of warnings for dangerous statements.
    """
    warnings: list[TranspileWarning] = []

    dangerous_types = (exp.Drop, exp.Delete)

    for stmt in parsed.find_all(*dangerous_types):
        stmt_type = type(stmt).__name__.upper()
        warnings.append(
            TranspileWarning(
                type=WarningType.DANGEROUS_STATEMENT,
                message=f"{_WARNING_MESSAGES[WarningType.DANGEROUS_STATEMENT]} ({stmt_type})",
            )
        )

    # Check for TRUNCATE separately as it might be represented differently
    # In some dialects, TRUNCATE is a separate statement type
    if hasattr(exp, "TruncateTable"):
        for _stmt in parsed.find_all(exp.TruncateTable):
            warnings.append(
                TranspileWarning(
                    type=WarningType.DANGEROUS_STATEMENT,
                    message=f"{_WARNING_MESSAGES[WarningType.DANGEROUS_STATEMENT]} (TRUNCATE)",
                )
            )

    return warnings
