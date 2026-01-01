"""Query API result models.

This module provides result models for QueryAPI operations.

Example:
    >>> from dli.models.query import QueryListResult, QueryCancelResult
    >>> result = api.list_queries(scope=QueryScope.MY)
    >>> print(f"Found {result.total_count} queries")
    >>> for query in result.queries:
    ...     print(f"{query.query_id}: {query.state}")
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field

from dli.core.query.models import QueryDetail, QueryInfo
from dli.models.common import ResultStatus

__all__ = [
    "QueryCancelResult",
    "QueryDetailResult",
    "QueryListResult",
]


class QueryListResult(BaseModel):
    """Result of query list operations.

    Contains a list of query summaries with pagination metadata.

    Attributes:
        queries: List of QueryInfo objects.
        total_count: Total count of matching queries (across all pages).
        has_more: Whether more results are available.
        status: Operation result status.
        error_message: Error message if operation failed.

    Example:
        >>> result = api.list_queries(scope=QueryScope.MY, limit=10)
        >>> print(f"Showing {len(result.queries)} of {result.total_count}")
        >>> if result.has_more:
        ...     print("More results available")
    """

    model_config = ConfigDict(frozen=True)

    queries: list[QueryInfo] = Field(
        default_factory=list, description="List of queries"
    )
    total_count: int = Field(description="Total count of matching queries")
    has_more: bool = Field(default=False, description="Whether more results available")
    status: ResultStatus = Field(description="Operation status")
    error_message: str | None = Field(
        default=None, description="Error message if failed"
    )


class QueryDetailResult(BaseModel):
    """Result of query detail operation.

    Contains detailed information about a single query execution.

    Attributes:
        query: QueryDetail object with full metadata.
        status: Operation result status.
        error_message: Error message if operation failed.

    Example:
        >>> result = api.get("bq_job_abc123")
        >>> if result.status == ResultStatus.SUCCESS:
        ...     print(f"Duration: {result.query.duration_seconds}s")
    """

    model_config = ConfigDict(frozen=True)

    query: QueryDetail | None = Field(default=None, description="Query detail")
    status: ResultStatus = Field(description="Operation status")
    error_message: str | None = Field(
        default=None, description="Error message if failed"
    )


class QueryCancelResult(BaseModel):
    """Result of query cancellation operation.

    Contains information about cancelled queries.

    Attributes:
        cancelled_count: Number of queries that were cancelled.
        queries: List of cancelled query summaries.
        dry_run: Whether this was a dry run (no actual cancellation).
        status: Operation result status.
        error_message: Error message if operation failed.

    Example:
        >>> # Cancel all queries for an account
        >>> result = api.cancel(user="airflow-prod", dry_run=True)
        >>> print(f"Would cancel {len(result.queries)} queries")
        >>>
        >>> # Actually cancel
        >>> result = api.cancel(user="airflow-prod")
        >>> print(f"Cancelled {result.cancelled_count} queries")
    """

    model_config = ConfigDict(frozen=True)

    cancelled_count: int = Field(description="Number of queries cancelled")
    queries: list[QueryInfo] = Field(
        default_factory=list, description="Cancelled queries"
    )
    dry_run: bool = Field(default=False, description="Whether this was a dry run")
    status: ResultStatus = Field(description="Operation status")
    error_message: str | None = Field(
        default=None, description="Error message if failed"
    )
