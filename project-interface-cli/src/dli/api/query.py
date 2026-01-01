"""QueryAPI - Library API for query execution metadata.

This module provides the QueryAPI class for programmatic access to
query execution history and metadata from the Basecamp Server catalog.

Example:
    >>> from dli import QueryAPI, ExecutionContext, ExecutionMode
    >>> from dli.core.query.models import QueryScope, QueryState
    >>> ctx = ExecutionContext(
    ...     execution_mode=ExecutionMode.SERVER,
    ...     server_url="http://basecamp:8080",
    ... )
    >>> api = QueryAPI(context=ctx)
    >>> # List my queries (default scope)
    >>> result = api.list_queries(limit=20)
    >>> for query in result.queries:
    ...     print(f"{query.query_id}: {query.state}")
"""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from dli.core.client import BasecampClient, ServerConfig
from dli.core.query.models import (
    QueryDetail,
    QueryInfo,
    QueryScope,
    QueryState,
)
from dli.exceptions import (
    ConfigurationError,
    ErrorCode,
    QueryAccessDeniedError,
    QueryCancelError,
    QueryInvalidFilterError,
    QueryNotFoundError,
)
from dli.models.common import ExecutionContext, ExecutionMode, ResultStatus
from dli.models.query import (
    QueryCancelResult,
    QueryDetailResult,
    QueryListResult,
)

__all__ = ["QueryAPI"]


class QueryAPI:
    """Library API for query execution metadata.

    Provides programmatic access to query execution history and metadata
    from the Basecamp Server catalog. All data comes from server API.

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations.

    Example:
        >>> from dli import QueryAPI, ExecutionContext, ExecutionMode
        >>> from dli.core.query.models import QueryScope
        >>> ctx = ExecutionContext(
        ...     execution_mode=ExecutionMode.SERVER,
        ...     server_url="http://basecamp:8080",
        ... )
        >>> api = QueryAPI(context=ctx)
        >>> # List my queries (default scope)
        >>> result = api.list_queries(limit=20)
        >>> for query in result.queries:
        ...     print(f"{query.query_id}: {query.state}")
        >>>
        >>> # List system queries with account filter
        >>> result = api.list_queries(
        ...     scope=QueryScope.SYSTEM,
        ...     account_keyword="airflow",
        ...     status=QueryState.FAILED,
        ... )

    Attributes:
        context: Execution context with mode, server URL, etc.
    """

    def __init__(
        self,
        context: ExecutionContext | None = None,
        *,
        client: BasecampClient | None = None,  # DI for testing
    ) -> None:
        """Initialize QueryAPI.

        Args:
            context: Execution context. Defaults to ExecutionContext().
            client: Optional BasecampClient for dependency injection.
        """
        self.context = context or ExecutionContext()
        self._client = client

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"QueryAPI(context={self.context!r})"

    @property
    def _is_mock_mode(self) -> bool:
        """Check if running in mock mode."""
        return self.context.execution_mode == ExecutionMode.MOCK

    def _get_client(self) -> BasecampClient:
        """Get or create BasecampClient instance (lazy initialization).

        Returns:
            BasecampClient instance.
        """
        if self._client is None:
            config = ServerConfig(
                url=self.context.server_url or "http://localhost:8081",
            )
            self._client = BasecampClient(
                config=config,
                mock_mode=self._is_mock_mode,
            )
        return self._client

    # =========================================================================
    # List Queries (Unified Method)
    # =========================================================================

    def list_queries(
        self,
        *,
        scope: QueryScope = QueryScope.MY,
        account_keyword: str | None = None,
        sql_pattern: str | None = None,
        status: QueryState | None = None,
        tags: list[str] | None = None,
        engine: Literal["bigquery", "trino"] | None = None,
        since: datetime | str | None = None,
        until: datetime | str | None = None,
        limit: int = 10,
        offset: int = 0,
    ) -> QueryListResult:
        """List queries with flexible scope-based filtering.

        Args:
            scope: Query scope - MY, SYSTEM, USER, or ALL. Defaults to MY.
            account_keyword: Filter by account name (optional, searches
                account name only).
            sql_pattern: Filter by SQL query text content (optional).
            status: Filter by query state.
            tags: Filter by tags (AND logic).
            engine: Filter by query engine.
            since: Start time filter (datetime or relative string like "1h", "7d").
                   Defaults to "24h".
            until: End time filter.
            limit: Maximum number of results (default: 10).
            offset: Pagination offset (default: 0).

        Returns:
            QueryListResult with list of QueryInfo objects.

        Example:
            >>> # My failed queries (default scope=MY)
            >>> result = api.list_queries(status=QueryState.FAILED, since="24h")
            >>> for q in result.queries:
            ...     print(f"{q.query_id}: {q.error_message}")

            >>> # System queries for specific account
            >>> result = api.list_queries(
            ...     scope=QueryScope.SYSTEM,
            ...     account_keyword="airflow",
            ... )

            >>> # All queries with SQL pattern
            >>> result = api.list_queries(
            ...     scope=QueryScope.ALL,
            ...     sql_pattern="SELECT * FROM users",
            ... )

            >>> # Combine account and SQL filters
            >>> result = api.list_queries(
            ...     scope=QueryScope.SYSTEM,
            ...     account_keyword="airflow",
            ...     sql_pattern="daily_metrics",
            ... )
        """
        client = self._get_client()
        response = client.query_list(
            scope=scope.value,
            account_keyword=account_keyword,
            sql_pattern=sql_pattern,
            state=status.value if status else None,
            tags=tags,
            engine=engine,
            since=since.isoformat() if isinstance(since, datetime) else since,
            until=until.isoformat() if isinstance(until, datetime) else until,
            limit=limit,
            offset=offset,
        )

        if not response.success:
            return QueryListResult(
                queries=[],
                total_count=0,
                has_more=False,
                status=ResultStatus.FAILURE,
                error_message=response.error,
            )

        data = response.data or {}
        if isinstance(data, list):
            # Handle case where data is a list directly
            queries = [QueryInfo.model_validate(q) for q in data]
            return QueryListResult(
                queries=queries,
                total_count=len(queries),
                has_more=len(queries) >= limit,
                status=ResultStatus.SUCCESS,
            )

        queries = [QueryInfo.model_validate(q) for q in data.get("queries", [])]

        return QueryListResult(
            queries=queries,
            total_count=data.get("total_count", len(queries)),
            has_more=data.get("has_more", False),
            status=ResultStatus.SUCCESS,
        )

    # =========================================================================
    # Query Detail
    # =========================================================================

    def get(self, query_id: str, *, include_full_query: bool = False) -> QueryDetail:
        """Get detailed metadata for a specific query.

        Args:
            query_id: The query ID to retrieve.
            include_full_query: Include complete query text (not truncated).

        Returns:
            QueryDetail with full query metadata.

        Raises:
            QueryNotFoundError: If query ID not found.
            QueryAccessDeniedError: If access denied.

        Example:
            >>> detail = api.get("bq_job_abc123")
            >>> print(f"Duration: {detail.duration_seconds}s")
            >>> print(f"Tables: {[t.name for t in detail.tables_used]}")
        """
        client = self._get_client()
        response = client.query_get(
            query_id=query_id,
            include_full_query=include_full_query,
        )

        if not response.success:
            if response.status_code == 404:
                raise QueryNotFoundError(
                    message=f"Query '{query_id}' not found",
                    code=ErrorCode.QUERY_NOT_FOUND,
                    query_id=query_id,
                )
            if response.status_code == 403:
                raise QueryAccessDeniedError(
                    message=f"Access denied to query '{query_id}'",
                    code=ErrorCode.QUERY_ACCESS_DENIED,
                    query_id=query_id,
                )
            raise ConfigurationError(
                message=response.error or "Failed to get query",
                code=ErrorCode.QUERY_SERVER_ERROR,
            )

        return QueryDetail.model_validate(response.data)

    def get_result(
        self, query_id: str, *, include_full_query: bool = False
    ) -> QueryDetailResult:
        """Get detailed metadata for a specific query (returns result object).

        This method is similar to get() but returns a QueryDetailResult
        instead of raising exceptions. Useful for error handling in CLI.

        Args:
            query_id: The query ID to retrieve.
            include_full_query: Include complete query text (not truncated).

        Returns:
            QueryDetailResult with query detail or error message.

        Example:
            >>> result = api.get_result("bq_job_abc123")
            >>> if result.status == ResultStatus.SUCCESS:
            ...     print(f"Duration: {result.query.duration_seconds}s")
            >>> else:
            ...     print(f"Error: {result.error_message}")
        """
        try:
            detail = self.get(query_id, include_full_query=include_full_query)
            return QueryDetailResult(
                query=detail,
                status=ResultStatus.SUCCESS,
            )
        except (QueryNotFoundError, QueryAccessDeniedError, ConfigurationError) as e:
            return QueryDetailResult(
                query=None,
                status=ResultStatus.FAILURE,
                error_message=str(e),
            )

    # =========================================================================
    # Query Cancellation
    # =========================================================================

    def cancel(
        self,
        query_id: str | None = None,
        *,
        user: str | None = None,
        dry_run: bool = False,
    ) -> QueryCancelResult:
        """Cancel running query(s).

        Must provide either query_id OR user, not both.

        Args:
            query_id: Specific query ID to cancel.
            user: Account name to cancel all running queries for.
            dry_run: If True, return what would be cancelled without executing.

        Returns:
            QueryCancelResult with cancelled query details.

        Raises:
            QueryNotFoundError: If query ID not found.
            QueryAccessDeniedError: If access denied.
            QueryInvalidFilterError: If both query_id and user are provided,
                or neither is provided.

        Example:
            >>> # Cancel specific query
            >>> result = api.cancel(query_id="bq_job_abc123")
            >>> print(f"Cancelled: {result.cancelled_count}")

            >>> # Cancel all queries for an account
            >>> result = api.cancel(user="airflow-prod", dry_run=True)
            >>> print(f"Would cancel {len(result.queries)} queries")
        """
        # Validation
        if query_id and user:
            raise QueryInvalidFilterError(
                message="Cannot specify both query_id and user",
                code=ErrorCode.QUERY_INVALID_FILTER,
            )
        if not query_id and not user:
            raise QueryInvalidFilterError(
                message="Must specify either query_id or user",
                code=ErrorCode.QUERY_INVALID_FILTER,
            )

        client = self._get_client()
        response = client.query_cancel(
            query_id=query_id,
            user=user,
            dry_run=dry_run,
        )

        if not response.success:
            if response.status_code == 404:
                raise QueryNotFoundError(
                    message=f"Query '{query_id}' not found",
                    code=ErrorCode.QUERY_NOT_FOUND,
                    query_id=query_id or "",
                )
            if response.status_code == 403:
                raise QueryAccessDeniedError(
                    message="Access denied to cancel query",
                    code=ErrorCode.QUERY_ACCESS_DENIED,
                    query_id=query_id or "",
                )
            raise QueryCancelError(
                message=response.error or "Failed to cancel query",
                code=ErrorCode.QUERY_CANCEL_FAILED,
                query_id=query_id or "",
            )

        data = response.data or {}
        if isinstance(data, list):
            # Handle case where data is a list directly
            queries = [QueryInfo.model_validate(q) for q in data]
            return QueryCancelResult(
                cancelled_count=len(queries) if not dry_run else 0,
                queries=queries,
                dry_run=dry_run,
                status=ResultStatus.SUCCESS,
            )

        queries = [QueryInfo.model_validate(q) for q in data.get("queries", [])]

        return QueryCancelResult(
            cancelled_count=data.get("cancelled_count", len(queries)),
            queries=queries,
            dry_run=dry_run,
            status=ResultStatus.SUCCESS,
        )
