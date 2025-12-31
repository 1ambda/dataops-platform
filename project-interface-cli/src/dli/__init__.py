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
    MetricAPI,
    QualityAPI,
    TranspileAPI,
    WorkflowAPI,
)

# Exceptions
from dli.exceptions import (
    ConfigurationError,
    DatasetNotFoundError,
    DLIError,
    DLIValidationError,
    ErrorCode,
    ExecutionError,
    MetricNotFoundError,
    QualityNotFoundError,
    QualitySpecNotFoundError,
    QualitySpecParseError,
    QualityTargetNotFoundError,
    ServerError,
    TableNotFoundError,
    TranspileError,
    WorkflowExecutionError,
    WorkflowNotFoundError,
    WorkflowPermissionError,
    WorkflowRegistrationError,
)

# Context and Configuration
from dli.models.common import ExecutionContext, ExecutionMode

__all__ = [
    # API Classes
    "CatalogAPI",
    "ConfigAPI",
    "DatasetAPI",
    "MetricAPI",
    "QualityAPI",
    "TranspileAPI",
    "WorkflowAPI",
    # Exceptions
    "ConfigurationError",
    "DLIError",
    "DLIValidationError",
    "DatasetNotFoundError",
    "ErrorCode",
    "ExecutionError",
    "MetricNotFoundError",
    "QualityNotFoundError",
    "QualitySpecNotFoundError",
    "QualitySpecParseError",
    "QualityTargetNotFoundError",
    "ServerError",
    "TableNotFoundError",
    "TranspileError",
    "WorkflowExecutionError",
    "WorkflowNotFoundError",
    "WorkflowPermissionError",
    "WorkflowRegistrationError",
    # Context
    "ExecutionContext",
    "ExecutionMode",
    # Version
    "__version__",
]
