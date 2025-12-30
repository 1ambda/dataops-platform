"""Common models for DLI Library API.

This module provides shared data models used across all API classes:
- ExecutionContext: Configuration and runtime settings
- ExecutionMode: Query execution location enum
- ResultStatus: Execution result status enum
- BaseResult: Base class for execution results
- ValidationResult: Validation operation result

Example:
    >>> from dli.models.common import ExecutionContext, ExecutionMode
    >>> ctx = ExecutionContext(
    ...     project_path="/path/to/project",
    ...     execution_mode=ExecutionMode.MOCK,
    ... )
    >>> print(ctx.dialect)
    trino
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from pathlib import Path
from typing import Any, Literal, TypeAlias
import warnings

from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# Type aliases for clarity (explicit TypeAlias for static analysis)
SQLDialect: TypeAlias = Literal["trino", "bigquery", "snowflake", "duckdb", "spark"]

# DataSource semantics:
# - "local": Read YAML spec files from local disk (project_path based)
# - "server": Query registered Dataset/Metric from Basecamp Server API
DataSource: TypeAlias = Literal["local", "server"]


class ExecutionMode(str, Enum):
    """Query execution location.

    Determines where SQL queries are executed:
    - LOCAL: Direct execution on Query Engine (BigQuery, Trino, etc.)
    - SERVER: Execution via Basecamp Server API
    - MOCK: Test mode with no actual execution

    Note: This is different from DataSource which determines where
    spec files are loaded from.

    Example:
        >>> ctx = ExecutionContext(execution_mode=ExecutionMode.LOCAL)
        >>> if ctx.execution_mode == ExecutionMode.MOCK:
        ...     print("Running in mock mode")
    """

    LOCAL = "local"
    SERVER = "server"
    MOCK = "mock"


class ExecutionContext(BaseSettings):
    """Library API execution context.

    Loads configuration from environment variables with DLI_ prefix.
    For example: DLI_SERVER_URL, DLI_PROJECT_PATH, DLI_EXECUTION_MODE

    Attributes:
        project_path: Project root directory path.
        server_url: Basecamp server URL for API calls.
        api_token: API authentication token.
        execution_mode: Query execution mode (local/server/mock).
        timeout: Query execution timeout in seconds.
        dry_run: Dry-run mode (no actual execution).
        dialect: Default SQL dialect.
        parameters: Runtime parameters for Jinja rendering.
        verbose: Enable verbose logging.

    Example:
        >>> # From environment variables
        >>> ctx = ExecutionContext()
        >>> print(ctx.execution_mode)  # Uses DLI_EXECUTION_MODE env var
        ExecutionMode.LOCAL

        >>> # Explicit configuration
        >>> ctx = ExecutionContext(
        ...     server_url="https://basecamp.example.com",
        ...     execution_mode=ExecutionMode.SERVER,
        ... )

        >>> # Mock mode for testing
        >>> ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)

    Migration from mock_mode:
        The `mock_mode` parameter is deprecated. Use `execution_mode=ExecutionMode.MOCK`
        instead. Passing `mock_mode=True` will automatically set execution_mode to MOCK.
    """

    model_config = SettingsConfigDict(
        env_prefix="DLI_",
        env_file=".env",
        extra="ignore",
    )

    # Project settings
    project_path: Path | None = Field(
        default=None,
        description="Project root path for local spec files",
    )

    # Server connection
    server_url: str | None = Field(
        default=None,
        description="Basecamp server URL",
    )
    api_token: str | None = Field(
        default=None,
        description="API authentication token",
    )

    # Execution options
    execution_mode: ExecutionMode = Field(
        default=ExecutionMode.LOCAL,
        description="Query execution mode (local/server/mock)",
    )
    timeout: int = Field(
        default=300,
        ge=1,
        le=3600,
        description="Query execution timeout in seconds",
    )
    dry_run: bool = Field(
        default=False,
        description="Dry-run mode (no actual execution)",
    )
    dialect: SQLDialect = Field(
        default="trino",
        description="Default SQL dialect",
    )

    # Runtime parameters for Jinja rendering
    parameters: dict[str, Any] = Field(
        default_factory=dict,
        description="Parameters for Jinja template rendering",
    )

    # Logging
    verbose: bool = Field(
        default=False,
        description="Enable verbose logging",
    )

    @property
    def mock_mode(self) -> bool:
        """Deprecated. Use execution_mode == ExecutionMode.MOCK instead.

        Returns:
            True if execution_mode is MOCK.
        """
        warnings.warn(
            "mock_mode is deprecated. Use execution_mode == ExecutionMode.MOCK",
            DeprecationWarning,
            stacklevel=2,
        )
        return self.execution_mode == ExecutionMode.MOCK

    @model_validator(mode="before")
    @classmethod
    def _migrate_mock_mode(cls, data: dict[str, Any]) -> dict[str, Any]:
        """Migrate legacy mock_mode=True to execution_mode=MOCK.

        This validator provides backward compatibility for users who still
        use the old mock_mode parameter.
        """
        if isinstance(data, dict) and data.get("mock_mode") is True:
            data.setdefault("execution_mode", ExecutionMode.MOCK)
            # Remove mock_mode to avoid pydantic validation error
            data.pop("mock_mode", None)
        return data

    def __repr__(self) -> str:
        """Return concise representation."""
        return (
            f"ExecutionContext(server_url={self.server_url!r}, "
            f"execution_mode={self.execution_mode.value!r}, dialect={self.dialect!r})"
        )


class ResultStatus(str, Enum):
    """API execution result status.

    NOTE: This is for API results. For Workflow run status,
    use RunStatus from dli.core.client.

    Attributes:
        SUCCESS: Operation completed successfully.
        FAILURE: Operation failed with error.
        SKIPPED: Operation was skipped (e.g., already up-to-date).
        PENDING: Operation is pending/not started.
    """

    SUCCESS = "success"
    FAILURE = "failure"
    SKIPPED = "skipped"
    PENDING = "pending"


class ValidationResult(BaseModel):
    """Validation operation result.

    Returned by validate() methods to indicate spec/SQL validity.

    Attributes:
        valid: Whether validation passed.
        errors: List of error messages.
        warnings: List of warning messages.

    Example:
        >>> result = api.validate("my_dataset")
        >>> if not result.valid:
        ...     for error in result.errors:
        ...         print(f"Error: {error}")
    """

    model_config = ConfigDict(frozen=True)

    valid: bool = Field(..., description="Whether validation passed")
    errors: list[str] = Field(default_factory=list, description="Error messages")
    warnings: list[str] = Field(default_factory=list, description="Warning messages")

    @property
    def has_errors(self) -> bool:
        """Check if there are any errors."""
        return len(self.errors) > 0

    @property
    def has_warnings(self) -> bool:
        """Check if there are any warnings."""
        return len(self.warnings) > 0


class BaseResult(BaseModel):
    """Base class for execution results.

    Provides common fields for all execution result types.

    Attributes:
        status: Execution status.
        started_at: When execution started.
        ended_at: When execution ended.
        duration_ms: Execution duration in milliseconds.
        error_message: Error message if failed.
    """

    model_config = ConfigDict(frozen=True)

    status: ResultStatus = Field(..., description="Execution status")
    started_at: datetime = Field(..., description="Execution start time")
    ended_at: datetime | None = Field(default=None, description="Execution end time")
    duration_ms: int | None = Field(
        default=None, description="Duration in milliseconds"
    )
    error_message: str | None = Field(
        default=None, description="Error message if failed"
    )


class DatasetResult(BaseResult):
    """Dataset execution result.

    Returned by DatasetAPI.run() method.

    Attributes:
        name: Dataset name.
        sql: Rendered SQL (if show_sql=True).
        rows_affected: Number of rows affected.
    """

    name: str = Field(..., description="Dataset name")
    sql: str | None = Field(default=None, description="Rendered SQL")
    rows_affected: int | None = Field(default=None, description="Rows affected")


class MetricResult(BaseResult):
    """Metric execution result.

    Returned by MetricAPI.run() method.

    Attributes:
        name: Metric name.
        sql: Rendered SQL (if show_sql=True).
        data: Query result rows.
        row_count: Number of rows returned.
        columns: Column names.
    """

    name: str = Field(..., description="Metric name")
    sql: str | None = Field(default=None, description="Rendered SQL")
    data: list[dict[str, Any]] | None = Field(default=None, description="Result rows")
    row_count: int | None = Field(default=None, description="Number of rows")
    columns: list[str] | None = Field(default=None, description="Column names")


class TranspileWarning(BaseModel):
    """Transpile warning information.

    Attributes:
        message: Warning message.
        line: Line number (if available).
        column: Column number (if available).
        rule: Related rule name (if applicable).
    """

    model_config = ConfigDict(frozen=True)

    message: str = Field(..., description="Warning message")
    line: int | None = Field(default=None, description="Line number")
    column: int | None = Field(default=None, description="Column number")
    rule: str | None = Field(default=None, description="Related rule")


class TranspileRule(BaseModel):
    """Transpile rule information.

    Attributes:
        source_table: Source table pattern.
        target_table: Target table replacement.
        priority: Rule priority (higher = applied first).
        enabled: Whether rule is enabled.
    """

    model_config = ConfigDict(frozen=True)

    source_table: str = Field(..., description="Source table pattern")
    target_table: str = Field(..., description="Target table replacement")
    priority: int = Field(default=0, description="Rule priority")
    enabled: bool = Field(default=True, description="Whether enabled")


class TranspileResult(BaseModel):
    """Transpile operation result.

    Returned by TranspileAPI.transpile() method.

    Attributes:
        original_sql: Original SQL before transpilation.
        transpiled_sql: SQL after transpilation.
        success: Whether transpilation succeeded.
        applied_rules: Rules that were applied.
        warnings: Warnings generated.
        duration_ms: Processing time in milliseconds.
    """

    model_config = ConfigDict(frozen=True)

    original_sql: str = Field(..., description="Original SQL")
    transpiled_sql: str = Field(..., description="Transpiled SQL")
    success: bool = Field(..., description="Whether successful")
    applied_rules: list[TranspileRule] = Field(
        default_factory=list, description="Applied rules"
    )
    warnings: list[TranspileWarning] = Field(
        default_factory=list, description="Warnings"
    )
    duration_ms: int = Field(..., description="Processing time (ms)")

    @property
    def has_changes(self) -> bool:
        """Check if SQL was modified."""
        return self.original_sql != self.transpiled_sql


class EnvironmentInfo(BaseModel):
    """Environment configuration information.

    Attributes:
        name: Environment name (e.g., "dev", "prod").
        connection_string: Connection string (masked).
        is_active: Whether this is the current environment.
    """

    model_config = ConfigDict(frozen=True)

    name: str = Field(..., description="Environment name")
    connection_string: str | None = Field(default=None, description="Connection string")
    is_active: bool = Field(default=False, description="Is active environment")


class ConfigValue(BaseModel):
    """Configuration value with metadata.

    Attributes:
        key: Configuration key.
        value: Configuration value.
        source: Where the value came from.
    """

    model_config = ConfigDict(frozen=True)

    key: str = Field(..., description="Config key")
    value: Any = Field(..., description="Config value")
    source: str = Field(default="config", description="Value source")


__all__ = [
    "BaseResult",
    "ConfigValue",
    "DataSource",
    "DatasetResult",
    # Config models
    "EnvironmentInfo",
    # Context
    "ExecutionContext",
    "ExecutionMode",
    "MetricResult",
    # Enums
    "ResultStatus",
    # Type aliases
    "SQLDialect",
    "TranspileResult",
    "TranspileRule",
    "TranspileWarning",
    # Result models
    "ValidationResult",
]
