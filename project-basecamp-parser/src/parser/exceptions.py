"""Custom exceptions for the SQL parser service."""

from __future__ import annotations


class SQLParseError(Exception):
    """Raised when SQL parsing fails."""

    def __init__(self, message: str, sql: str | None = None) -> None:
        """Initialize the exception.

        Args:
            message: The error message
            sql: The SQL that failed to parse (optional)
        """
        super().__init__(message)
        self.message = message
        self.sql = sql

    def __str__(self) -> str:
        """Return string representation."""
        if self.sql:
            return f"{self.message} (SQL: {self.sql[:50]}...)"
        return self.message


class SQLValidationError(Exception):
    """Raised when SQL validation fails."""

    def __init__(self, message: str, sql: str | None = None) -> None:
        """Initialize the exception.

        Args:
            message: The error message
            sql: The SQL that failed validation (optional)
        """
        super().__init__(message)
        self.message = message
        self.sql = sql


class ConfigurationError(Exception):
    """Raised when there's a configuration issue."""

    pass