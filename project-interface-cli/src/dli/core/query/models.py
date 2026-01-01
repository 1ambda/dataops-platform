"""Query execution metadata models.

This module provides data models for query execution metadata
retrieved from the Basecamp Server catalog API.

Example:
    >>> from dli.core.query.models import QueryScope, QueryState, QueryInfo
    >>> # Filter by scope
    >>> if scope == QueryScope.MY:
    ...     queries = get_my_queries()
    >>> # Check query state
    >>> for query in queries:
    ...     if query.is_running:
    ...         print(f"Query {query.query_id} is still running")
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

__all__ = [
    "AccountType",
    "QueryDetail",
    "QueryInfo",
    "QueryResources",
    "QueryScope",
    "QueryState",
    "TableReference",
]


class AccountType(str, Enum):
    """Type of account that executed the query.

    Attributes:
        PERSONAL: Individual user account (e.g., user@company.com).
        SYSTEM: Service/system account (e.g., airflow-prod, dbt-runner).
    """

    PERSONAL = "personal"
    SYSTEM = "system"


class QueryScope(str, Enum):
    """Scope for query listing.

    Determines which queries to include in list results.

    Attributes:
        MY: Queries executed by current authenticated user.
        SYSTEM: Queries from system/service accounts.
        USER: Queries from personal (non-system) accounts.
        ALL: All accessible queries regardless of account type.
    """

    MY = "my"
    SYSTEM = "system"
    USER = "user"
    ALL = "all"


class QueryState(str, Enum):
    """Query execution state.

    Represents the current status of a query execution.

    Attributes:
        PENDING: Query is queued, waiting to start.
        RUNNING: Query is currently executing.
        SUCCESS: Query completed successfully.
        FAILED: Query failed with an error.
        CANCELLED: Query was cancelled by user or system.
    """

    PENDING = "pending"
    RUNNING = "running"
    SUCCESS = "success"
    FAILED = "failed"
    CANCELLED = "cancelled"


class TableReference(BaseModel):
    """Reference to a table used in a query.

    Tracks which tables were accessed during query execution
    and the type of operation performed.

    Attributes:
        name: Fully qualified table name (e.g., project.dataset.table).
        operation: Type of table operation (read or write).
        alias: Table alias used in query (if any).
    """

    model_config = ConfigDict(frozen=True)

    name: str = Field(description="Fully qualified table name")
    operation: Literal["read", "write"] = Field(description="Table operation type")
    alias: str | None = Field(default=None, description="Table alias in query")


class QueryResources(BaseModel):
    """Resource usage metrics for a query.

    Tracks compute and storage resources consumed during execution.

    Attributes:
        bytes_processed: Number of bytes processed by the query.
        bytes_billed: Number of bytes billed for the query.
        slot_time_seconds: Total slot time consumed (BigQuery-specific).
        rows_affected: Number of rows affected by the query.
    """

    model_config = ConfigDict(frozen=True)

    bytes_processed: int | None = Field(default=None, description="Bytes processed")
    bytes_billed: int | None = Field(default=None, description="Bytes billed")
    slot_time_seconds: float | None = Field(default=None, description="Slot time")
    rows_affected: int | None = Field(default=None, description="Rows affected")


class QueryInfo(BaseModel):
    """Summary information about a query execution.

    Used in list views for quick overview of query executions.
    Contains essential metadata without full query details.

    Attributes:
        query_id: Unique query identifier.
        engine: Query engine (bigquery or trino).
        state: Current query execution state.
        account: Account that executed the query.
        account_type: Type of account (personal or system).
        started_at: Query start time.
        finished_at: Query finish time (if completed).
        duration_seconds: Query duration in seconds (if completed).
        tables_used_count: Number of tables referenced.
        error_message: Error message if query failed.
        tags: Query tags/labels.

    Example:
        >>> info = QueryInfo(
        ...     query_id="bq_job_abc123",
        ...     engine="bigquery",
        ...     state=QueryState.SUCCESS,
        ...     account="user@company.com",
        ...     account_type=AccountType.PERSONAL,
        ...     started_at=datetime.now(),
        ...     duration_seconds=12.5,
        ... )
        >>> if info.is_running:
        ...     print("Query is still running")
    """

    model_config = ConfigDict(frozen=True)

    query_id: str = Field(description="Unique query identifier")
    engine: Literal["bigquery", "trino"] = Field(description="Query engine")
    state: QueryState = Field(description="Current query state")
    account: str = Field(description="Account that executed the query")
    account_type: AccountType = Field(description="Type of account")
    started_at: datetime = Field(description="Query start time")
    finished_at: datetime | None = Field(default=None, description="Query finish time")
    duration_seconds: float | None = Field(
        default=None, description="Query duration in seconds"
    )
    tables_used_count: int = Field(default=0, description="Number of tables used")
    error_message: str | None = Field(
        default=None, description="Error message if failed"
    )
    tags: list[str] = Field(default_factory=list, description="Query tags/labels")

    @property
    def is_running(self) -> bool:
        """Check if query is currently running.

        Returns:
            True if query is in PENDING or RUNNING state.
        """
        return self.state in (QueryState.PENDING, QueryState.RUNNING)

    @property
    def is_terminal(self) -> bool:
        """Check if query has reached a terminal state.

        Returns:
            True if query is SUCCESS, FAILED, or CANCELLED.
        """
        return self.state in (
            QueryState.SUCCESS,
            QueryState.FAILED,
            QueryState.CANCELLED,
        )


class QueryDetail(BaseModel):
    """Detailed information about a query execution.

    Includes full metadata, resource usage, tables used, and query text.
    Used for detailed query inspection via the `show` command.

    Attributes:
        query_id: Unique query identifier.
        engine: Query engine (bigquery or trino).
        state: Current query execution state.
        account: Account that executed the query.
        account_type: Type of account (personal or system).
        started_at: Query start time.
        finished_at: Query finish time (if completed).
        duration_seconds: Query duration in seconds.
        queue_time_seconds: Time spent waiting in queue.
        bytes_processed: Bytes processed by the query.
        bytes_billed: Bytes billed for the query.
        slot_time_seconds: Slot time consumed (BigQuery).
        rows_affected: Rows affected by the query.
        tables_used: List of tables referenced by the query.
        tags: Query tags/labels.
        error_message: Error message if query failed.
        error_code: Error code if query failed.
        query_preview: Truncated query text (first N characters).
        query_text: Full query text (if requested).

    Example:
        >>> detail = api.get("bq_job_abc123")
        >>> print(f"Duration: {detail.duration_seconds}s")
        >>> print(f"Tables read: {[t.name for t in detail.tables_read]}")
        >>> print(f"Tables written: {[t.name for t in detail.tables_written]}")
    """

    model_config = ConfigDict(frozen=True)

    # Core identifiers
    query_id: str = Field(description="Unique query identifier")
    engine: Literal["bigquery", "trino"] = Field(description="Query engine")
    state: QueryState = Field(description="Current query state")
    account: str = Field(description="Account that executed the query")
    account_type: AccountType = Field(description="Type of account")

    # Timing
    started_at: datetime = Field(description="Query start time")
    finished_at: datetime | None = Field(default=None, description="Query finish time")
    duration_seconds: float | None = Field(default=None, description="Query duration")
    queue_time_seconds: float | None = Field(
        default=None, description="Time spent in queue"
    )

    # Resources
    bytes_processed: int | None = Field(default=None, description="Bytes processed")
    bytes_billed: int | None = Field(default=None, description="Bytes billed")
    slot_time_seconds: float | None = Field(default=None, description="Slot time")
    rows_affected: int | None = Field(default=None, description="Rows affected")

    # Tables
    tables_used: list[TableReference] = Field(
        default_factory=list, description="Tables used"
    )

    # Metadata
    tags: list[str] = Field(default_factory=list, description="Query tags/labels")
    error_message: str | None = Field(
        default=None, description="Error message if failed"
    )
    error_code: str | None = Field(default=None, description="Error code if failed")

    # Query text
    query_preview: str | None = Field(
        default=None, description="Truncated query text"
    )
    query_text: str | None = Field(
        default=None, description="Full query text (if requested)"
    )

    @property
    def tables_read(self) -> list[TableReference]:
        """Get tables read by the query.

        Returns:
            List of TableReference objects with operation='read'.
        """
        return [t for t in self.tables_used if t.operation == "read"]

    @property
    def tables_written(self) -> list[TableReference]:
        """Get tables written by the query.

        Returns:
            List of TableReference objects with operation='write'.
        """
        return [t for t in self.tables_used if t.operation == "write"]

    @property
    def is_running(self) -> bool:
        """Check if query is currently running.

        Returns:
            True if query is in PENDING or RUNNING state.
        """
        return self.state in (QueryState.PENDING, QueryState.RUNNING)

    @property
    def is_terminal(self) -> bool:
        """Check if query has reached a terminal state.

        Returns:
            True if query is SUCCESS, FAILED, or CANCELLED.
        """
        return self.state in (
            QueryState.SUCCESS,
            QueryState.FAILED,
            QueryState.CANCELLED,
        )
