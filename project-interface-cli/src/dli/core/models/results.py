"""Result models for the DLI Core Engine.

This module contains execution and validation result models:
- ValidationResult: SQL validation result
- ExecutionResult: Single SQL execution result
- DatasetExecutionResult: Full dataset execution result (Pre -> Main -> Post)
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field

from dli.core.models.base import _utc_now


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
