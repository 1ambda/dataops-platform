"""DLI Library API Module.

This module provides programmatic access to DLI functionality.
Use these APIs for integration with Airflow, scripts, or applications.

Example:
    >>> from dli.api import DatasetAPI, MetricAPI, TranspileAPI
    >>> from dli.models import ExecutionContext, ExecutionMode
    >>>
    >>> ctx = ExecutionContext(
    ...     project_path="/path/to/project",
    ...     execution_mode=ExecutionMode.MOCK,
    ... )
    >>>
    >>> # Dataset operations
    >>> dataset_api = DatasetAPI(context=ctx)
    >>> datasets = dataset_api.list_datasets()
    >>> result = dataset_api.run("catalog.schema.dataset", dry_run=True)
    >>>
    >>> # Metric operations
    >>> metric_api = MetricAPI(context=ctx)
    >>> result = metric_api.run("catalog.schema.metric")
    >>> print(result.data)
    >>>
    >>> # SQL transpilation
    >>> transpile_api = TranspileAPI(context=ctx)
    >>> result = transpile_api.transpile("SELECT * FROM raw.events")
    >>> print(result.transpiled_sql)
"""

from dli.api.catalog import CatalogAPI
from dli.api.config import ConfigAPI
from dli.api.dataset import DatasetAPI
from dli.api.metric import MetricAPI
from dli.api.quality import QualityAPI
from dli.api.transpile import TranspileAPI
from dli.api.workflow import WorkflowAPI

__all__ = [
    "CatalogAPI",
    "ConfigAPI",
    "DatasetAPI",
    "MetricAPI",
    "QualityAPI",
    "TranspileAPI",
    "WorkflowAPI",
]
