"""Custom exceptions for the DataOps CLI."""

from __future__ import annotations


class DataOpsCLIError(Exception):
    """Base exception for DataOps CLI errors."""

    def __init__(self, message: str, exit_code: int = 1) -> None:
        """Initialize the exception.

        Args:
            message: The error message
            exit_code: Exit code for the CLI (default: 1)
        """
        super().__init__(message)
        self.message = message
        self.exit_code = exit_code


class APIConnectionError(DataOpsCLIError):
    """Raised when API connection fails."""

    def __init__(self, message: str, url: str | None = None) -> None:
        """Initialize the exception.

        Args:
            message: The error message
            url: The URL that failed (optional)
        """
        super().__init__(message, exit_code=3)
        self.url = url


class ConfigurationError(DataOpsCLIError):
    """Raised when there's a configuration issue."""

    def __init__(self, message: str) -> None:
        """Initialize the exception.

        Args:
            message: The error message
        """
        super().__init__(message, exit_code=2)


class SQLProcessingError(DataOpsCLIError):
    """Raised when SQL processing fails."""

    def __init__(self, message: str, sql: str | None = None) -> None:
        """Initialize the exception.

        Args:
            message: The error message
            sql: The SQL that caused the error (optional)
        """
        super().__init__(message, exit_code=4)
        self.sql = sql


class ValidationError(DataOpsCLIError):
    """Raised when input validation fails."""

    def __init__(self, message: str) -> None:
        """Initialize the exception.

        Args:
            message: The error message
        """
        super().__init__(message, exit_code=5)