"""Base models and enums for the DLI Core Engine.

This module contains foundational types shared across specs:
- QueryType: Query type classification (SELECT/DML)
- SpecType: Spec type classification (Metric/Dataset)
- ParameterType: Parameter type for validation
- QueryParameter: Parameter definition with validation
- StatementDefinition: Pre/Post SQL statement definition
- DatasetVersion: Version information for datasets
- ExecutionConfig: Execution settings
"""

from __future__ import annotations

from datetime import UTC, date, datetime
from enum import Enum
from pathlib import Path
from typing import Any

from pydantic import BaseModel


def _utc_now() -> datetime:
    """Return current UTC time as a timezone-aware datetime.

    This function is designed to be used as a Pydantic default_factory
    for datetime fields that need the current UTC timestamp.

    Returns:
        datetime: Current UTC time with timezone info.
    """
    return datetime.now(UTC)


class QueryType(str, Enum):
    """Query type classification."""

    SELECT = "SELECT"
    DML = "DML"


class SpecType(str, Enum):
    """Spec type classification for distinguishing metrics from datasets.

    - Metric: Read-only analytical queries (query_type = SELECT)
    - Dataset: Data processing operations (query_type = DML)

    File naming conventions:
    - Metric: metric.{catalog}.{schema}.{name}.yaml
    - Dataset: dataset.{catalog}.{schema}.{name}.yaml
    """

    METRIC = "Metric"
    DATASET = "Dataset"


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
