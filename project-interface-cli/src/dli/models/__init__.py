"""DLI Library Public Models.

This module exports all public data models for the DLI Library API.

Example:
    >>> from dli.models import ExecutionContext, DatasetResult, ValidationResult
    >>> ctx = ExecutionContext(mock_mode=True)
    >>> result = api.run("my_dataset")
    >>> print(result.status)
"""

from dli.models.common import (
    # Result models
    BaseResult,
    ConfigValue,
    DatasetResult,
    # Type aliases
    DataSource,
    EnvironmentInfo,
    # Context
    ExecutionContext,
    MetricResult,
    # Enums
    ResultStatus,
    SQLDialect,
    TranspileResult,
    TranspileRule,
    TranspileWarning,
    ValidationResult,
)
from dli.models.config import (
    # Config models
    ConfigSource,
    ConfigValidationResult,
    ConfigValueInfo,
    EnvironmentProfile,
)
from dli.models.quality import (
    # Quality models
    DqQualityResult,
    DqTestDefinitionSpec,
    QualityInfo,
    QualityMetadata,
    QualityNotifications,
    QualitySchedule,
    QualitySpec,
    QualityTarget,
    QualityTargetType,
)
from dli.models.query import (
    # Query result models
    QueryCancelResult,
    QueryDetailResult,
    QueryListResult,
)
from dli.models.run import (
    # Run result models
    ExecutionPlan,
    OutputFormat,
    RunResult,
)
from dli.models.workflow import (
    # Workflow result models
    WorkflowHistoryResult,
    WorkflowListResult,
    WorkflowRegisterResult,
    WorkflowRunResult,
    WorkflowStatusResult,
)

__all__ = [
    "BaseResult",
    # Config models
    "ConfigSource",
    "ConfigValidationResult",
    "ConfigValue",
    "ConfigValueInfo",
    "DataSource",
    "DatasetResult",
    # Quality models
    "DqQualityResult",
    "DqTestDefinitionSpec",
    # Environment models
    "EnvironmentInfo",
    "EnvironmentProfile",
    # Context
    "ExecutionContext",
    "MetricResult",
    "QualityInfo",
    "QualityMetadata",
    "QualityNotifications",
    "QualitySchedule",
    "QualitySpec",
    "QualityTarget",
    "QualityTargetType",
    # Query result models
    "QueryCancelResult",
    "QueryDetailResult",
    "QueryListResult",
    # Enums
    "ResultStatus",
    # Run result models
    "ExecutionPlan",
    "OutputFormat",
    "RunResult",
    # Type aliases
    "SQLDialect",
    "TranspileResult",
    "TranspileRule",
    "TranspileWarning",
    # Result models
    "ValidationResult",
    # Workflow result models
    "WorkflowHistoryResult",
    "WorkflowListResult",
    "WorkflowRegisterResult",
    "WorkflowRunResult",
    "WorkflowStatusResult",
]
