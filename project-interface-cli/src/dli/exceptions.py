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
    - DLI-8xx: Workflow errors
    - DLI-9xx: Lineage errors
    """

    # Configuration Errors (DLI-0xx)
    CONFIG_INVALID = "DLI-001"
    CONFIG_NOT_FOUND = "DLI-002"
    PROJECT_NOT_FOUND = "DLI-003"
    CONFIG_ENV_NOT_FOUND = "DLI-004"
    CONFIG_TEMPLATE_ERROR = "DLI-005"
    CONFIG_VALIDATION_FAILED = "DLI-006"
    CONFIG_WRITE_ERROR = "DLI-007"

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

    # Run Errors (DLI-41x) - Sub-range of Execution (DLI-4xx)
    RUN_FILE_NOT_FOUND = "DLI-410"
    RUN_LOCAL_DENIED = "DLI-411"
    RUN_SERVER_UNAVAILABLE = "DLI-412"
    RUN_EXECUTION_FAILED = "DLI-413"
    RUN_OUTPUT_FAILED = "DLI-414"
    RUN_TIMEOUT = "DLI-415"
    RUN_PARAMETER_INVALID = "DLI-416"

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
    QUALITY_TEST_TIMEOUT = "DLI-605"
    QUALITY_NOT_FOUND = "DLI-606"

    # Catalog Errors (DLI-7xx)
    CATALOG_CONNECTION_ERROR = "DLI-701"
    CATALOG_TABLE_NOT_FOUND = "DLI-702"
    CATALOG_INVALID_IDENTIFIER = "DLI-703"
    CATALOG_ACCESS_DENIED = "DLI-704"
    CATALOG_ENGINE_NOT_SUPPORTED = "DLI-705"
    CATALOG_SCHEMA_TOO_LARGE = "DLI-706"

    # Query Errors (DLI-78x) - Sub-range of Catalog (DLI-7xx)
    QUERY_NOT_FOUND = "DLI-780"
    QUERY_ACCESS_DENIED = "DLI-781"
    QUERY_CANCEL_FAILED = "DLI-782"
    QUERY_INVALID_FILTER = "DLI-783"
    QUERY_SERVER_ERROR = "DLI-784"

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

    # Lineage Errors (DLI-9xx)
    LINEAGE_NOT_FOUND = "DLI-900"
    LINEAGE_DEPTH_EXCEEDED = "DLI-901"
    LINEAGE_CYCLE_DETECTED = "DLI-902"
    LINEAGE_SERVER_ERROR = "DLI-903"
    LINEAGE_TIMEOUT = "DLI-904"

    # Debug Errors (DLI-95x) - Sub-range of Lineage (DLI-9xx)
    DEBUG_SYSTEM_CHECK_FAILED = "DLI-950"
    DEBUG_CONFIG_CHECK_FAILED = "DLI-951"
    DEBUG_SERVER_CHECK_FAILED = "DLI-952"
    DEBUG_AUTH_CHECK_FAILED = "DLI-953"
    DEBUG_CONNECTION_CHECK_FAILED = "DLI-954"
    DEBUG_NETWORK_CHECK_FAILED = "DLI-955"
    DEBUG_TIMEOUT = "DLI-956"


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
class ConfigEnvNotFoundError(DLIError):
    """Named environment not found error.

    Raised when a named environment cannot be found in configuration.

    Attributes:
        env_name: The environment name that was not found.
        available: List of available environment names.
    """

    code: ErrorCode = ErrorCode.CONFIG_ENV_NOT_FOUND
    env_name: str = ""
    available: list[str] = field(default_factory=list)

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.available:
            available_str = ", ".join(self.available)
            return f"[{self.code.value}] Environment '{self.env_name}' not found. Available: {available_str}"
        return f"[{self.code.value}] Environment '{self.env_name}' not found"


@dataclass
class ConfigTemplateError(DLIError):
    """Template resolution error.

    Raised when a template variable cannot be resolved.

    Attributes:
        template: The template string that failed.
        var_name: The variable name that was missing.
    """

    code: ErrorCode = ErrorCode.CONFIG_TEMPLATE_ERROR
    template: str = ""
    var_name: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.var_name:
            return f"[{self.code.value}] Missing environment variable: {self.var_name}"
        if self.template:
            return f"[{self.code.value}] Template resolution failed: {self.template}"
        return f"[{self.code.value}] {self.message}"


@dataclass
class ConfigValidationError(DLIError):
    """Configuration validation error.

    Raised when configuration validation fails.

    Attributes:
        errors: List of validation error messages.
        warnings: List of validation warning messages.
    """

    code: ErrorCode = ErrorCode.CONFIG_VALIDATION_FAILED
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
class ConfigWriteError(DLIError):
    """Configuration write error.

    Raised when writing configuration fails.

    Attributes:
        config_path: Path to the configuration file.
    """

    code: ErrorCode = ErrorCode.CONFIG_WRITE_ERROR
    config_path: Path | None = None

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.config_path:
            return f"[{self.code.value}] Failed to write config: {self.config_path}"
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
    spec_path: Path | str | None = None

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.spec_path:
            return f"[{self.code.value}] Quality Spec not found: {self.spec_path}"
        return f"[{self.code.value}] {self.message}"


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
    spec_path: Path | str | None = None
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


@dataclass
class QualityTestTimeoutError(DLIError):
    """Quality test timeout error.

    Raised when a quality test exceeds the configured timeout duration.

    Attributes:
        test_name: Name of the test that timed out.
        target_urn: URN of the target being tested.
        timeout_seconds: The timeout duration that was exceeded.
        cause: Original exception that caused the timeout (e.g., TimeoutError).
    """

    code: ErrorCode = ErrorCode.QUALITY_TEST_TIMEOUT
    test_name: str = ""
    target_urn: str = ""
    timeout_seconds: float | None = None
    cause: Exception | None = None

    def __post_init__(self) -> None:
        """Chain the cause exception and initialize base class."""
        super().__post_init__()
        if self.cause:
            self.__cause__ = self.cause

    def __str__(self) -> str:
        """Return formatted error message."""
        base = f"[{self.code.value}] Test '{self.test_name}' timed out"
        if self.target_urn:
            base += f" for {self.target_urn}"
        if self.timeout_seconds is not None:
            base += f" after {self.timeout_seconds}s"
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


@dataclass
class CatalogAccessDeniedError(DLIError):
    """Catalog access denied error.

    Raised when access to a catalog resource is denied.

    Attributes:
        table_ref: The table reference that was denied.
        reason: Optional reason for denial.
    """

    code: ErrorCode = ErrorCode.CATALOG_ACCESS_DENIED
    table_ref: str = ""
    reason: str | None = None

    def __str__(self) -> str:
        """Return formatted error message."""
        msg = f"[{self.code.value}] Access denied for table: {self.table_ref}"
        if self.reason:
            msg += f" - {self.reason}"
        return msg


@dataclass
class CatalogSchemaError(DLIError):
    """Catalog schema too large error.

    Raised when a table schema exceeds size limits.

    Attributes:
        table_ref: The table reference with large schema.
        column_count: Number of columns in the schema.
        max_columns: Maximum allowed columns.
    """

    code: ErrorCode = ErrorCode.CATALOG_SCHEMA_TOO_LARGE
    table_ref: str = ""
    column_count: int | None = None
    max_columns: int | None = None

    def __str__(self) -> str:
        """Return formatted error message."""
        msg = f"[{self.code.value}] Schema too large for table: {self.table_ref}"
        if self.column_count and self.max_columns:
            msg += f" ({self.column_count} columns, max {self.max_columns})"
        return msg


# Query Errors (DLI-78x)


@dataclass
class QueryNotFoundError(DLIError):
    """Query not found error.

    Raised when a query cannot be found by ID.

    Attributes:
        query_id: The query ID that was not found.
    """

    code: ErrorCode = ErrorCode.QUERY_NOT_FOUND
    query_id: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return f"[{self.code.value}] Query '{self.query_id}' not found"


@dataclass
class QueryAccessDeniedError(DLIError):
    """Query access denied error.

    Raised when access to a query is denied.

    Attributes:
        query_id: The query ID that was denied.
    """

    code: ErrorCode = ErrorCode.QUERY_ACCESS_DENIED
    query_id: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return f"[{self.code.value}] Access denied to query '{self.query_id}'"


@dataclass
class QueryCancelError(DLIError):
    """Query cancellation error.

    Raised when a query cancellation fails.

    Attributes:
        query_id: The query ID that failed to cancel.
    """

    code: ErrorCode = ErrorCode.QUERY_CANCEL_FAILED
    query_id: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return f"[{self.code.value}] Failed to cancel query '{self.query_id}'"


@dataclass
class QueryInvalidFilterError(DLIError):
    """Query invalid filter error.

    Raised when query filter parameters are invalid.

    Attributes:
        filter_name: The invalid filter parameter name.
        filter_value: The invalid filter value.
    """

    code: ErrorCode = ErrorCode.QUERY_INVALID_FILTER
    filter_name: str = ""
    filter_value: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return f"[{self.code.value}] Invalid filter: {self.filter_name}={self.filter_value}"


# Lineage Errors (DLI-9xx)


@dataclass
class LineageError(DLIError):
    """Base lineage error.

    Raised for lineage-related operations.

    Attributes:
        resource_name: The resource name for which lineage was queried.
    """

    code: ErrorCode = ErrorCode.LINEAGE_SERVER_ERROR
    resource_name: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.resource_name:
            return f"[{self.code.value}] {self.message} (resource: {self.resource_name})"
        return f"[{self.code.value}] {self.message}"


@dataclass
class LineageNotFoundError(DLIError):
    """Lineage not found error.

    Raised when lineage information cannot be found for a resource.

    Attributes:
        resource_name: The resource name that was not found.
    """

    code: ErrorCode = ErrorCode.LINEAGE_NOT_FOUND
    resource_name: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return f"[{self.code.value}] Lineage not found for resource: {self.resource_name}"


@dataclass
class LineageTimeoutError(DLIError):
    """Lineage query timeout error.

    Raised when a lineage query exceeds the configured timeout.

    Attributes:
        resource_name: The resource name being queried.
        timeout_seconds: The timeout duration that was exceeded.
        cause: Original exception that caused the timeout.
    """

    code: ErrorCode = ErrorCode.LINEAGE_TIMEOUT
    resource_name: str = ""
    timeout_seconds: float | None = None
    cause: Exception | None = None

    def __post_init__(self) -> None:
        """Chain the cause exception and initialize base class."""
        super().__post_init__()
        if self.cause:
            self.__cause__ = self.cause

    def __str__(self) -> str:
        """Return formatted error message."""
        base = f"[{self.code.value}] Lineage query timed out"
        if self.resource_name:
            base += f" for resource: {self.resource_name}"
        if self.timeout_seconds is not None:
            base += f" after {self.timeout_seconds}s"
        return base


# Run Errors (DLI-41x)


@dataclass
class RunFileNotFoundError(DLIError):
    """Run SQL file not found error.

    Raised when the SQL file specified for run command cannot be found.

    Attributes:
        path: Path to the SQL file that was not found.
    """

    code: ErrorCode = ErrorCode.RUN_FILE_NOT_FOUND
    path: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.path:
            return f"[{self.code.value}] SQL file not found: {self.path}"
        return f"[{self.code.value}] {self.message}"


@dataclass
class RunLocalDeniedError(DLIError):
    """Local execution denied by server policy.

    Raised when the server denies a request for local execution.

    Attributes:
        server_message: Message from the server explaining the denial.
    """

    code: ErrorCode = ErrorCode.RUN_LOCAL_DENIED
    server_message: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        base = f"[{self.code.value}] Local execution not permitted by server policy"
        if self.server_message:
            base += f": {self.server_message}"
        elif self.message:
            base += f": {self.message}"
        return base


@dataclass
class RunServerUnavailableError(DLIError):
    """Server execution unavailable.

    Raised when server execution is requested but the server is unreachable.

    Attributes:
        server_url: URL of the server that was unavailable.
    """

    code: ErrorCode = ErrorCode.RUN_SERVER_UNAVAILABLE
    server_url: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        base = f"[{self.code.value}] Server execution unavailable"
        if self.server_url:
            base += f" at {self.server_url}"
        elif self.message:
            base += f": {self.message}"
        return base


@dataclass
class RunExecutionError(DLIError):
    """Run query execution failed.

    Raised when the SQL query execution fails.

    Attributes:
        cause: Original exception or error description that caused the failure.
    """

    code: ErrorCode = ErrorCode.RUN_EXECUTION_FAILED
    cause: str | Exception | None = None

    def __post_init__(self) -> None:
        """Chain the cause exception and initialize base class."""
        super().__post_init__()
        if isinstance(self.cause, Exception):
            self.__cause__ = self.cause

    def __str__(self) -> str:
        """Return formatted error message."""
        base = f"[{self.code.value}] Query execution failed"
        if self.message:
            base += f": {self.message}"
        if self.cause:
            base += f" (cause: {self.cause})"
        return base


@dataclass
class RunOutputError(DLIError):
    """Run output file write failed.

    Raised when the output file cannot be written.

    Attributes:
        path: Path to the output file that could not be written.
    """

    code: ErrorCode = ErrorCode.RUN_OUTPUT_FAILED
    path: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.path:
            return f"[{self.code.value}] Cannot write output file: {self.path}"
        return f"[{self.code.value}] {self.message}"


@dataclass
class RunTimeoutError(DLIError):
    """Run query timeout error.

    Raised when a run query exceeds the configured timeout duration.

    Attributes:
        timeout_seconds: The timeout duration that was exceeded.
    """

    code: ErrorCode = ErrorCode.RUN_TIMEOUT
    timeout_seconds: float | None = None

    def __str__(self) -> str:
        """Return formatted error message."""
        base = f"[{self.code.value}] Query execution timed out"
        if self.timeout_seconds is not None:
            base += f" after {self.timeout_seconds}s"
        elif self.message:
            base += f": {self.message}"
        return base


@dataclass
class RunParameterInvalidError(DLIError):
    """Run parameter invalid error.

    Raised when a parameter has an invalid format or value.

    Attributes:
        parameter: The invalid parameter string.
    """

    code: ErrorCode = ErrorCode.RUN_PARAMETER_INVALID
    parameter: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.parameter:
            return f"[{self.code.value}] Invalid parameter format: '{self.parameter}'. Expected 'key=value'"
        return f"[{self.code.value}] {self.message}"


# Debug Errors (DLI-95x)


@dataclass
class DebugCheckError(DLIError):
    """Base debug check error.

    Raised when a debug diagnostic check fails.

    Attributes:
        check_name: Name of the check that failed.
        category: Check category (system, config, server, etc.).
    """

    code: ErrorCode = ErrorCode.DEBUG_SYSTEM_CHECK_FAILED
    check_name: str = ""
    category: str = ""

    def __str__(self) -> str:
        """Return formatted error message."""
        if self.check_name:
            return f"[{self.code.value}] Debug check failed: {self.check_name}"
        return f"[{self.code.value}] {self.message}"


@dataclass
class DebugTimeoutError(DLIError):
    """Debug check timeout error.

    Raised when a debug check exceeds the configured timeout.

    Attributes:
        check_name: Name of the check that timed out.
        timeout_seconds: The timeout duration that was exceeded.
    """

    code: ErrorCode = ErrorCode.DEBUG_TIMEOUT
    check_name: str = ""
    timeout_seconds: float | None = None

    def __str__(self) -> str:
        """Return formatted error message."""
        base = f"[{self.code.value}] Debug check timed out"
        if self.check_name:
            base += f": {self.check_name}"
        if self.timeout_seconds is not None:
            base += f" after {self.timeout_seconds}s"
        return base


__all__ = [
    # Catalog Errors
    "CatalogAccessDeniedError",
    "CatalogError",
    "CatalogSchemaError",
    "CatalogTableNotFoundError",
    # Config Errors
    "ConfigEnvNotFoundError",
    "ConfigTemplateError",
    "ConfigValidationError",
    "ConfigWriteError",
    "ConfigurationError",
    "DLIError",
    "DLIValidationError",
    "DatasetNotFoundError",
    # Debug Errors
    "DebugCheckError",
    "DebugTimeoutError",
    "ErrorCode",
    "ExecutionError",
    "InvalidIdentifierError",
    # Lineage Errors
    "LineageError",
    "LineageNotFoundError",
    "LineageTimeoutError",
    "MetricNotFoundError",
    # Quality Errors
    "QualityNotFoundError",
    "QualitySpecNotFoundError",
    "QualitySpecParseError",
    "QualityTargetNotFoundError",
    "QualityTestExecutionError",
    "QualityTestTimeoutError",
    # Query Errors
    "QueryAccessDeniedError",
    "QueryCancelError",
    "QueryInvalidFilterError",
    "QueryNotFoundError",
    # Run Errors
    "RunExecutionError",
    "RunFileNotFoundError",
    "RunLocalDeniedError",
    "RunOutputError",
    "RunParameterInvalidError",
    "RunServerUnavailableError",
    "RunTimeoutError",
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
