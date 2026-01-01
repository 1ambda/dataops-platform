"""Run command result models.

This module provides public data models for the Run API results.

Example:
    >>> from dli.models.run import OutputFormat, RunResult, ExecutionPlan
    >>> result = api.run(sql_path=Path("query.sql"), output_path=Path("out.csv"))
    >>> print(f"Rows: {result.row_count}")
"""

from __future__ import annotations

from enum import Enum
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field

from dli.models.common import ExecutionMode, ResultStatus

__all__ = [
    "ExecutionPlan",
    "OutputFormat",
    "RunResult",
]


class OutputFormat(str, Enum):
    """Output format for run results.

    Attributes:
        CSV: Comma-separated values (default).
        TSV: Tab-separated values.
        JSON: JSON Lines format (one object per line).
    """

    CSV = "csv"
    TSV = "tsv"
    JSON = "json"


class RunResult(BaseModel):
    """Result of SQL execution.

    Returned by RunAPI.run() method.

    Attributes:
        status: Execution status.
        sql_path: Input SQL file path.
        output_path: Output file path.
        output_format: Output format used.
        row_count: Number of rows returned.
        duration_seconds: Execution duration in seconds.
        execution_mode: Execution mode used (LOCAL, SERVER, MOCK).
        rendered_sql: Rendered SQL after parameter substitution.
        bytes_processed: Bytes processed (if available).
        bytes_billed: Bytes billed (if available).

    Example:
        >>> result = api.run(
        ...     sql_path=Path("query.sql"),
        ...     output_path=Path("results.csv"),
        ... )
        >>> print(f"Rows: {result.row_count}, Duration: {result.duration_seconds}s")
    """

    model_config = ConfigDict(frozen=True)

    status: ResultStatus = Field(description="Execution status")
    sql_path: Path = Field(description="Input SQL file path")
    output_path: Path = Field(description="Output file path")
    output_format: OutputFormat = Field(description="Output format used")
    row_count: int = Field(description="Number of rows returned")
    duration_seconds: float = Field(description="Execution duration in seconds")
    execution_mode: ExecutionMode = Field(description="Execution mode used")
    rendered_sql: str = Field(description="Rendered SQL after parameter substitution")
    bytes_processed: int | None = Field(default=None, description="Bytes processed")
    bytes_billed: int | None = Field(default=None, description="Bytes billed")

    @property
    def is_success(self) -> bool:
        """Check if execution was successful."""
        return self.status == ResultStatus.SUCCESS


class ExecutionPlan(BaseModel):
    """Execution plan for dry run.

    Returned by RunAPI.dry_run() method. Shows what would be executed
    without actually running the query.

    Attributes:
        sql_path: Input SQL file path.
        output_path: Output file path.
        output_format: Output format.
        dialect: SQL dialect.
        execution_mode: Resolved execution mode.
        rendered_sql: Rendered SQL after parameter substitution.
        parameters: Parameters used for rendering.
        is_valid: Whether execution plan is valid.
        validation_error: Validation error message if any.

    Example:
        >>> plan = api.dry_run(
        ...     sql_path=Path("query.sql"),
        ...     output_path=Path("results.csv"),
        ... )
        >>> print(f"Mode: {plan.execution_mode}, Valid: {plan.is_valid}")
    """

    model_config = ConfigDict(frozen=True)

    sql_path: Path = Field(description="Input SQL file path")
    output_path: Path = Field(description="Output file path")
    output_format: OutputFormat = Field(description="Output format")
    dialect: str = Field(description="SQL dialect")
    execution_mode: ExecutionMode = Field(description="Resolved execution mode")
    rendered_sql: str = Field(description="Rendered SQL")
    parameters: dict[str, str] = Field(
        default_factory=dict, description="Parameters used"
    )
    is_valid: bool = Field(description="Whether execution plan is valid")
    validation_error: str | None = Field(
        default=None, description="Validation error if any"
    )
