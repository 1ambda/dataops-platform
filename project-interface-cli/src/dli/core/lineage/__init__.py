"""Lineage module for DLI CLI.

This module provides data lineage functionality for tracking
table-level dependencies between datasets.

Key Features:
- Server-based lineage lookup (registered datasets only)
- Upstream analysis (what this resource depends on)
- Downstream analysis (what depends on this resource)
- Configurable traversal depth

Note:
    This implementation is SERVER-BASED ONLY. It queries registered
    datasets from the server and does not perform local SQLGlot processing.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Literal


class LineageDirection(str, Enum):
    """Direction for lineage traversal."""

    UPSTREAM = "upstream"
    DOWNSTREAM = "downstream"
    BOTH = "both"


@dataclass
class LineageNode:
    """Represents a single node in the lineage graph.

    A node corresponds to a registered dataset or table in the system.

    Attributes:
        name: Fully qualified name (e.g., 'iceberg.analytics.daily_clicks')
        type: Type of resource ('Dataset', 'Metric', 'External')
        owner: Owner of the resource
        team: Team responsible for the resource
        description: Optional description of the resource
        tags: List of tags associated with the resource
        depth: Distance from the root node in the lineage graph
    """

    name: str
    type: str = "Dataset"
    owner: str | None = None
    team: str | None = None
    description: str | None = None
    tags: list[str] = field(default_factory=list)
    depth: int = 0


@dataclass
class LineageEdge:
    """Represents an edge (dependency) between two lineage nodes.

    Attributes:
        source: Name of the source node (upstream)
        target: Name of the target node (downstream)
        edge_type: Type of dependency ('direct', 'indirect')
    """

    source: str
    target: str
    edge_type: str = "direct"


@dataclass
class LineageResult:
    """Result of a lineage query.

    Attributes:
        root: The root node that was queried
        nodes: All nodes in the lineage graph
        edges: All edges (dependencies) in the lineage graph
        direction: Direction of the lineage query
        max_depth: Maximum depth that was traversed
        total_upstream: Total count of upstream dependencies
        total_downstream: Total count of downstream dependencies
    """

    root: LineageNode
    nodes: list[LineageNode] = field(default_factory=list)
    edges: list[LineageEdge] = field(default_factory=list)
    direction: LineageDirection = LineageDirection.BOTH
    max_depth: int = -1
    total_upstream: int = 0
    total_downstream: int = 0

    @property
    def upstream_nodes(self) -> list[LineageNode]:
        """Get nodes that are upstream of the root."""
        # In upstream direction, sources feed into the root
        upstream_names = {e.source for e in self.edges if e.target == self.root.name}
        return [n for n in self.nodes if n.name in upstream_names or n.depth < 0]

    @property
    def downstream_nodes(self) -> list[LineageNode]:
        """Get nodes that are downstream of the root."""
        # In downstream direction, root feeds into targets
        downstream_names = {e.target for e in self.edges if e.source == self.root.name}
        return [n for n in self.nodes if n.name in downstream_names or n.depth > 0]


# Type alias for direction parameter
LineageDirectionType = Literal["upstream", "downstream", "both"]


__all__ = [
    "LineageDirection",
    "LineageDirectionType",
    "LineageEdge",
    "LineageNode",
    "LineageResult",
]
