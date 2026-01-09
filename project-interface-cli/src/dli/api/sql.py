"""SQL Snippet Management API.

This module provides the SqlAPI class for programmatic access to SQL snippets
stored on the Basecamp Server. Snippets are organized by project and optionally
by folder.

Example:
    >>> from dli import SqlAPI, ExecutionContext, ExecutionMode
    >>> ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
    >>> api = SqlAPI(context=ctx)
    >>> result = api.list_snippets(project="marketing")
    >>> for snippet in result.snippets:
    ...     print(f"{snippet.id}: {snippet.name}")
"""

from __future__ import annotations

from datetime import datetime

from dli.core.client import BasecampClient, ServerConfig
from dli.exceptions import (
    SqlAccessDeniedError,
    SqlProjectNotFoundError,
    SqlSnippetNotFoundError,
    SqlUpdateFailedError,
)
from dli.models.common import ExecutionContext, ExecutionMode
from dli.models.sql import (
    SqlDialect,
    SqlListResult,
    SqlSnippetDetail,
    SqlSnippetInfo,
    SqlUpdateResult,
)

__all__ = ["SqlAPI"]


class SqlAPI:
    """API for managing SQL snippets stored on Basecamp Server.

    Provides methods to list, retrieve, and update saved SQL snippets.
    Snippets are organized by project and optionally by folder.

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations.

    Example:
        >>> from dli import SqlAPI, ExecutionContext, ExecutionMode
        >>> ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
        >>> api = SqlAPI(context=ctx)
        >>> result = api.list_snippets(project="marketing")
        >>> for snippet in result.snippets:
        ...     print(f"{snippet.id}: {snippet.name}")
        >>>
        >>> # Get snippet details
        >>> detail = api.get(snippet_id=123, project="marketing")
        >>> print(detail.sql)
        >>>
        >>> # Update snippet
        >>> result = api.put(snippet_id=123, sql="SELECT * FROM users", project="marketing")
        >>> print(f"Updated at: {result.updated_at}")

    Attributes:
        context: Execution context with mode, server URL, etc.
    """

    def __init__(
        self,
        context: ExecutionContext | None = None,
        *,
        client: BasecampClient | None = None,  # DI for testing
    ) -> None:
        """Initialize SqlAPI.

        Args:
            context: Execution context. Defaults to ExecutionContext().
            client: Optional BasecampClient for dependency injection.
        """
        self.context = context or ExecutionContext()
        self._client = client

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"SqlAPI(context={self.context!r})"

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

    def _resolve_project_id(self, project: str | None) -> tuple[int, str]:
        """Resolve project name to ID.

        Args:
            project: Project name. If None, uses first available project.

        Returns:
            Tuple of (project_id, project_name).

        Raises:
            SqlProjectNotFoundError: If project is not found.
        """
        client = self._get_client()

        # Use provided project or try to get default from config
        project_name = project
        if project_name is None:
            # Try to get first project as default
            if self._is_mock_mode:
                return (1, "default")
            response = client.project_list(limit=1)
            if response.success and response.data:
                data = response.data
                if isinstance(data, dict):
                    projects = data.get("projects", [])
                    if projects:
                        return (projects[0]["id"], projects[0]["name"])
            raise SqlProjectNotFoundError(
                message="No default project configured",
                project="",
            )

        # Look up project by name
        response = client.project_get_by_name(project_name)
        if not response.success:
            raise SqlProjectNotFoundError(
                message=f"Project not found: {project_name}",
                project=project_name,
            )

        data = response.data or {}
        if isinstance(data, dict):
            return (data.get("id", 0), data.get("name", project_name))
        return (0, project_name)

    # =========================================================================
    # List Snippets
    # =========================================================================

    def list_snippets(
        self,
        project: str | None = None,
        *,
        folder: str | None = None,
        starred: bool = False,
        limit: int = 20,
        offset: int = 0,
    ) -> SqlListResult:
        """List SQL snippets with optional filters.

        Args:
            project: Filter by project name. If None, uses default project.
            folder: Filter by folder name.
            starred: If True, only return starred snippets.
            limit: Maximum number of results (default: 20).
            offset: Pagination offset (default: 0).

        Returns:
            SqlListResult with snippets and pagination info.

        Raises:
            SqlProjectNotFoundError: If specified project is not found.

        Example:
            >>> result = api.list_snippets(project="analytics", starred=True)
            >>> print(f"Found {result.total} starred snippets")
            >>> for s in result.snippets:
            ...     print(f"  {s.id}: {s.name}")
        """
        project_id, project_name = self._resolve_project_id(project)
        client = self._get_client()

        # TODO: Resolve folder name to ID if provided
        folder_id = None
        _ = folder  # Suppress unused variable warning

        response = client.sql_list_snippets(
            project_id=project_id,
            folder_id=folder_id,
            starred=starred if starred else None,
            limit=limit,
            offset=offset,
        )

        if not response.success:
            raise SqlProjectNotFoundError(
                message=f"Failed to list snippets: {response.error}",
                project=project or "default",
            )

        data = response.data or {}
        if not isinstance(data, dict):
            return SqlListResult(
                snippets=[],
                total=0,
                offset=offset,
                limit=limit,
            )

        snippets = []
        for item in data.get("snippets", []):
            snippets.append(
                SqlSnippetInfo(
                    id=item.get("id", 0),
                    name=item.get("name", ""),
                    project=item.get("projectName", project_name),
                    folder=item.get("folderName"),
                    dialect=SqlDialect(item.get("dialect", "bigquery").lower()),
                    starred=item.get("isStarred", False),
                    updated_at=datetime.fromisoformat(
                        item.get("updatedAt", "2026-01-01T00:00:00Z").replace(
                            "Z", "+00:00"
                        )
                    ),
                    updated_by=item.get("updatedBy", ""),
                )
            )

        return SqlListResult(
            snippets=snippets,
            total=data.get("total", len(snippets)),
            offset=data.get("offset", offset),
            limit=data.get("limit", limit),
        )

    # =========================================================================
    # Get Snippet
    # =========================================================================

    def get(self, snippet_id: int, project: str | None = None) -> SqlSnippetDetail:
        """Get a SQL snippet by ID.

        Args:
            snippet_id: The snippet ID to retrieve.
            project: Project name containing the snippet.

        Returns:
            SqlSnippetDetail with full snippet data including SQL content.

        Raises:
            SqlSnippetNotFoundError: If snippet is not found.
            SqlAccessDeniedError: If access is denied.

        Example:
            >>> detail = api.get(snippet_id=123, project="analytics")
            >>> print(f"-- {detail.name} ({detail.dialect.value})")
            >>> print(detail.sql)
        """
        project_id, project_name = self._resolve_project_id(project)
        client = self._get_client()

        response = client.sql_get_snippet(project_id, snippet_id)

        if not response.success:
            error = response.error or ""
            if "not found" in error.lower() or response.status_code == 404:
                raise SqlSnippetNotFoundError(
                    message=f"Snippet {snippet_id} not found",
                    snippet_id=snippet_id,
                )
            if "denied" in error.lower() or response.status_code == 403:
                raise SqlAccessDeniedError(
                    message=f"Access denied to snippet {snippet_id}",
                    snippet_id=snippet_id,
                )
            raise SqlSnippetNotFoundError(
                message=f"Failed to get snippet: {error}",
                snippet_id=snippet_id,
            )

        data = response.data or {}
        if not isinstance(data, dict):
            raise SqlSnippetNotFoundError(
                message=f"Invalid response for snippet {snippet_id}",
                snippet_id=snippet_id,
            )

        return SqlSnippetDetail(
            id=data.get("id", snippet_id),
            name=data.get("name", ""),
            project=data.get("projectName", project_name),
            folder=data.get("folderName"),
            dialect=SqlDialect(data.get("dialect", "bigquery").lower()),
            sql=data.get("sqlText", ""),
            starred=data.get("isStarred", False),
            created_at=datetime.fromisoformat(
                data.get("createdAt", "2026-01-01T00:00:00Z").replace("Z", "+00:00")
            ),
            updated_at=datetime.fromisoformat(
                data.get("updatedAt", "2026-01-01T00:00:00Z").replace("Z", "+00:00")
            ),
            created_by=data.get("createdBy", ""),
            updated_by=data.get("updatedBy", ""),
        )

    # =========================================================================
    # Update Snippet
    # =========================================================================

    def put(
        self, snippet_id: int, sql: str, project: str | None = None
    ) -> SqlUpdateResult:
        """Update a SQL snippet's content.

        Args:
            snippet_id: The snippet ID to update.
            sql: The new SQL content.
            project: Project name containing the snippet.

        Returns:
            SqlUpdateResult with update confirmation.

        Raises:
            SqlSnippetNotFoundError: If snippet is not found.
            SqlAccessDeniedError: If access is denied.
            SqlUpdateFailedError: If update fails.

        Example:
            >>> result = api.put(
            ...     snippet_id=123,
            ...     sql="SELECT * FROM users WHERE active = true",
            ...     project="analytics"
            ... )
            >>> print(f"Updated {result.name} at {result.updated_at}")
        """
        project_id, _ = self._resolve_project_id(project)
        client = self._get_client()

        response = client.sql_update_snippet(project_id, snippet_id, sql)

        if not response.success:
            error = response.error or ""
            if "not found" in error.lower() or response.status_code == 404:
                raise SqlSnippetNotFoundError(
                    message=f"Snippet {snippet_id} not found",
                    snippet_id=snippet_id,
                )
            if "denied" in error.lower() or response.status_code == 403:
                raise SqlAccessDeniedError(
                    message=f"Access denied to snippet {snippet_id}",
                    snippet_id=snippet_id,
                )
            raise SqlUpdateFailedError(
                message=f"Failed to update snippet {snippet_id}",
                snippet_id=snippet_id,
                reason=error,
            )

        data = response.data or {}
        if not isinstance(data, dict):
            raise SqlUpdateFailedError(
                message=f"Invalid response for snippet {snippet_id}",
                snippet_id=snippet_id,
                reason="Invalid response format",
            )

        return SqlUpdateResult(
            id=data.get("id", snippet_id),
            name=data.get("name", ""),
            updated_at=datetime.fromisoformat(
                data.get("updatedAt", "2026-01-01T00:00:00Z").replace("Z", "+00:00")
            ),
            updated_by=data.get("updatedBy", ""),
        )
