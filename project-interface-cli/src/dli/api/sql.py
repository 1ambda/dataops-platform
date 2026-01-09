"""SQL Worksheet Management API.

This module provides the SqlAPI class for programmatic access to SQL worksheets
stored on the Basecamp Server. Worksheets are organized by team and optionally
by folder.

Example:
    >>> from dli import SqlAPI, ExecutionContext, ExecutionMode
    >>> ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
    >>> api = SqlAPI(context=ctx)
    >>> result = api.list_worksheets(team="marketing")
    >>> for worksheet in result.worksheets:
    ...     print(f"{worksheet.id}: {worksheet.name}")
"""

from __future__ import annotations

from datetime import datetime

from dli.core.client import BasecampClient, ServerConfig
from dli.exceptions import (
    SqlAccessDeniedError,
    SqlTeamNotFoundError,
    SqlWorksheetNotFoundError,
    SqlUpdateFailedError,
)
from dli.models.common import ExecutionContext, ExecutionMode
from dli.models.sql import (
    SqlDialect,
    SqlListResult,
    SqlWorksheetDetail,
    SqlWorksheetInfo,
    SqlUpdateResult,
)

__all__ = ["SqlAPI"]


class SqlAPI:
    """API for managing SQL worksheets stored on Basecamp Server.

    Provides methods to list, retrieve, and update saved SQL worksheets.
    Worksheets are organized by team and optionally by folder.

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations.

    Example:
        >>> from dli import SqlAPI, ExecutionContext, ExecutionMode
        >>> ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
        >>> api = SqlAPI(context=ctx)
        >>> result = api.list_worksheets(team="marketing")
        >>> for worksheet in result.worksheets:
        ...     print(f"{worksheet.id}: {worksheet.name}")
        >>>
        >>> # Get worksheet details
        >>> detail = api.get(worksheet_id=123, team="marketing")
        >>> print(detail.sql)
        >>>
        >>> # Update worksheet
        >>> result = api.put(worksheet_id=123, sql="SELECT * FROM users", team="marketing")
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

    def _resolve_team_id(self, team: str | None) -> tuple[int, str]:
        """Resolve team name to ID.

        Args:
            team: Team name. If None, uses first available team.

        Returns:
            Tuple of (team_id, team_name).

        Raises:
            SqlTeamNotFoundError: If team is not found.
        """
        client = self._get_client()

        # Use provided team or try to get default from config
        team_name = team
        if team_name is None:
            # Try to get first team as default
            if self._is_mock_mode:
                return (1, "default")
            response = client.team_list(limit=1)
            if response.success and response.data:
                data = response.data
                if isinstance(data, dict):
                    teams = data.get("teams", [])
                    if teams:
                        return (teams[0]["id"], teams[0]["name"])
            raise SqlTeamNotFoundError(
                message="No default team configured",
                team="",
            )

        # Look up team by name
        response = client.team_get_by_name(team_name)
        if not response.success:
            raise SqlTeamNotFoundError(
                message=f"Team not found: {team_name}",
                team=team_name,
            )

        data = response.data or {}
        if isinstance(data, dict):
            return (data.get("id", 0), data.get("name", team_name))
        return (0, team_name)

    # =========================================================================
    # List Worksheets
    # =========================================================================

    def list_worksheets(
        self,
        team: str | None = None,
        *,
        folder: str | None = None,
        starred: bool = False,
        limit: int = 20,
        offset: int = 0,
    ) -> SqlListResult:
        """List SQL worksheets with optional filters.

        Args:
            team: Filter by team name. If None, uses default team.
            folder: Filter by folder name.
            starred: If True, only return starred worksheets.
            limit: Maximum number of results (default: 20).
            offset: Pagination offset (default: 0).

        Returns:
            SqlListResult with worksheets and pagination info.

        Raises:
            SqlTeamNotFoundError: If specified team is not found.

        Example:
            >>> result = api.list_worksheets(team="analytics", starred=True)
            >>> print(f"Found {result.total} starred worksheets")
            >>> for w in result.worksheets:
            ...     print(f"  {w.id}: {w.name}")
        """
        team_id, team_name = self._resolve_team_id(team)
        client = self._get_client()

        # TODO: Resolve folder name to ID if provided
        folder_id = None
        _ = folder  # Suppress unused variable warning

        response = client.sql_list_worksheets(
            team_id=team_id,
            folder_id=folder_id,
            starred=starred if starred else None,
            limit=limit,
            offset=offset,
        )

        if not response.success:
            raise SqlTeamNotFoundError(
                message=f"Failed to list worksheets: {response.error}",
                team=team or "default",
            )

        data = response.data or {}
        if not isinstance(data, dict):
            return SqlListResult(
                worksheets=[],
                total=0,
                offset=offset,
                limit=limit,
            )

        worksheets = []
        for item in data.get("worksheets", []):
            worksheets.append(
                SqlWorksheetInfo(
                    id=item.get("id", 0),
                    name=item.get("name", ""),
                    team=item.get("teamName", team_name),
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
            worksheets=worksheets,
            total=data.get("total", len(worksheets)),
            offset=data.get("offset", offset),
            limit=data.get("limit", limit),
        )

    # =========================================================================
    # Get Worksheet
    # =========================================================================

    def get(self, worksheet_id: int, team: str | None = None) -> SqlWorksheetDetail:
        """Get a SQL worksheet by ID.

        Args:
            worksheet_id: The worksheet ID to retrieve.
            team: Team name containing the worksheet.

        Returns:
            SqlWorksheetDetail with full worksheet data including SQL content.

        Raises:
            SqlWorksheetNotFoundError: If worksheet is not found.
            SqlAccessDeniedError: If access is denied.

        Example:
            >>> detail = api.get(worksheet_id=123, team="analytics")
            >>> print(f"-- {detail.name} ({detail.dialect.value})")
            >>> print(detail.sql)
        """
        team_id, team_name = self._resolve_team_id(team)
        client = self._get_client()

        response = client.sql_get_worksheet(team_id, worksheet_id)

        if not response.success:
            error = response.error or ""
            if "not found" in error.lower() or response.status_code == 404:
                raise SqlWorksheetNotFoundError(
                    message=f"Worksheet {worksheet_id} not found",
                    worksheet_id=worksheet_id,
                )
            if "denied" in error.lower() or response.status_code == 403:
                raise SqlAccessDeniedError(
                    message=f"Access denied to worksheet {worksheet_id}",
                    worksheet_id=worksheet_id,
                )
            raise SqlWorksheetNotFoundError(
                message=f"Failed to get worksheet: {error}",
                worksheet_id=worksheet_id,
            )

        data = response.data or {}
        if not isinstance(data, dict):
            raise SqlWorksheetNotFoundError(
                message=f"Invalid response for worksheet {worksheet_id}",
                worksheet_id=worksheet_id,
            )

        return SqlWorksheetDetail(
            id=data.get("id", worksheet_id),
            name=data.get("name", ""),
            team=data.get("teamName", team_name),
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
    # Update Worksheet
    # =========================================================================

    def put(
        self, worksheet_id: int, sql: str, team: str | None = None
    ) -> SqlUpdateResult:
        """Update a SQL worksheet's content.

        Args:
            worksheet_id: The worksheet ID to update.
            sql: The new SQL content.
            team: Team name containing the worksheet.

        Returns:
            SqlUpdateResult with update confirmation.

        Raises:
            SqlWorksheetNotFoundError: If worksheet is not found.
            SqlAccessDeniedError: If access is denied.
            SqlUpdateFailedError: If update fails.

        Example:
            >>> result = api.put(
            ...     worksheet_id=123,
            ...     sql="SELECT * FROM users WHERE active = true",
            ...     team="analytics"
            ... )
            >>> print(f"Updated {result.name} at {result.updated_at}")
        """
        team_id, _ = self._resolve_team_id(team)
        client = self._get_client()

        response = client.sql_update_worksheet(team_id, worksheet_id, sql)

        if not response.success:
            error = response.error or ""
            if "not found" in error.lower() or response.status_code == 404:
                raise SqlWorksheetNotFoundError(
                    message=f"Worksheet {worksheet_id} not found",
                    worksheet_id=worksheet_id,
                )
            if "denied" in error.lower() or response.status_code == 403:
                raise SqlAccessDeniedError(
                    message=f"Access denied to worksheet {worksheet_id}",
                    worksheet_id=worksheet_id,
                )
            raise SqlUpdateFailedError(
                message=f"Failed to update worksheet {worksheet_id}",
                worksheet_id=worksheet_id,
                reason=error,
            )

        data = response.data or {}
        if not isinstance(data, dict):
            raise SqlUpdateFailedError(
                message=f"Invalid response for worksheet {worksheet_id}",
                worksheet_id=worksheet_id,
                reason="Invalid response format",
            )

        return SqlUpdateResult(
            id=data.get("id", worksheet_id),
            name=data.get("name", ""),
            updated_at=datetime.fromisoformat(
                data.get("updatedAt", "2026-01-01T00:00:00Z").replace("Z", "+00:00")
            ),
            updated_by=data.get("updatedBy", ""),
        )
