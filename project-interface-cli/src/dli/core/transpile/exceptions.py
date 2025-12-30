"""
Exception hierarchy for SQL transpile operations.

This module defines a minimal exception hierarchy for transpile errors.
Following MVP principles, we keep only 4 essential exception classes.
"""

from __future__ import annotations

from typing import Any

# Constants
_MAX_SUGGESTIONS = 5
_MAX_SQL_PREVIEW_LENGTH = 100

__all__ = [
    "MetricNotFoundError",
    "RuleFetchError",
    "SqlParseError",
    "TranspileError",
]


class TranspileError(Exception):
    """Base exception for all transpile-related errors.

    This is the root exception class for the transpile module.
    It can also be used directly for general errors including
    network failures and timeouts.

    Attributes:
        message: Human-readable error description.
        details: Additional structured information about the error.
    """

    def __init__(self, message: str, details: dict[str, Any] | None = None):
        self.message = message
        self.details = details or {}
        super().__init__(message)

    def __str__(self) -> str:
        return self.message


class RuleFetchError(TranspileError):
    """Failed to fetch transpile rules from server.

    Raised when the server returns an error response (4xx/5xx)
    or when rules cannot be parsed correctly.

    Attributes:
        status_code: HTTP status code from server (if available).
        detail: Additional error details from server response.
    """

    def __init__(
        self,
        message: str = "Failed to fetch transpile rules",
        status_code: int | None = None,
        detail: str | None = None,
    ):
        full_message = message
        if status_code:
            full_message += f" (HTTP {status_code})"
        if detail:
            full_message += f": {detail}"

        super().__init__(
            full_message,
            {"status_code": status_code, "detail": detail},
        )
        self.status_code = status_code
        self.detail = detail


class MetricNotFoundError(TranspileError):
    """Metric not found in server registry.

    Raised when METRIC(name) references a metric that doesn't exist.
    Provides suggestions for similar metric names when available.

    Attributes:
        metric_name: The metric name that was not found.
        available_metrics: List of available metric names for suggestions.
    """

    def __init__(
        self,
        metric_name: str,
        available_metrics: list[str] | None = None,
    ):
        message = f"Metric '{metric_name}' not found"
        if available_metrics:
            # Show up to MAX_SUGGESTIONS suggestions
            suggestions = available_metrics[:_MAX_SUGGESTIONS]
            message += f". Available: {', '.join(suggestions)}"
            if len(available_metrics) > _MAX_SUGGESTIONS:
                message += f" (+{len(available_metrics) - _MAX_SUGGESTIONS} more)"
            message += "\nHint: Use `dli metric list` to see all available metrics."

        super().__init__(
            message,
            {"metric_name": metric_name, "available": available_metrics},
        )
        self.metric_name = metric_name
        self.available_metrics = available_metrics or []


class SqlParseError(TranspileError):
    """SQL parsing failed.

    Raised when SQLGlot cannot parse the input SQL.
    Includes position information when available.

    Attributes:
        sql: The SQL string that failed to parse (truncated if too long).
        detail: Specific parse error description.
        line: Line number where error occurred (1-based, if known).
        column: Column number where error occurred (1-based, if known).
    """

    def __init__(
        self,
        sql: str,
        detail: str,
        line: int | None = None,
        column: int | None = None,
    ):
        message = f"Failed to parse SQL: {detail}"
        if line is not None:
            message += f" (line {line}"
            if column is not None:
                message += f", column {column}"
            message += ")"

        # Truncate SQL for error message
        truncated_sql = (
            sql[:_MAX_SQL_PREVIEW_LENGTH] + "..."
            if len(sql) > _MAX_SQL_PREVIEW_LENGTH
            else sql
        )

        super().__init__(
            message,
            {
                "sql": truncated_sql,
                "detail": detail,
                "line": line,
                "column": column,
            },
        )
        self.sql = sql
        self.detail = detail
        self.line = line
        self.column = column
