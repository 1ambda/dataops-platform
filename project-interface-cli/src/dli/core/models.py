"""Pydantic data models for the DLI Core Engine.

This module defines the core data structures used throughout the DLI system:
- QueryParameter: Parameter definition with type validation
- StatementDefinition: Pre/Post SQL statement definition
- DatasetVersion: Version information for datasets
- ExecutionConfig: Execution settings
- DatasetSpec: Full dataset specification
- ValidationResult: SQL validation result
- ExecutionResult: Single SQL execution result
- DatasetExecutionResult: Full dataset execution result (Pre -> Main -> Post)
"""

from __future__ import annotations

from datetime import UTC, date, datetime
from enum import Enum
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, PrivateAttr

# Constants for fully qualified name parsing (catalog.schema.table)
_FQN_CATALOG_INDEX = 0
_FQN_SCHEMA_INDEX = 1
_FQN_TABLE_INDEX = 2
_FQN_MIN_PARTS_FOR_SCHEMA = 2
_FQN_MIN_PARTS_FOR_TABLE = 3


def _utc_now() -> datetime:
    """Return current UTC time as a timezone-aware datetime."""
    return datetime.now(UTC)


class QueryType(str, Enum):
    """Query type classification."""

    SELECT = "SELECT"
    DML = "DML"


class ParameterType(str, Enum):
    """Supported parameter types for SQL query parameters."""

    STRING = "string"
    INTEGER = "integer"
    FLOAT = "float"
    DATE = "date"
    BOOLEAN = "boolean"
    LIST = "list"


class QueryParameter(BaseModel):
    """Query parameter definition with type validation.

    Attributes:
        name: Parameter name (used in Jinja2 templates)
        type: Parameter type for validation and conversion
        required: Whether the parameter must be provided
        default: Default value if not provided
        description: Human-readable parameter description
    """

    name: str
    type: ParameterType = ParameterType.STRING
    required: bool = True
    default: Any | None = None
    description: str = ""

    def validate_value(self, value: Any) -> Any:
        """Validate and convert a parameter value to the appropriate type.

        Args:
            value: The raw value to validate and convert

        Returns:
            The converted value

        Raises:
            ValueError: If a required parameter is missing or type conversion fails
        """
        if value is None:
            if self.required and self.default is None:
                msg = f"Required parameter '{self.name}' is missing"
                raise ValueError(msg)
            return self.default

        converters: dict[ParameterType, Any] = {
            ParameterType.INTEGER: int,
            ParameterType.FLOAT: float,
            ParameterType.BOOLEAN: lambda x: str(x).lower() in ("true", "1", "yes"),
            ParameterType.STRING: str,
            ParameterType.DATE: lambda x: x
            if isinstance(x, date)
            else date.fromisoformat(str(x)),
            ParameterType.LIST: lambda x: x if isinstance(x, list) else [x],
        }

        converter = converters.get(self.type, str)
        try:
            return converter(value)
        except (ValueError, TypeError) as e:
            msg = f"Failed to convert parameter '{self.name}' to {self.type.value}: {e}"
            raise ValueError(msg) from e


class StatementDefinition(BaseModel):
    """Pre/Post SQL statement definition (inline or file reference).

    Attributes:
        name: Statement name for identification
        sql: Inline SQL content
        file: SQL file path (relative to spec file)
        continue_on_error: Whether to continue execution on error
    """

    name: str
    sql: str | None = None
    file: str | None = None
    continue_on_error: bool = False

    def get_sql(self, base_dir: Path) -> str:
        """Get the SQL content from inline or file.

        Args:
            base_dir: Base directory for resolving file paths

        Returns:
            SQL content string

        Raises:
            ValueError: If neither sql nor file is provided
        """
        if self.sql:
            return self.sql
        if self.file:
            file_path = base_dir / self.file
            return file_path.read_text(encoding="utf-8")
        msg = f"Statement '{self.name}' has no sql or file"
        raise ValueError(msg)


class DatasetVersion(BaseModel):
    """Version information for a dataset.

    Attributes:
        version: Version identifier (e.g., "v1", "v2")
        started_at: Version start date
        ended_at: Version end date (None if currently active)
        description: Version description
    """

    version: str
    started_at: date
    ended_at: date | None = None
    description: str = ""

    @property
    def is_active(self) -> bool:
        """Check if this version is currently active."""
        return self.ended_at is None


class ExecutionConfig(BaseModel):
    """Execution configuration settings.

    Attributes:
        timeout_seconds: Query execution timeout
        retry_count: Number of retries on failure
        retry_delay_seconds: Delay between retries
        dialect: SQL dialect (e.g., "trino", "bigquery")
    """

    timeout_seconds: int = 3600
    retry_count: int = 2
    retry_delay_seconds: int = 60
    dialect: str = "trino"


class DatasetSpec(BaseModel):
    """Full Dataset Specification.

    This is the core data model representing a dataset definition
    loaded from a spec.*.yaml file.

    Attributes:
        name: Fully qualified name (catalog.schema.table)
        description: Human-readable description
        owner: Owner email
        team: Team identifier (e.g., "@data-analytics")
        domains: List of domain tags
        tags: List of general tags
        versions: List of version information
        query_type: Query type (SELECT or DML)
        parameters: List of query parameters
        query_statement: Inline SQL content
        query_file: SQL file path
        pre_statements: Pre-execution statements
        post_statements: Post-execution statements
        execution: Execution configuration
        depends_on: List of upstream dependencies
        schema_fields: Output schema definition
    """

    # Basic identifiers
    name: str
    description: str = ""

    # Ownership
    owner: str
    team: str
    domains: list[str] = Field(default_factory=list)
    tags: list[str] = Field(default_factory=list)

    # Versioning
    versions: list[DatasetVersion] = Field(default_factory=list)

    # Query definition
    query_type: QueryType
    parameters: list[QueryParameter] = Field(default_factory=list)

    # SQL content (inline or file-based)
    query_statement: str | None = None
    query_file: str | None = None

    # Pre/Post statements
    pre_statements: list[StatementDefinition] = Field(default_factory=list)
    post_statements: list[StatementDefinition] = Field(default_factory=list)

    # Execution settings
    execution: ExecutionConfig = Field(default_factory=ExecutionConfig)

    # Metadata
    depends_on: list[str] = Field(default_factory=list)
    schema_fields: list[dict[str, Any]] = Field(default_factory=list, alias="schema")

    # Internal fields (set during loading via PrivateAttr for Pydantic compatibility)
    _spec_path: Path | None = PrivateAttr(default=None)
    _base_dir: Path | None = PrivateAttr(default=None)

    model_config = ConfigDict(populate_by_name=True)

    def get_main_sql(self) -> str:
        """Get the main SQL content from inline or file.

        Returns:
            Main SQL content string

        Raises:
            ValueError: If neither query_statement nor query_file is provided
        """
        if self.query_statement:
            return self.query_statement
        if self.query_file and self._base_dir:
            return (self._base_dir / self.query_file).read_text(encoding="utf-8")
        msg = f"Dataset '{self.name}' has no query_statement or query_file"
        raise ValueError(msg)

    @property
    def catalog(self) -> str:
        """Extract catalog from the fully qualified name."""
        parts = self.name.split(".")
        return parts[_FQN_CATALOG_INDEX] if parts else ""

    @property
    def schema_name(self) -> str:
        """Extract schema from the fully qualified name."""
        parts = self.name.split(".")
        return parts[_FQN_SCHEMA_INDEX] if len(parts) >= _FQN_MIN_PARTS_FOR_SCHEMA else ""

    @property
    def table(self) -> str:
        """Extract table from the fully qualified name."""
        parts = self.name.split(".")
        return parts[_FQN_TABLE_INDEX] if len(parts) >= _FQN_MIN_PARTS_FOR_TABLE else ""

    @property
    def active_version(self) -> DatasetVersion | None:
        """Get the currently active version."""
        for v in self.versions:
            if v.is_active:
                return v
        return None

    @property
    def spec_path(self) -> Path | None:
        """Get the path to the spec file (set during loading)."""
        return self._spec_path

    @property
    def base_dir(self) -> Path | None:
        """Get the base directory for resolving relative paths (set during loading)."""
        return self._base_dir

    def set_paths(self, spec_path: Path) -> None:
        """Set the spec path and base directory.

        Args:
            spec_path: Path to the spec file
        """
        self._spec_path = spec_path
        self._base_dir = spec_path.parent


class ValidationResult(BaseModel):
    """Result of SQL validation.

    Attributes:
        is_valid: Whether the SQL is valid
        errors: List of validation errors
        warnings: List of validation warnings
        rendered_sql: The rendered SQL (if rendering was successful)
        phase: Execution phase (pre, main, post)
    """

    is_valid: bool
    errors: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    rendered_sql: str | None = None
    phase: str = "main"


class ExecutionResult(BaseModel):
    """Result of a single SQL execution.

    Attributes:
        dataset_name: Name of the dataset
        phase: Execution phase (pre, main, post)
        statement_name: Name of the statement (for pre/post)
        success: Whether execution was successful
        row_count: Number of rows affected/returned
        columns: List of column names
        data: List of row dictionaries
        rendered_sql: The SQL that was executed
        execution_time_ms: Execution time in milliseconds
        error_message: Error message if execution failed
        executed_at: Timestamp of execution
    """

    dataset_name: str
    phase: str
    statement_name: str | None = None
    success: bool
    row_count: int | None = None
    columns: list[str] = Field(default_factory=list)
    data: list[dict[str, Any]] = Field(default_factory=list)
    rendered_sql: str = ""
    execution_time_ms: int = 0
    error_message: str | None = None
    executed_at: datetime = Field(default_factory=_utc_now)


class DatasetExecutionResult(BaseModel):
    """Result of full dataset execution (Pre -> Main -> Post).

    Attributes:
        dataset_name: Name of the dataset
        success: Whether overall execution was successful
        pre_results: Results from pre-statements
        main_result: Result from main query
        post_results: Results from post-statements
        total_execution_time_ms: Total execution time
        error_message: Error message if execution failed
    """

    dataset_name: str
    success: bool
    pre_results: list[ExecutionResult] = Field(default_factory=list)
    main_result: ExecutionResult | None = None
    post_results: list[ExecutionResult] = Field(default_factory=list)
    total_execution_time_ms: int = 0
    error_message: str | None = None
