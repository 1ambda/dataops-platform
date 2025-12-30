"""DLI Library Exception Hierarchy.

This module provides a structured exception hierarchy for the DLI library.
All exceptions include error codes for programmatic handling and detailed
context information for debugging.

Example:
    >>> from dli.exceptions import DatasetNotFoundError, ErrorCode
    >>> try:
    ...     api.get("missing_dataset")
    ... except DatasetNotFoundError as e:
    ...     print(f"Error {e.code.value}: {e}")
    ...     print(f"Searched paths: {e.searched_paths}")
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any


class ErrorCode(str, Enum):
    """Error codes for programmatic error handling.

    Error codes are organized by category:
    - DLI-0xx: Configuration errors
    - DLI-1xx: Not found errors
    - DLI-2xx: Validation errors
    - DLI-3xx: Transpile errors
    - DLI-4xx: Execution errors
    - DLI-5xx: Server errors
    """

    # Configuration Errors (DLI-0xx)
    CONFIG_INVALID = "DLI-001"
    CONFIG_NOT_FOUND = "DLI-002"
    PROJECT_NOT_FOUND = "DLI-003"

    # Not Found Errors (DLI-1xx)
    DATASET_NOT_FOUND = "DLI-101"
    METRIC_NOT_FOUND = "DLI-102"
    TABLE_NOT_FOUND = "DLI-103"
    WORKFLOW_NOT_FOUND = "DLI-104"

    # Validation Errors (DLI-2xx)
    VALIDATION_FAILED = "DLI-201"
    SQL_SYNTAX_ERROR = "DLI-202"
    SPEC_INVALID = "DLI-203"
    PARAMETER_INVALID = "DLI-204"

    # Transpile Errors (DLI-3xx)
    TRANSPILE_FAILED = "DLI-301"
    DIALECT_UNSUPPORTED = "DLI-302"
    RULE_CONFLICT = "DLI-303"

    # Execution Errors (DLI-4xx)
    EXECUTION_FAILED = "DLI-401"
    TIMEOUT = "DLI-402"
    CONNECTION_FAILED = "DLI-403"

    # Server Errors (DLI-5xx)
    SERVER_UNREACHABLE = "DLI-501"
    SERVER_AUTH_FAILED = "DLI-502"
    SERVER_ERROR = "DLI-503"


@dataclass
class DLIError(Exception):
    """Base exception for all DLI library errors.

    All DLI exceptions include an error code and optional detailed information.
    This enables both human-readable error messages and programmatic handling.

    Attributes:
        message: Human-readable error description.
        code: ErrorCode enum value for programmatic handling.
        details: Additional context as key-value pairs.

    Example:
        >>> try:
        ...     raise DLIError("Something went wrong", ErrorCode.EXECUTION_FAILED)
        ... except DLIError as e:
        ...     print(f"[{e.code.value}] {e.message}")
        [DLI-401] Something went wrong
    """

    message: str
    code: ErrorCode = ErrorCode.EXECUTION_FAILED
    details: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        """Initialize Exception.args for proper exception handling."""
        # Set args for proper exception serialization and traceback display
        super().__init__(str(self))

    def __str__(self) -> str:
        """Return formatted error message with code."""
        return f"[{self.code.value}] {self.message}"

    def __repr__(self) -> str:
        """Return detailed representation."""
        return f"{self.__class__.__name__}(message={self.message!r}, code={self.code})"


@dataclass
class ConfigurationError(DLIError):
    """Configuration-related errors.

    Raised when configuration is missing, invalid, or cannot be loaded.

    Attributes:
        config_path: Path to the configuration file (if applicable).
    """

    code: ErrorCode = ErrorCode.CONFIG_INVALID
    config_path: Path | None = None

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.config_path:
            return f"[{self.code.value}] {self.message} (path: {self.config_path})"
        return f"[{self.code.value}] {self.message}"


@dataclass
class DLIValidationError(DLIError):
    """Validation failure error.

    Raised when spec or SQL validation fails. Named with DLI prefix to avoid
    conflict with pydantic.ValidationError.

    Attributes:
        errors: List of validation error messages.
        warnings: List of validation warning messages.
    """

    code: ErrorCode = ErrorCode.VALIDATION_FAILED
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def __str__(self) -> str:
        """Return formatted error message with error count."""
        error_count = len(self.errors)
        warning_count = len(self.warnings)
        base = f"[{self.code.value}] {self.message}"
        if error_count or warning_count:
            base += f" ({error_count} errors, {warning_count} warnings)"
        return base


@dataclass
class ExecutionError(DLIError):
    """Execution failure error.

    Raised when dataset/metric execution fails. Preserves the original
    exception as cause for debugging.

    Attributes:
        cause: Original exception that caused this error.
    """

    code: ErrorCode = ErrorCode.EXECUTION_FAILED
    cause: Exception | None = None

    def __post_init__(self) -> None:
        """Chain the cause exception and initialize base class."""
        super().__post_init__()
        if self.cause:
            self.__cause__ = self.cause


@dataclass
class DatasetNotFoundError(DLIError):
    """Dataset not found error.

    Raised when a dataset cannot be found by name.

    Attributes:
        name: The dataset name that was not found.
        searched_paths: Paths that were searched.
    """

    code: ErrorCode = ErrorCode.DATASET_NOT_FOUND
    name: str = ""
    searched_paths: list[Path] = field(default_factory=list)

    def __str__(self) -> str:
        """Return formatted error message with search paths."""
        paths = (
            ", ".join(str(p) for p in self.searched_paths)
            if self.searched_paths
            else "N/A"
        )
        return f"[{self.code.value}] Dataset '{self.name}' not found. Searched: {paths}"


@dataclass
class MetricNotFoundError(DLIError):
    """Metric not found error.

    Raised when a metric cannot be found by name.

    Attributes:
        name: The metric name that was not found.
        searched_paths: Paths that were searched.
    """

    code: ErrorCode = ErrorCode.METRIC_NOT_FOUND
    name: str = ""
    searched_paths: list[Path] = field(default_factory=list)

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.searched_paths:
            paths = ", ".join(str(p) for p in self.searched_paths)
            return (
                f"[{self.code.value}] Metric '{self.name}' not found. Searched: {paths}"
            )
        return f"[{self.code.value}] Metric '{self.name}' not found"


@dataclass
class TableNotFoundError(DLIError):
    """Table not found in catalog error.

    Attributes:
        table_ref: The table reference that was not found.
    """

    code: ErrorCode = ErrorCode.TABLE_NOT_FOUND
    table_ref: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        return f"[{self.code.value}] Table '{self.table_ref}' not found in catalog"


@dataclass
class TranspileError(DLIError):
    """SQL transpilation error.

    Raised when SQL transpilation fails due to syntax errors or
    unsupported features.

    Attributes:
        sql: The SQL that failed to transpile.
        line: Line number where error occurred (if available).
        column: Column number where error occurred (if available).
    """

    code: ErrorCode = ErrorCode.TRANSPILE_FAILED
    sql: str = ""
    line: int | None = None
    column: int | None = None

    def __str__(self) -> str:
        """Return formatted error message with location."""
        base = f"[{self.code.value}] {self.message}"
        if self.line is not None:
            base += f" at line {self.line}"
            if self.column is not None:
                base += f", column {self.column}"
        return base


@dataclass
class ServerError(DLIError):
    """Server communication error.

    Raised when communication with Basecamp server fails.

    Attributes:
        status_code: HTTP status code (if applicable).
        url: URL that was being accessed.
    """

    code: ErrorCode = ErrorCode.SERVER_ERROR
    status_code: int | None = None
    url: str | None = None

    def __str__(self) -> str:
        """Return formatted error message with status."""
        base = f"[{self.code.value}] {self.message}"
        if self.status_code:
            base += f" (HTTP {self.status_code})"
        if self.url:
            base += f" - {self.url}"
        return base


@dataclass
class WorkflowNotFoundError(DLIError):
    """Workflow not found error.

    Attributes:
        dataset_name: The dataset name for which workflow was not found.
    """

    code: ErrorCode = ErrorCode.WORKFLOW_NOT_FOUND
    dataset_name: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        return (
            f"[{self.code.value}] Workflow for dataset '{self.dataset_name}' not found"
        )


__all__ = [
    "ConfigurationError",
    "DLIError",
    "DLIValidationError",
    "DatasetNotFoundError",
    "ErrorCode",
    "ExecutionError",
    "MetricNotFoundError",
    "ServerError",
    "TableNotFoundError",
    "TranspileError",
    "WorkflowNotFoundError",
]
