"""DataOps CLI - Command-line interface and Library for DataOps platform.

This package provides both a CLI (`dli`) and a Library API for programmatic
access to DataOps platform operations.

CLI Usage:
    $ dli dataset list
    $ dli metric run my_metric --params date=2025-01-01
    $ dli transpile run "SELECT * FROM raw.events"

Library Usage:
    >>> from dli import DatasetAPI, MetricAPI, ExecutionContext
    >>> from dli.models.common import ExecutionMode
    >>> ctx = ExecutionContext(
    ...     project_path="/path/to/project",
    ...     execution_mode=ExecutionMode.MOCK,
    ... )
    >>> api = DatasetAPI(context=ctx)
    >>> result = api.run("catalog.schema.dataset", dry_run=True)

API Classes:
    - DatasetAPI: Dataset CRUD and execution
    - MetricAPI: Metric CRUD and execution
    - TranspileAPI: SQL transpilation
    - CatalogAPI: Data catalog browsing
    - ConfigAPI: Configuration management (read-only)
    - QualityAPI: Data quality testing

Exceptions:
    - DLIError: Base exception for all DLI errors
    - DatasetNotFoundError: Dataset not found
    - MetricNotFoundError: Metric not found
    - TranspileError: SQL transpilation failure
    - ExecutionError: Execution failure
    - ConfigurationError: Configuration error
    - ServerError: Server communication error
    - QualitySpecNotFoundError: Quality Spec not found
    - QualitySpecParseError: Quality Spec parsing error
    - QualityNotFoundError: Quality not found on server

Example (Airflow PythonOperator):
    >>> from airflow.decorators import task
    >>> from dli import DatasetAPI, ExecutionContext
    >>>
    >>> @task
    >>> def run_dataset(dataset_name: str, execution_date: str) -> dict:
    ...     ctx = ExecutionContext(
    ...         project_path="/opt/airflow/dags/models",
    ...         parameters={"execution_date": execution_date},
    ...     )
    ...     api = DatasetAPI(context=ctx)
    ...     result = api.run(dataset_name)
    ...     return result.model_dump()
"""

__version__ = "0.2.0"

# Public API classes
from dli.api import (
    CatalogAPI,
    ConfigAPI,
    DatasetAPI,
    DebugAPI,
    LineageAPI,
    MetricAPI,
    QualityAPI,
    QueryAPI,
    RunAPI,
    SqlAPI,
    TranspileAPI,
    WorkflowAPI,
)

# Debug models (public API)
from dli.core.debug.models import CheckCategory, CheckResult, CheckStatus, DebugResult

# Exceptions
from dli.exceptions import (
    ConfigEnvNotFoundError,
    ConfigTemplateError,
    ConfigurationError,
    ConfigValidationError,
    ConfigWriteError,
    DatasetNotFoundError,
    DebugCheckError,
    DebugTimeoutError,
    DLIError,
    DLIValidationError,
    ErrorCode,
    ExecutionError,
    FormatConfigError,
    FormatDialectError,
    FormatError,
    FormatLintError,
    FormatSqlError,
    FormatYamlError,
    LineageError,
    LineageNotFoundError,
    LineageTimeoutError,
    MetricNotFoundError,
    QualityNotFoundError,
    QualitySpecNotFoundError,
    QualitySpecParseError,
    QualityTargetNotFoundError,
    QueryAccessDeniedError,
    QueryCancelError,
    QueryInvalidFilterError,
    QueryNotFoundError,
    RunExecutionError,
    RunFileNotFoundError,
    RunLocalDeniedError,
    RunOutputError,
    RunServerUnavailableError,
    ServerError,
    SqlAccessDeniedError,
    SqlFileNotFoundError,
    SqlProjectNotFoundError,
    SqlSnippetNotFoundError,
    SqlUpdateFailedError,
    TableNotFoundError,
    TranspileError,
    WorkflowExecutionError,
    WorkflowNotFoundError,
    WorkflowPermissionError,
    WorkflowRegistrationError,
)

# Context and Configuration
# Config models
from dli.models.common import ConfigValue, ExecutionContext, ExecutionMode
from dli.models.config import (
    ConfigSource,
    ConfigValidationResult,
    ConfigValueInfo,
    EnvironmentProfile,
)

# Format models (public API)
from dli.models.format import (
    FileFormatResult,
    FileFormatStatus,
    FormatResult,
    FormatStatus,
    LintViolation,
)

# Run models (public API)
from dli.models.run import ExecutionPlan, OutputFormat, RunResult

__all__ = [
    # API Classes
    "CatalogAPI",
    # Debug models
    "CheckCategory",
    "CheckResult",
    "CheckStatus",
    "ConfigAPI",
    # Exceptions
    "ConfigEnvNotFoundError",
    # Config Models
    "ConfigSource",
    "ConfigTemplateError",
    "ConfigValidationError",
    "ConfigValidationResult",
    "ConfigValue",
    "ConfigValueInfo",
    "ConfigWriteError",
    "ConfigurationError",
    "DLIError",
    "DLIValidationError",
    "DatasetAPI",
    "DatasetNotFoundError",
    "DebugAPI",
    "DebugCheckError",
    "DebugResult",
    "DebugTimeoutError",
    "EnvironmentProfile",
    "ErrorCode",
    # Context
    "ExecutionContext",
    "ExecutionError",
    "ExecutionMode",
    # Run models
    "ExecutionPlan",
    # Format models
    "FileFormatResult",
    "FileFormatStatus",
    "FormatConfigError",
    "FormatDialectError",
    "FormatError",
    "FormatLintError",
    "FormatResult",
    "FormatSqlError",
    "FormatStatus",
    "FormatYamlError",
    "LineageAPI",
    "LineageError",
    "LineageNotFoundError",
    "LineageTimeoutError",
    "LintViolation",
    "MetricAPI",
    "MetricNotFoundError",
    "OutputFormat",
    "QualityAPI",
    "QualityNotFoundError",
    "QualitySpecNotFoundError",
    "QualitySpecParseError",
    "QualityTargetNotFoundError",
    "QueryAPI",
    "QueryAccessDeniedError",
    "QueryCancelError",
    "QueryInvalidFilterError",
    "QueryNotFoundError",
    "RunAPI",
    "RunExecutionError",
    "RunFileNotFoundError",
    "RunLocalDeniedError",
    "RunOutputError",
    "RunResult",
    "RunServerUnavailableError",
    "ServerError",
    "SqlAPI",
    "SqlAccessDeniedError",
    "SqlFileNotFoundError",
    "SqlProjectNotFoundError",
    "SqlSnippetNotFoundError",
    "SqlUpdateFailedError",
    "TableNotFoundError",
    "TranspileAPI",
    "TranspileError",
    "WorkflowAPI",
    "WorkflowExecutionError",
    "WorkflowNotFoundError",
    "WorkflowPermissionError",
    "WorkflowRegistrationError",
    # Version
    "__version__",
]
