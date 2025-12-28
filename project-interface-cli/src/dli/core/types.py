"""Type definitions for the DLI Core Engine.

This module provides TypedDict definitions for better type safety and IDE support.
These types are used throughout the core engine for return values and parameters.

Usage:
    from dli.core.types import RenderResult, DryRunResult, TemplateContextDict
"""

from __future__ import annotations

from typing import Any, Callable, TypedDict


class RenderResult(TypedDict, total=False):
    """Result from DatasetService.render_sql() and format_sql() methods.

    Contains rendered SQL statements organized by execution phase.

    Attributes:
        pre: List of pre-statement SQL strings (optional)
        main: Main query SQL string (required)
        post: List of post-statement SQL strings (optional)

    Example:
        >>> result: RenderResult = {
        ...     "pre": ["DELETE FROM temp_table"],
        ...     "main": "INSERT INTO target SELECT * FROM source",
        ...     "post": ["ANALYZE TABLE target"]
        ... }
    """

    pre: list[str]
    main: str
    post: list[str]


class DryRunResult(TypedDict, total=False):
    """Result from BaseExecutor.dry_run() method.

    Contains validation status and query metadata from a dry run.
    The actual fields depend on the executor implementation.

    Attributes:
        valid: Whether the query is valid (optional)
        success: Whether the dry run succeeded (optional)
        bytes_processed: Estimated bytes to be processed (optional)
        bytes_processed_gb: Bytes processed in gigabytes (optional)
        estimated_cost_usd: Estimated query cost in USD (optional)
        error: Error message if validation failed (optional)
        error_message: Alternative error message field (optional)

    Example:
        >>> result: DryRunResult = {
        ...     "valid": True,
        ...     "bytes_processed": 1000000,
        ...     "bytes_processed_gb": 0.001,
        ...     "estimated_cost_usd": 0.000005
        ... }
    """

    valid: bool
    success: bool
    bytes_processed: int
    bytes_processed_gb: float
    estimated_cost_usd: float
    error: str
    error_message: str


class TemplateContextDict(TypedDict, total=False):
    """Dictionary exported by TemplateContext.to_dict() for Jinja2 rendering.

    Contains all variables and functions available in SQL templates.

    Date Variables:
        execution_date: Execution date in YYYY-MM-DD format
        ds: Same as execution_date (dbt compatible)
        ds_nodash: Date in YYYYMMDD format
        yesterday_ds: Yesterday's date
        tomorrow_ds: Tomorrow's date
        week_start_ds: Monday of the execution week
        month_start_ds: First day of the execution month
        year: Execution year as integer
        month: Execution month as integer (1-12)
        day: Execution day of month as integer (1-31)

    Functions:
        var: Get project variable
        env_var: Get environment variable
        date_add: Add days to date string
        date_sub: Subtract days from date string
        ref: Reference another dataset
        source: Reference a source table

    Example:
        >>> context = TemplateContext(execution_date=date(2025, 1, 15))
        >>> ctx_dict: TemplateContextDict = context.to_dict()
        >>> ctx_dict["ds"]
        '2025-01-15'
    """

    # Date variables
    execution_date: str
    ds: str
    ds_nodash: str
    yesterday_ds: str
    tomorrow_ds: str
    week_start_ds: str
    month_start_ds: str
    year: int
    month: int
    day: int

    # Functions (typed as Callable for reference, actual values are methods)
    var: Callable[[str, Any], Any]
    env_var: Callable[[str, str], str]
    date_add: Callable[[str, int], str]
    date_sub: Callable[[str, int], str]
    ref: Callable[[str], str]
    source: Callable[[str, str], str]


class ProjectDefaults(TypedDict, total=False):
    """Default settings from dli.yaml configuration.

    Contains project-wide default values for execution.

    Attributes:
        dialect: Default SQL dialect (e.g., 'trino', 'bigquery')
        timeout_seconds: Default query timeout in seconds
        retry_count: Default number of retries on failure
        retry_delay_seconds: Default delay between retries

    Example:
        >>> defaults: ProjectDefaults = {
        ...     "dialect": "trino",
        ...     "timeout_seconds": 3600,
        ...     "retry_count": 2,
        ...     "retry_delay_seconds": 60
        ... }
    """

    dialect: str
    timeout_seconds: int
    retry_count: int
    retry_delay_seconds: int


class EnvironmentConfig(TypedDict, total=False):
    """Environment-specific configuration from dli.yaml.

    Contains settings for a specific deployment environment.

    Attributes:
        connection_string: Database connection string
        catalog: Default catalog name
        schema: Default schema name
        variables: Environment-specific variables

    Example:
        >>> env_config: EnvironmentConfig = {
        ...     "connection_string": "trino://localhost:8080/iceberg",
        ...     "catalog": "iceberg",
        ...     "schema": "analytics"
        ... }
    """

    connection_string: str
    catalog: str
    schema: str
    variables: dict[str, Any]


# Type aliases for common patterns
SQLPhaseDict = dict[str, str | list[str]]
"""Type alias for SQL organized by phase (pre/main/post)."""

ParameterDict = dict[str, Any]
"""Type alias for template parameter dictionaries."""

ReferenceDict = dict[str, str]
"""Type alias for dataset reference mappings."""
