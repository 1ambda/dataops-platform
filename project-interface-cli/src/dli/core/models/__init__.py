"""DLI Core Engine Models.

This package provides the core data models for the DLI system, organized into
logical modules following 2025 best practices from SQLMesh and dbt.

Modules:
    base: Core enums and parameter models
    spec: Base specification model (SpecBase)
    metric: Semantic layer models (MetricSpec, MetricDefinition, DimensionDefinition)
    dataset: Dataset specification (DatasetSpec)
    results: Execution and validation results

Usage:
    >>> from dli.core.models import DatasetSpec, MetricSpec
    >>> from dli.core.models import AggregationType, MetricDefinition

All models are re-exported from this package for convenience.
"""

# Base models and enums
from dli.core.models.base import (
    DatasetVersion,
    ExecutionConfig,
    ParameterType,
    QueryParameter,
    QueryType,
    SpecType,
    StatementDefinition,
)

# Dataset models
from dli.core.models.dataset import DatasetSpec

# Metric models
from dli.core.models.metric import (
    AggregationType,
    DimensionDefinition,
    DimensionType,
    MetricDefinition,
    MetricSpec,
)

# Result models
from dli.core.models.results import (
    DatasetExecutionResult,
    ExecutionResult,
    MetricExecutionResult,
    ValidationResult,
)

# Base specification
from dli.core.models.spec import SpecBase

# Type alias for union of spec types
Spec = MetricSpec | DatasetSpec

__all__ = [  # noqa: RUF022 - Grouped by module for readability
    # Base types
    "QueryType",
    "SpecType",
    "ParameterType",
    "QueryParameter",
    "StatementDefinition",
    "DatasetVersion",
    "ExecutionConfig",
    # Base spec
    "SpecBase",
    # Metric types
    "AggregationType",
    "DimensionType",
    "MetricDefinition",
    "DimensionDefinition",
    "MetricSpec",
    # Dataset types
    "DatasetSpec",
    # Result types
    "ValidationResult",
    "ExecutionResult",
    "DatasetExecutionResult",
    "MetricExecutionResult",
    # Type alias
    "Spec",
]
