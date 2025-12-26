"""DLI Core Engine.

This module provides the core functionality for the DLI CLI:
- models: Pydantic data models for datasets, parameters, and results
- discovery: DLI_HOME discovery and project configuration loading
- registry: Dataset spec registry with caching and search
- renderer: Jinja2 SQL template rendering
- validator: SQLGlot-based SQL validation
- executor: Abstract base executor and 3-stage execution engine
- service: Unified service layer
"""

from dli.core.discovery import (
    DatasetDiscovery,
    ProjectConfig,
    get_dli_home,
    load_project,
)
from dli.core.executor import (
    BaseExecutor,
    DatasetExecutor,
    FailingMockExecutor,
    MockExecutor,
)
from dli.core.models import (
    DatasetExecutionResult,
    DatasetSpec,
    DatasetVersion,
    ExecutionConfig,
    ExecutionResult,
    ParameterType,
    QueryParameter,
    QueryType,
    StatementDefinition,
    ValidationResult,
)
from dli.core.registry import DatasetRegistry
from dli.core.renderer import SQLRenderer
from dli.core.service import DatasetService
from dli.core.validator import SQLValidator

__all__ = [  # noqa: RUF022 - Grouped by module for readability
    # Models
    "DatasetExecutionResult",
    "DatasetSpec",
    "DatasetVersion",
    "ExecutionConfig",
    "ExecutionResult",
    "ParameterType",
    "QueryParameter",
    "QueryType",
    "StatementDefinition",
    "ValidationResult",
    # Discovery
    "DatasetDiscovery",
    "ProjectConfig",
    "get_dli_home",
    "load_project",
    # Registry
    "DatasetRegistry",
    # Renderer
    "SQLRenderer",
    # Validator
    "SQLValidator",
    # Executor
    "BaseExecutor",
    "DatasetExecutor",
    "FailingMockExecutor",
    "MockExecutor",
    # Service
    "DatasetService",
]
