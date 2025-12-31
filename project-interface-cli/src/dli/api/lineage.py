"""LineageAPI - Library API for Lineage operations.

This module provides the LineageAPI class which wraps the BasecampClient
for programmatic access to lineage operations including upstream/downstream
dependency analysis.

Example:
    >>> from dli import LineageAPI, ExecutionContext, ExecutionMode
    >>> ctx = ExecutionContext(
    ...     execution_mode=ExecutionMode.SERVER,
    ...     server_url="http://basecamp:8080",
    ... )
    >>> api = LineageAPI(context=ctx)
    >>> result = api.get_lineage("iceberg.analytics.daily_clicks")
    >>> print(f"Upstream: {result.total_upstream}, Downstream: {result.total_downstream}")

    >>> # Get upstream dependencies only
    >>> upstream = api.get_upstream("iceberg.analytics.daily_clicks", depth=2)
    >>> for node in upstream.nodes:
    ...     print(f"{node.name} (depth: {node.depth})")
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from dli.core.client import BasecampClient, ServerConfig
from dli.core.lineage import (
    LineageDirection,
    LineageDirectionType,
    LineageEdge,
    LineageNode,
    LineageResult,
)
from dli.exceptions import (
    ConfigurationError,
    ErrorCode,
    LineageError,
    LineageNotFoundError,
    LineageTimeoutError,
)
from dli.models.common import ExecutionContext, ExecutionMode

if TYPE_CHECKING:
    pass

__all__ = ["LineageAPI"]


class LineageAPI:
    """Library API for lineage operations.

    Provides programmatic access to lineage operations including
    upstream/downstream dependency analysis for datasets and metrics.

    This class follows the same patterns as WorkflowAPI and DatasetAPI:
    - Facade pattern over BasecampClient
    - Full mock mode support for testing
    - Dependency injection for client

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations (standard pattern for Kubernetes Airflow).

    Example:
        >>> from dli import LineageAPI, ExecutionContext, ExecutionMode
        >>> ctx = ExecutionContext(
        ...     execution_mode=ExecutionMode.SERVER,
        ...     server_url="http://basecamp:8080",
        ... )
        >>> api = LineageAPI(context=ctx)
        >>> result = api.get_lineage("iceberg.analytics.daily_clicks")
        >>> print(f"Upstream: {result.total_upstream}")

    Attributes:
        context: Execution context with mode, server URL, project path, etc.
    """

    def __init__(
        self,
        context: ExecutionContext | None = None,
        *,
        client: BasecampClient | None = None,
    ) -> None:
        """Initialize LineageAPI.

        Args:
            context: Execution context. Defaults to ExecutionContext().
            client: Optional BasecampClient for dependency injection.
        """
        self.context = context or ExecutionContext()
        self._client = client

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"LineageAPI(context={self.context!r})"

    @property
    def _is_mock_mode(self) -> bool:
        """Check if running in mock mode."""
        return self.context.execution_mode == ExecutionMode.MOCK

    def _get_client(self) -> BasecampClient:
        """Get or create BasecampClient instance (lazy initialization).

        Returns:
            BasecampClient instance.

        Raises:
            ConfigurationError: If server_url is required but not set.
        """
        if self._client is not None:
            return self._client

        if self._is_mock_mode:
            config = ServerConfig(url="http://mock-server")
            self._client = BasecampClient(config, mock_mode=True)
            return self._client

        if not self.context.server_url:
            raise ConfigurationError(
                message="server_url required for SERVER mode",
                code=ErrorCode.CONFIG_INVALID,
            )

        config = ServerConfig(
            url=self.context.server_url,
            api_key=self.context.api_token,
        )
        self._client = BasecampClient(config, mock_mode=False)
        return self._client

    # =========================================================================
    # Lineage Query
    # =========================================================================

    def get_lineage(
        self,
        resource_name: str,
        direction: LineageDirectionType = "both",
        depth: int = -1,
    ) -> LineageResult:
        """Get full lineage for a resource.

        Queries the server for lineage information about the specified
        resource, including upstream dependencies and downstream dependents.

        Args:
            resource_name: Fully qualified resource name
                (e.g., 'iceberg.analytics.daily_clicks')
            direction: Direction of lineage to retrieve:
                - 'upstream': What this resource depends on
                - 'downstream': What depends on this resource
                - 'both': Both directions (default)
            depth: Maximum traversal depth (-1 for unlimited)

        Returns:
            LineageResult containing nodes and edges.

        Raises:
            LineageNotFoundError: If lineage not found for resource.
            LineageError: If lineage query fails.
            LineageTimeoutError: If lineage query times out.

        Example:
            >>> result = api.get_lineage("iceberg.analytics.daily_clicks")
            >>> print(f"Total upstream: {result.total_upstream}")
            >>> for node in result.nodes:
            ...     print(f"  {node.name} (depth: {node.depth})")
        """
        if self._is_mock_mode:
            return self._create_mock_lineage_result(
                resource_name=resource_name,
                direction=direction,
                depth=depth,
            )

        client = self._get_client()
        response = client.get_lineage(
            resource_name=resource_name,
            direction=direction,
            depth=depth,
        )

        if not response.success:
            if response.status_code == 404:
                raise LineageNotFoundError(
                    message=f"Lineage not found for resource: {resource_name}",
                    resource_name=resource_name,
                )
            if response.status_code == 408:
                raise LineageTimeoutError(
                    message="Lineage query timed out",
                    resource_name=resource_name,
                )
            raise LineageError(
                message=response.error or "Lineage query failed",
                resource_name=resource_name,
            )

        return self._parse_lineage_response(
            resource_name=resource_name,
            direction=direction,
            depth=depth,
            data=response.data,
        )

    def get_upstream(
        self,
        resource_name: str,
        depth: int = -1,
    ) -> LineageResult:
        """Get upstream dependencies for a resource.

        Convenience method for getting only upstream dependencies
        (what this resource depends on).

        Args:
            resource_name: Fully qualified resource name
                (e.g., 'iceberg.analytics.daily_clicks')
            depth: Maximum traversal depth (-1 for unlimited)

        Returns:
            LineageResult containing upstream nodes and edges.

        Raises:
            LineageNotFoundError: If lineage not found for resource.
            LineageError: If lineage query fails.

        Example:
            >>> upstream = api.get_upstream("iceberg.analytics.daily_clicks", depth=2)
            >>> for node in upstream.nodes:
            ...     print(f"Depends on: {node.name}")
        """
        return self.get_lineage(
            resource_name=resource_name,
            direction="upstream",
            depth=depth,
        )

    def get_downstream(
        self,
        resource_name: str,
        depth: int = -1,
    ) -> LineageResult:
        """Get downstream dependents for a resource.

        Convenience method for getting only downstream dependents
        (what depends on this resource).

        Args:
            resource_name: Fully qualified resource name
                (e.g., 'iceberg.analytics.daily_clicks')
            depth: Maximum traversal depth (-1 for unlimited)

        Returns:
            LineageResult containing downstream nodes and edges.

        Raises:
            LineageNotFoundError: If lineage not found for resource.
            LineageError: If lineage query fails.

        Example:
            >>> downstream = api.get_downstream("iceberg.raw.events", depth=3)
            >>> for node in downstream.nodes:
            ...     print(f"Depends on this: {node.name}")
        """
        return self.get_lineage(
            resource_name=resource_name,
            direction="downstream",
            depth=depth,
        )

    # =========================================================================
    # Private Helpers
    # =========================================================================

    def _create_mock_lineage_result(
        self,
        resource_name: str,
        direction: LineageDirectionType,
        depth: int,
    ) -> LineageResult:
        """Create mock lineage result for testing.

        Args:
            resource_name: The resource name queried.
            direction: Direction of the query.
            depth: Requested depth.

        Returns:
            Mock LineageResult with sample data.
        """
        # Create root node
        root = LineageNode(
            name=resource_name,
            type="Dataset",
            owner="mock@example.com",
            team="@mock-team",
            description=f"Mock dataset: {resource_name}",
            depth=0,
        )

        # Create mock upstream and downstream nodes
        nodes: list[LineageNode] = []
        edges: list[LineageEdge] = []

        if direction in ("upstream", "both"):
            # Add mock upstream dependency
            upstream_name = f"mock.raw.{resource_name.split('.')[-1]}_source"
            upstream_node = LineageNode(
                name=upstream_name,
                type="Dataset",
                owner="mock@example.com",
                team="@mock-team",
                depth=-1,
            )
            nodes.append(upstream_node)
            edges.append(
                LineageEdge(
                    source=upstream_name,
                    target=resource_name,
                    edge_type="direct",
                )
            )

        if direction in ("downstream", "both"):
            # Add mock downstream dependent
            downstream_name = f"mock.analytics.{resource_name.split('.')[-1]}_report"
            downstream_node = LineageNode(
                name=downstream_name,
                type="Dataset",
                owner="mock@example.com",
                team="@mock-team",
                depth=1,
            )
            nodes.append(downstream_node)
            edges.append(
                LineageEdge(
                    source=resource_name,
                    target=downstream_name,
                    edge_type="direct",
                )
            )

        return LineageResult(
            root=root,
            nodes=nodes,
            edges=edges,
            direction=LineageDirection(direction),
            max_depth=depth,
            total_upstream=1 if direction in ("upstream", "both") else 0,
            total_downstream=1 if direction in ("downstream", "both") else 0,
        )

    def _parse_lineage_response(
        self,
        resource_name: str,
        direction: LineageDirectionType,
        depth: int,
        data: dict | list | None,
    ) -> LineageResult:
        """Parse the server response into a LineageResult.

        Args:
            resource_name: The queried resource name.
            direction: Direction of the query.
            depth: Requested depth.
            data: Raw response data from server.

        Returns:
            Parsed LineageResult.
        """
        if data is None:
            data = {}

        if isinstance(data, list):
            data = {"nodes": data}

        # Parse root node
        root_data = data.get("root", {"name": resource_name})
        root = LineageNode(
            name=root_data.get("name", resource_name),
            type=root_data.get("type", "Dataset"),
            owner=root_data.get("owner"),
            team=root_data.get("team"),
            description=root_data.get("description"),
            tags=root_data.get("tags", []),
            depth=0,
        )

        # Parse nodes
        nodes: list[LineageNode] = []
        for node_data in data.get("nodes", []):
            node = LineageNode(
                name=node_data.get("name", ""),
                type=node_data.get("type", "Dataset"),
                owner=node_data.get("owner"),
                team=node_data.get("team"),
                description=node_data.get("description"),
                tags=node_data.get("tags", []),
                depth=node_data.get("depth", 0),
            )
            nodes.append(node)

        # Parse edges
        edges: list[LineageEdge] = []
        for edge_data in data.get("edges", []):
            edge = LineageEdge(
                source=edge_data.get("source", ""),
                target=edge_data.get("target", ""),
                edge_type=edge_data.get("edge_type", "direct"),
            )
            edges.append(edge)

        # Map direction string to enum
        direction_enum = LineageDirection(direction)

        return LineageResult(
            root=root,
            nodes=nodes,
            edges=edges,
            direction=direction_enum,
            max_depth=depth,
            total_upstream=data.get("total_upstream", 0),
            total_downstream=data.get("total_downstream", 0),
        )
