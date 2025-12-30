"""CatalogAPI - Library API for data catalog operations.

This module provides the CatalogAPI class for programmatic access to
the data catalog (table metadata, ownership, quality, etc.).

Example:
    >>> from dli import CatalogAPI, ExecutionContext
    >>> ctx = ExecutionContext(server_url="https://basecamp.example.com")
    >>> api = CatalogAPI(context=ctx)
    >>> tables = api.list_tables("my-project.analytics")
    >>> detail = api.get("my-project.analytics.users")
"""

from __future__ import annotations

from typing import Any

from dli.core.catalog import TableDetail, TableInfo
from dli.core.client import BasecampClient, ServerConfig
from dli.models.common import ExecutionContext


class CatalogAPI:
    """Data Catalog browsing Library API.

    Provides programmatic access to catalog operations:
    - List tables/schemas/catalogs with filtering
    - Get table details (columns, ownership, quality, freshness)
    - Search tables by keyword

    All data comes from Basecamp Server API.

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations.

    Example:
        >>> from dli import CatalogAPI, ExecutionContext
        >>> ctx = ExecutionContext(server_url="http://basecamp:8080")
        >>> api = CatalogAPI(context=ctx)
        >>> tables = api.list_tables(project="my-project", limit=10)
        >>> for t in tables:
        ...     print(f"{t.name}: {t.row_count} rows")
    """

    def __init__(self, context: ExecutionContext | None = None) -> None:
        """Initialize CatalogAPI.

        Args:
            context: Execution context with settings. If None, creates
                     default context from environment variables.
        """
        self.context = context or ExecutionContext()
        self._client: BasecampClient | None = None

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"CatalogAPI(context={self.context!r})"

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
                mock_mode=self.context.mock_mode,
            )

        return self._client

    def list_tables(
        self,
        identifier: str | None = None,
        *,
        limit: int = 100,
    ) -> list[TableInfo]:
        """List tables/schemas/catalogs with implicit routing.

        Args:
            identifier: 1-4 part identifier for implicit routing.
                - None: List all projects
                - "project": List datasets in project
                - "project.dataset": List tables in dataset
                - "project.dataset.table": Get table (redirect to get)
            limit: Maximum number of results.

        Returns:
            List of TableInfo objects.
        """
        client = self._get_client()

        # Parse identifier for routing
        project = None
        dataset = None

        if identifier:
            parts = identifier.split(".")
            if len(parts) >= 1:
                project = parts[0]
            if len(parts) >= 2:
                dataset = parts[1]
            if len(parts) >= 3:
                # This is a specific table, return as single-item list
                detail = self.get(identifier)
                if detail:
                    return [
                        TableInfo(
                            name=detail.name,
                            engine=detail.engine,
                            owner=detail.ownership.owner,
                            team=detail.ownership.team,
                            tags=detail.tags,
                            row_count=None,  # Not available directly on TableDetail
                            last_updated=detail.freshness.last_updated,
                        )
                    ]
                return []

        response = client.catalog_list(
            project=project,
            dataset=dataset,
            limit=limit,
        )

        if not response.success or not response.data:
            return []

        # response.data is list[dict[str, Any]] for list operations
        data_list = (
            response.data if isinstance(response.data, list) else [response.data]
        )

        # Convert to TableInfo objects
        return [self._dict_to_table_info(item) for item in data_list]

    def get(self, table: str) -> TableDetail | None:
        """Get table details.

        Args:
            table: Table reference (project.dataset.table).

        Returns:
            TableDetail if found, None otherwise.
        """
        client = self._get_client()

        response = client.catalog_get(table)

        if not response.success or not response.data:
            return None

        # response.data is dict[str, Any] for get operation
        if isinstance(response.data, list):
            return None
        return self._dict_to_table_detail(response.data)

    def search(
        self,
        pattern: str,
        *,
        limit: int = 100,
    ) -> list[TableInfo]:
        """Search tables by pattern.

        Searches in table names, column names, descriptions, and tags.

        Args:
            pattern: Search pattern (substring match).
            limit: Maximum number of results.

        Returns:
            List of matching TableInfo objects.
        """
        client = self._get_client()

        response = client.catalog_search(pattern, limit=limit)

        if not response.success or not response.data:
            return []

        # response.data is list[dict[str, Any]] for search operations
        data_list = (
            response.data if isinstance(response.data, list) else [response.data]
        )
        return [self._dict_to_table_info(item) for item in data_list]

    def _dict_to_table_info(self, data: dict[str, Any]) -> TableInfo:
        """Convert dictionary to TableInfo model.

        Args:
            data: Dictionary from server response.

        Returns:
            TableInfo instance.
        """
        return TableInfo(
            name=data.get("name", ""),
            engine=data.get("engine", "unknown"),
            owner=data.get("owner"),
            team=data.get("team"),
            tags=data.get("tags", []),
            row_count=data.get("row_count"),
            last_updated=data.get("last_updated"),
        )

    def _dict_to_table_detail(self, data: dict[str, Any]) -> TableDetail:
        """Convert dictionary to TableDetail model.

        Args:
            data: Dictionary from server response.

        Returns:
            TableDetail instance.
        """
        from dli.core.catalog import (
            ColumnInfo,
            FreshnessInfo,
            OwnershipInfo,
            QualityInfo,
        )

        # Build column info list
        columns = []
        for col in data.get("columns", []):
            columns.append(
                ColumnInfo(
                    name=col.get("name", ""),
                    data_type=col.get("data_type", "UNKNOWN"),
                    description=col.get("description"),
                    is_pii=col.get("is_pii", False),
                    fill_rate=col.get("fill_rate"),
                    distinct_count=col.get("distinct_count"),
                )
            )

        # Build ownership info
        ownership_data = data.get("ownership", {})
        ownership = OwnershipInfo(
            owner=ownership_data.get("owner"),
            team=ownership_data.get("team"),
            stewards=ownership_data.get("stewards", []),
            consumers=ownership_data.get("consumers", []),
        )

        # Build freshness info
        freshness_data = data.get("freshness", {})
        freshness = FreshnessInfo(
            last_updated=freshness_data.get("last_updated"),
            avg_update_lag_hours=freshness_data.get("avg_update_lag_hours"),
            update_frequency=freshness_data.get("update_frequency"),
            is_stale=freshness_data.get("is_stale", False),
            stale_threshold_hours=freshness_data.get("stale_threshold_hours"),
        )

        # Build quality info
        quality_data = data.get("quality", {})
        quality = QualityInfo(
            score=quality_data.get("score"),
            total_tests=quality_data.get("total_tests", 0),
            passed_tests=quality_data.get("passed_tests", 0),
            failed_tests=quality_data.get("failed_tests", 0),
            warnings=quality_data.get("warnings", 0),
            recent_tests=[],  # Simplified for now
        )

        return TableDetail(
            name=data.get("name", ""),
            engine=data.get("engine", "unknown"),
            description=data.get("description"),
            tags=data.get("tags", []),
            basecamp_url=data.get("basecamp_url", ""),
            columns=columns,
            ownership=ownership,
            freshness=freshness,
            quality=quality,
            sample_queries=data.get("sample_queries", []),
        )


__all__ = ["CatalogAPI"]
