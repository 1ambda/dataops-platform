"""Lineage Client for DLI CLI.

This module provides a dedicated client for lineage operations,
wrapping the BasecampClient for server-based lineage queries.

The LineageClient provides a higher-level interface specifically
designed for lineage operations, with proper typing and error handling.
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from dli.core.lineage import (
    LineageDirection,
    LineageDirectionType,
    LineageEdge,
    LineageNode,
    LineageResult,
)

if TYPE_CHECKING:
    from dli.core.client import BasecampClient

logger = logging.getLogger(__name__)


class LineageClientError(Exception):
    """Exception raised for lineage client errors."""

    def __init__(self, message: str, status_code: int = 500):
        """Initialize the error.

        Args:
            message: Error message describing what went wrong
            status_code: HTTP status code if applicable
        """
        super().__init__(message)
        self.message = message
        self.status_code = status_code


class LineageClient:
    """Client for lineage operations.

    This client provides methods to query lineage information from
    the Basecamp server. All operations are server-based and work
    only with registered datasets.

    Attributes:
        client: Underlying BasecampClient for API communication
    """

    def __init__(self, client: BasecampClient):
        """Initialize the LineageClient.

        Args:
            client: BasecampClient instance for server communication
        """
        self.client = client

    def get_lineage(
        self,
        resource_name: str,
        direction: LineageDirectionType = "both",
        depth: int = -1,
    ) -> LineageResult:
        """Get lineage information for a resource.

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
            LineageResult containing nodes and edges

        Raises:
            LineageClientError: If the lineage query fails
        """
        response = self.client.get_lineage(
            resource_name=resource_name,
            direction=direction,
            depth=depth,
        )

        if not response.success:
            raise LineageClientError(
                message=response.error
                or f"Failed to get lineage for '{resource_name}'",
                status_code=response.status_code,
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
        """Get upstream lineage for a resource.

        Convenience method for getting only upstream dependencies.

        Args:
            resource_name: Fully qualified resource name
            depth: Maximum traversal depth (-1 for unlimited)

        Returns:
            LineageResult containing upstream nodes and edges
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
        """Get downstream lineage for a resource.

        Convenience method for getting only downstream dependents.

        Args:
            resource_name: Fully qualified resource name
            depth: Maximum traversal depth (-1 for unlimited)

        Returns:
            LineageResult containing downstream nodes and edges
        """
        return self.get_lineage(
            resource_name=resource_name,
            direction="downstream",
            depth=depth,
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
            resource_name: The queried resource name
            direction: Direction of the query
            depth: Requested depth
            data: Raw response data from server

        Returns:
            Parsed LineageResult
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


__all__ = [
    "LineageClient",
    "LineageClientError",
]
