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
    - DLI-6xx: Quality errors
    - DLI-7xx: Catalog errors
    """

    # Configuration Errors (DLI-0xx)
    CONFIG_INVALID = "DLI-001"
    CONFIG_NOT_FOUND = "DLI-002"
    PROJECT_NOT_FOUND = "DLI-003"

    # Not Found Errors (DLI-1xx)
    DATASET_NOT_FOUND = "DLI-101"
    METRIC_NOT_FOUND = "DLI-102"
    TABLE_NOT_FOUND = "DLI-103"
    # Note: WORKFLOW_NOT_FOUND moved to DLI-8xx range (DLI-800)

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
    EXECUTION_PERMISSION = "DLI-404"  # No execution permission
    EXECUTION_QUERY = "DLI-405"  # SQL execution error

    # Server Errors (DLI-5xx)
    SERVER_UNREACHABLE = "DLI-501"
    SERVER_AUTH_FAILED = "DLI-502"
    SERVER_ERROR = "DLI-503"
    SERVER_EXECUTION = "DLI-504"  # Server-side execution error

    # Quality Errors (DLI-6xx)
    QUALITY_SPEC_NOT_FOUND = "DLI-601"
    QUALITY_SPEC_PARSE = "DLI-602"
    QUALITY_TARGET_NOT_FOUND = "DLI-603"
    QUALITY_TEST_EXECUTION = "DLI-604"
    QUALITY_NOT_FOUND = "DLI-606"

    # Catalog Errors (DLI-7xx)
    CATALOG_CONNECTION_ERROR = "DLI-701"
    CATALOG_TABLE_NOT_FOUND = "DLI-702"
    CATALOG_INVALID_IDENTIFIER = "DLI-703"
    CATALOG_ACCESS_DENIED = "DLI-704"
    CATALOG_ENGINE_NOT_SUPPORTED = "DLI-705"

    # Workflow Errors (DLI-8xx)
    WORKFLOW_NOT_FOUND = "DLI-800"
    WORKFLOW_REGISTRATION_FAILED = "DLI-801"
    WORKFLOW_EXECUTION_FAILED = "DLI-802"
    WORKFLOW_PERMISSION_DENIED = "DLI-803"
    WORKFLOW_ALREADY_EXISTS = "DLI-804"
    WORKFLOW_INVALID_CRON = "DLI-805"
    WORKFLOW_OVERRIDDEN = "DLI-806"
    WORKFLOW_INVALID_STATE = "DLI-807"
    WORKFLOW_UNREGISTER_FAILED = "DLI-808"


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
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return (
            f"[{self.code.value}] Workflow for dataset '{self.dataset_name}' not found"
        )


@dataclass
class WorkflowRegistrationError(DLIError):
    """Error during workflow registration.

    Attributes:
        dataset_name: The dataset name that failed to register.
    """

    code: ErrorCode = ErrorCode.WORKFLOW_REGISTRATION_FAILED
    dataset_name: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return (
            f"[{self.code.value}] Failed to register workflow for '{self.dataset_name}'"
        )


@dataclass
class WorkflowExecutionError(DLIError):
    """Error during workflow execution.

    Attributes:
        dataset_name: The dataset name that failed to execute.
        run_id: The run ID if available.
    """

    code: ErrorCode = ErrorCode.WORKFLOW_EXECUTION_FAILED
    dataset_name: str = ""
    run_id: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return (
            f"[{self.code.value}] Workflow execution failed for '{self.dataset_name}'"
        )


@dataclass
class WorkflowPermissionError(DLIError):
    """Permission denied for workflow operation.

    Attributes:
        dataset_name: The dataset name for which permission was denied.
    """

    code: ErrorCode = ErrorCode.WORKFLOW_PERMISSION_DENIED
    dataset_name: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return (
            f"[{self.code.value}] Permission denied for workflow '{self.dataset_name}'"
        )


# Quality Errors (DLI-6xx)


@dataclass
class QualitySpecNotFoundError(DLIError):
    """Quality Spec file not found error.

    Raised when a Quality Spec YML file cannot be found.

    Attributes:
        spec_path: Path to the Quality Spec file that was not found.
    """

    code: ErrorCode = ErrorCode.QUALITY_SPEC_NOT_FOUND
    spec_path: Path | str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        return f"[{self.code.value}] Quality Spec not found: {self.spec_path}"


@dataclass
class QualitySpecParseError(DLIError):
    """Quality Spec parsing error.

    Raised when a Quality Spec YML file cannot be parsed.

    Attributes:
        spec_path: Path to the Quality Spec file.
        line: Line number where error occurred (if available).
        column: Column number where error occurred (if available).
    """

    code: ErrorCode = ErrorCode.QUALITY_SPEC_PARSE
    spec_path: Path | str = ""
    line: int | None = None
    column: int | None = None

    def __str__(self) -> str:
        """Return formatted error message with location."""
        base = f"[{self.code.value}] {self.message}"
        if self.spec_path:
            base += f" in {self.spec_path}"
        if self.line is not None:
            base += f" at line {self.line}"
            if self.column is not None:
                base += f", column {self.column}"
        return base


@dataclass
class QualityTargetNotFoundError(DLIError):
    """Quality target (Dataset/Metric) not found error.

    Raised when the target Dataset or Metric referenced in a Quality Spec
    cannot be found.

    Attributes:
        target_urn: The URN of the target that was not found.
    """

    code: ErrorCode = ErrorCode.QUALITY_TARGET_NOT_FOUND
    target_urn: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        return f"[{self.code.value}] Target not found: {self.target_urn}"


@dataclass
class QualityNotFoundError(DLIError):
    """Quality not found on server error.

    Raised when a Quality registered on the server cannot be found.

    Attributes:
        name: The Quality name that was not found.
    """

    code: ErrorCode = ErrorCode.QUALITY_NOT_FOUND
    name: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        return f"[{self.code.value}] Quality '{self.name}' not found"


@dataclass
class QualityTestExecutionError(DLIError):
    """Quality test execution error.

    Raised when a quality test fails to execute (not when it fails validation).

    Attributes:
        test_name: Name of the test that failed to execute.
        target_urn: URN of the target being tested.
        cause: Original exception that caused the failure.
    """

    code: ErrorCode = ErrorCode.QUALITY_TEST_EXECUTION
    test_name: str = ""
    target_urn: str = ""
    cause: Exception | None = None

    def __post_init__(self) -> None:
        """Chain the cause exception and initialize base class."""
        super().__post_init__()
        if self.cause:
            self.__cause__ = self.cause

    def __str__(self) -> str:
        """Return formatted error message."""
        base = f"[{self.code.value}] Test '{self.test_name}' execution failed"
        if self.target_urn:
            base += f" for {self.target_urn}"
        if self.cause:
            base += f": {self.cause}"
        return base


# Catalog Errors (DLI-7xx)


@dataclass
class CatalogError(DLIError):
    """Base catalog error.

    Raised for catalog-related operations.
    """

    code: ErrorCode = ErrorCode.CATALOG_CONNECTION_ERROR


@dataclass
class CatalogTableNotFoundError(DLIError):
    """Catalog table not found error.

    Raised when a table cannot be found in the catalog.

    Attributes:
        table_ref: The table reference that was not found.
    """

    code: ErrorCode = ErrorCode.CATALOG_TABLE_NOT_FOUND
    table_ref: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        return f"[{self.code.value}] Table '{self.table_ref}' not found in catalog"


@dataclass
class InvalidIdentifierError(DLIError):
    """Invalid identifier format error.

    Raised when a table identifier has an invalid format.

    Attributes:
        identifier: The invalid identifier string.
    """

    code: ErrorCode = ErrorCode.CATALOG_INVALID_IDENTIFIER
    identifier: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        return f"[{self.code.value}] Invalid identifier format: '{self.identifier}'"


@dataclass
class UnsupportedEngineError(DLIError):
    """Unsupported engine error.

    Raised when an unsupported query engine is specified.

    Attributes:
        engine: The unsupported engine name.
        supported: List of supported engine names.
    """

    code: ErrorCode = ErrorCode.CATALOG_ENGINE_NOT_SUPPORTED
    engine: str = ""
    supported: list[str] = field(default_factory=list)

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.supported:
            supported_str = ", ".join(self.supported)
            return f"[{self.code.value}] Engine '{self.engine}' not supported. Supported: {supported_str}"
        return f"[{self.code.value}] Engine '{self.engine}' not supported"


__all__ = [
    # Catalog Errors
    "CatalogError",
    "CatalogTableNotFoundError",
    "ConfigurationError",
    "DLIError",
    "DLIValidationError",
    "DatasetNotFoundError",
    "ErrorCode",
    "ExecutionError",
    "InvalidIdentifierError",
    "MetricNotFoundError",
    # Quality Errors
    "QualityNotFoundError",
    "QualitySpecNotFoundError",
    "QualitySpecParseError",
    "QualityTargetNotFoundError",
    "QualityTestExecutionError",
    "ServerError",
    "TableNotFoundError",
    "TranspileError",
    "UnsupportedEngineError",
    # Workflow Errors
    "WorkflowExecutionError",
    "WorkflowNotFoundError",
    "WorkflowPermissionError",
    "WorkflowRegistrationError",
]
