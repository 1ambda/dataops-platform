"""Internal models for Run command.

These models are used internally by the Run API and are not exposed
in the public API. For public result models, see dli/models/run.py.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

__all__ = [
    "ExecutionData",
    "RunConfig",
]


class RunConfig(BaseModel):
    """Configuration for run execution."""

    model_config = ConfigDict(frozen=True)

    sql_path: Path = Field(description="Path to SQL file")
    output_path: Path = Field(description="Path for output file")
    output_format: str = Field(default="csv", description="Output format")
    parameters: dict[str, str] = Field(
        default_factory=dict, description="SQL parameters"
    )
    limit: int | None = Field(default=None, description="Row limit")
    timeout: int = Field(default=300, description="Timeout in seconds")
    dialect: str = Field(default="bigquery", description="SQL dialect")


class ExecutionData(BaseModel):
    """Data returned from query execution."""

    model_config = ConfigDict(frozen=True)

    rows: list[dict[str, Any]] = Field(
        default_factory=list, description="Result rows"
    )
    row_count: int = Field(description="Number of rows")
    duration_seconds: float = Field(description="Execution duration")
    bytes_processed: int | None = Field(default=None, description="Bytes processed")
    bytes_billed: int | None = Field(default=None, description="Bytes billed")
