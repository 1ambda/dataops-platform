"""DataOps CLI - Command-line interface and Library for DataOps platform.

This package provides both a CLI (`dli`) and a Library API for programmatic
access to DataOps platform operations.

CLI Usage:
    $ dli dataset list
    $ dli metric run my_metric --params date=2025-01-01
    $ dli transpile run "SELECT * FROM raw.events"

Library Usage:
    >>> from dli import DatasetAPI, MetricAPI, ExecutionContext
    >>> ctx = ExecutionContext(
    ...     project_path="/path/to/project",
    ...     mock_mode=True,
    ... )
    >>> api = DatasetAPI(context=ctx)
    >>> result = api.run("catalog.schema.dataset", dry_run=True)

API Classes:
    - DatasetAPI: Dataset CRUD and execution
    - MetricAPI: Metric CRUD and execution
    - TranspileAPI: SQL transpilation
    - CatalogAPI: Data catalog browsing
    - ConfigAPI: Configuration management (read-only)

Exceptions:
    - DLIError: Base exception for all DLI errors
    - DatasetNotFoundError: Dataset not found
    - MetricNotFoundError: Metric not found
    - TranspileError: SQL transpilation failure
    - ExecutionError: Execution failure
    - ConfigurationError: Configuration error
    - ServerError: Server communication error

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
    TranspileAPI,
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
    ServerError,
    TableNotFoundError,
    TranspileError,
    WorkflowNotFoundError,
)

# Context and Configuration
from dli.models.common import ExecutionContext

__all__ = [
    "CatalogAPI",
    "ConfigAPI",
    "ConfigurationError",
    # Exceptions
    "DLIError",
    "DLIValidationError",
    # API Classes
    "DatasetAPI",
    "DatasetNotFoundError",
    "ErrorCode",
    # Context
    "ExecutionContext",
    "ExecutionError",
    "MetricAPI",
    "MetricNotFoundError",
    "ServerError",
    "TableNotFoundError",
    "TranspileAPI",
    "TranspileError",
    "WorkflowNotFoundError",
    # Version
    "__version__",
]
